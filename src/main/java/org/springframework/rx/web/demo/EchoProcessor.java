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

package org.springframework.rx.web.demo;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.reactivestreams.Processor;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.rx.Streams;
import reactor.rx.stream.Broadcaster;

import org.springframework.http.HttpMessage;
import org.springframework.http.PublishingHttpRequest;

/**
 * @author Arjen Poutsma
 */
public class EchoProcessor implements Processor<HttpMessage, ByteBuffer> {

	private final Broadcaster<ByteBuffer> byteBufferPublisher = Streams.broadcast();

	private Subscription subscription;

	@Override
	public void onSubscribe(Subscription subscription) {
		this.subscription = subscription;
		this.subscription.request(1);
	}

	@Override
	public void onNext(HttpMessage message) {
		if (message instanceof PublishingHttpRequest) {
			PublishingHttpRequest request = (PublishingHttpRequest) message;

			request.getBodyPublisher().subscribe(new Subscriber<ByteBuffer>() {
				private Subscription subscription;
				private int totalLen = 0;

				@Override
				public void onSubscribe(Subscription subscription) {
					this.subscription = subscription;
//					byteBufferPublisher.onSubscribe(subscription);
					this.subscription.request(1);
				}

				@Override
				public void onNext(ByteBuffer byteBuffer) {
					int len = byteBuffer.remaining();
					totalLen += len;
					byte[] bytes = new byte[len];
					byteBuffer.get(bytes);
					ByteBuffer copy = ByteBuffer.wrap(Arrays.copyOf(bytes, len));
//					System.out.println("Sending " + len + " bytes");
					byteBufferPublisher.onNext(copy);

					this.subscription.request(1);
				}

				@Override
				public void onError(Throwable t) {
					byteBufferPublisher.onError(t);

				}

				@Override
				public void onComplete() {
//					System.out.println("Total read EchoProcessor: " + totalLen);
					byteBufferPublisher.onComplete();

				}
			});
		}
		this.subscription.request(1);
	}

	@Override
	public void onError(Throwable t) {
		t.printStackTrace();
	}

	@Override
	public void onComplete() {
		System.out.println("Done!");
	}

	@Override
	public void subscribe(Subscriber<? super ByteBuffer> s) {
		this.byteBufferPublisher.subscribe(s);
	}
}
