package org.springframework.rx.web.demo;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

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
