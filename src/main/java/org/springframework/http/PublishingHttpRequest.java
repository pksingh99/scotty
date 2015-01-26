package org.springframework.http;

import java.nio.ByteBuffer;

import org.reactivestreams.Publisher;

import org.springframework.http.HttpRequest;

/**
 * @author Arjen Poutsma
 */
public interface PublishingHttpRequest extends HttpRequest {

	Publisher<ByteBuffer> getBodyPublisher();

}
