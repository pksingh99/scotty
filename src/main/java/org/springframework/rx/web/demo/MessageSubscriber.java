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

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import org.springframework.http.HttpMessage;
import org.springframework.http.PublishingHttpRequest;

/**
 * @author Arjen Poutsma
 */
public class MessageSubscriber implements Subscriber<HttpMessage> {

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
//			System.out.print(request.getMethod());
//			System.out.println(" " + request.getURI());

			request.getBodyPublisher().subscribe(new Subscriber<ByteBuffer>() {
				private Subscription subscription;
				private long totalRead = 0;
				@Override
				public void onSubscribe(Subscription subscription) {
					this.subscription = subscription;
					this.subscription.request(1);
				}

				@Override
				public void onNext(ByteBuffer byteBuffer) {
//					System.out.println("Received " + byteBuffer.remaining() + " bytes");
					byte[] bytes = new byte[byteBuffer.remaining()];
					totalRead += byteBuffer.remaining();
					byteBuffer.get(bytes);
					//System.out.println("Received: " + new String(bytes, StandardCharsets.UTF_8));
					this.subscription.request(1);
				}

				@Override
				public void onError(Throwable t) {
					t.printStackTrace();

				}

				@Override
				public void onComplete() {
//					System.out.printf("Done consuming body!  Total: %10d\n", totalRead);

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
}
