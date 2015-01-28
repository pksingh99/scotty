/*
 * Copyright 2002-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.rx.web.servlet;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.servlet.AsyncContext;
import javax.servlet.ReadListener;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.rx.Streams;
import reactor.rx.stream.Broadcaster;

import org.springframework.http.HttpMessage;
import org.springframework.http.PublishingHttpRequest;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.rx.web.demo.EchoProcessor;

/**
 * @author Arjen Poutsma
 */
@WebServlet(asyncSupported = true, urlPatterns = "/*")
public class HttpMessagePublisherServlet extends HttpServlet {

	public static final int DEFAULT_MAX_BYTE_BUFFER_SIZE = 1024 * 1024 * 10;

	private int maxByteBufferSize = DEFAULT_MAX_BYTE_BUFFER_SIZE;

	private final Broadcaster<HttpMessage> messagePublisher = Streams.broadcast();

	private EchoProcessor processor;

	public void setMaxByteBufferSize(int maxByteBufferSize) {
		this.maxByteBufferSize = maxByteBufferSize;
	}

	@Override
	public void init() throws ServletException {
		processor = new EchoProcessor();
		messagePublisher.subscribe(processor);
	}

	@Override
	protected void service(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		AsyncContext context = request.startAsync(request, response);
		AsyncContextSynchronizer contextWrapper = new AsyncContextSynchronizer(context);

		PublishingListener readListener = new PublishingListener(contextWrapper, maxByteBufferSize);
		request.getInputStream().setReadListener(readListener);

		SubscribingListener writeListener = new SubscribingListener(contextWrapper);
		response.getOutputStream().setWriteListener(writeListener);

		processor.subscribe(writeListener);

		this.messagePublisher.onNext(
				new ServletPublishingHttpRequest(request, readListener));
	}

	@Override
	public void destroy() {
		this.messagePublisher.onComplete();
	}

	private static class ServletPublishingHttpRequest extends ServletServerHttpRequest
			implements PublishingHttpRequest {

		private final Publisher<ByteBuffer> bodyPublisher;

		public ServletPublishingHttpRequest(HttpServletRequest servletRequest,
				Publisher<ByteBuffer> bodyPublisher) {
			super(servletRequest);
			this.bodyPublisher = bodyPublisher;
		}

		@Override
		public InputStream getBody() throws IOException {
			throw new UnsupportedOperationException();
		}

		@Override
		public Publisher<ByteBuffer> getBodyPublisher() {
			return this.bodyPublisher;
		}

	}

	private static class PublishingListener implements ReadListener, Publisher<ByteBuffer> {

		private static final int BUFFER_SIZE = 4096;

		private final byte[] buffer = new byte[BUFFER_SIZE];

		private final Broadcaster<ByteBuffer> byteBufferPublisher = Streams.broadcast();

		private final AsyncContextSynchronizer synchronizer;

		private final ServletInputStream input;

		private final ByteBuffer byteBuffer;

		public PublishingListener(AsyncContextSynchronizer synchronizer, int byteBufferCapacity)
				throws IOException {
			this.synchronizer = synchronizer;
			this.input = this.synchronizer.getInputStream();
			this.byteBuffer = ByteBuffer.allocate(byteBufferCapacity);
		}

		@Override
		public void onDataAvailable() throws IOException {
			int totalRead = 0;

			while (input.isReady()) {
				int read = this.input.read(this.buffer);
				totalRead += read;

				if (totalRead >= this.byteBuffer.capacity()) {
					publishByteBuffer();
					totalRead = read;
				}

				this.byteBuffer.put(buffer, 0, read);
			}

			if (totalRead > 0) {
				publishByteBuffer();
			}
		}

		private void publishByteBuffer() {
			this.byteBuffer.flip();
			this.byteBufferPublisher.onNext(this.byteBuffer);

			this.byteBuffer.clear();
		}

		@Override
		public void onAllDataRead() throws IOException {
			this.synchronizer.readComplete();
			this.byteBufferPublisher.onComplete();
		}

		@Override
		public void onError(Throwable t) {
			this.byteBufferPublisher.onError(t);
		}

		@Override
		public void subscribe(Subscriber<? super ByteBuffer> s) {
			this.byteBufferPublisher.subscribe(s);
		}
	}

	private static class SubscribingListener
			implements WriteListener, Subscriber<ByteBuffer> {

		private static final int BUFFER_SIZE = 4096;

		private final byte[] buffer = new byte[BUFFER_SIZE];

		private Queue<byte[]> queue = new LinkedBlockingQueue<>();

		private final AsyncContextSynchronizer synchronizer;

		private final ServletOutputStream output;

		private Subscription subscription;

		private AtomicBoolean subscriptionComplete = new AtomicBoolean(false);

		public SubscribingListener(AsyncContextSynchronizer synchronizer)
				throws IOException {
			this.synchronizer = synchronizer;
			this.output = this.synchronizer.getOutputStream();
		}

		@Override
		public void onSubscribe(Subscription subscription) {
			this.subscription = subscription;
		}

		@Override
		public void onNext(ByteBuffer byteBuffer) {
			while (byteBuffer.remaining() > 0) {
				int len = Math.min(BUFFER_SIZE, byteBuffer.remaining());
				byteBuffer.get(this.buffer, 0, len);

				queue.add(Arrays.copyOf(this.buffer, len));
			}
		}

		@Override
		public void onComplete() {
			subscriptionComplete.compareAndSet(false, true);
		}

		@Override
		public void onError(Throwable t) {
			t.printStackTrace();
			this.synchronizer.writeComplete();
		}

		@Override
		public void onWritePossible() throws IOException {
			if (this.subscription != null && this.output.isReady() && !this.subscriptionComplete.get()) {
				this.subscription.request(1);
			}

			while (!this.queue.isEmpty() && this.output.isReady()) {
				output.write(queue.poll());
			}
			if (this.queue.isEmpty() && this.subscriptionComplete.get()) {
				this.synchronizer.writeComplete();
			}
		}
	}


}
