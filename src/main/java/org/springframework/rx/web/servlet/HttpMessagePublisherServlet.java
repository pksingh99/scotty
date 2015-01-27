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
import javax.servlet.AsyncContext;
import javax.servlet.ReadListener;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import reactor.rx.Streams;
import reactor.rx.stream.Broadcaster;

import org.springframework.http.HttpMessage;
import org.springframework.http.PublishingHttpRequest;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.rx.web.demo.MessageSubscriber;

/**
 * @author Arjen Poutsma
 */
@WebServlet(asyncSupported = true, urlPatterns = "/*")
public class HttpMessagePublisherServlet extends HttpServlet {

	public static final int DEFAULT_MAX_BYTE_BUFFER_SIZE = 1024 * 1024 * 10;

	private int maxByteBufferSize = DEFAULT_MAX_BYTE_BUFFER_SIZE;

	private final Broadcaster<HttpMessage> messagePublisher = Streams.broadcast();

	public void setMaxByteBufferSize(int maxByteBufferSize) {
		this.maxByteBufferSize = maxByteBufferSize;
	}

	@Override
	public void init() throws ServletException {
		messagePublisher.subscribe(new MessageSubscriber());
	}

	@Override
	protected void service(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		AsyncContext context = request.startAsync(request, response);

		PublishingListener listener = new PublishingListener(context, maxByteBufferSize);
		request.getInputStream().setReadListener(listener);

		this.messagePublisher.onNext(new ServletPublishingHttpRequest(request, listener));
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

		private final Broadcaster<ByteBuffer> byteBufferPublisher = Streams.broadcast();

		private final byte[] buffer = new byte[BUFFER_SIZE];

		private final AsyncContext asyncContext;

		private final ServletInputStream input;

		private final ByteBuffer byteBuffer;

		public PublishingListener(AsyncContext asyncContext, int byteBufferCapacity)
				throws IOException {
			this.asyncContext = asyncContext;
			this.input = asyncContext.getRequest().getInputStream();
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
			this.byteBufferPublisher.onComplete();
			this.asyncContext.complete();
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


}
