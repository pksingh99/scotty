package org.springframework.rx.web.core;

import org.reactivestreams.Publisher;

import org.springframework.http.HttpMessage;

/**
 * @author Arjen Poutsma
 */
public interface HttpMessagePublisher extends Publisher<HttpMessage> {

}
