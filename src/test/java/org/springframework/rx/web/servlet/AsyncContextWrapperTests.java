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
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

import org.springframework.mock.web.MockAsyncContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

public class AsyncContextWrapperTests {

	private MockAsyncContext asyncContext;

	private AsyncContextSynchronizer wrapper;

	private MyAsyncListener listener;

	@Before
	public void createWrapper() {
		this.asyncContext = new MockAsyncContext(new MockHttpServletRequest(), new MockHttpServletResponse());
		this.listener = new MyAsyncListener();
		this.asyncContext.addListener(listener);
		this.wrapper = new AsyncContextSynchronizer(asyncContext);
	}

	@Test
	public void readCompletedFirst() {
		assertFalse(this.listener.complete);
		wrapper.readComplete();
		assertFalse(this.listener.complete);
		wrapper.writeComplete();
		assertTrue(this.listener.complete);
	}

	@Test
	public void writeCompletedFirst() {
		assertFalse(this.listener.complete);
		wrapper.writeComplete();
		assertFalse(this.listener.complete);
		wrapper.readComplete();
		assertTrue(this.listener.complete);
	}

	private static class MyAsyncListener implements AsyncListener {

		private boolean complete = false;

		@Override
		public void onComplete(AsyncEvent event) throws IOException {
			complete = true;
		}

		@Override
		public void onTimeout(AsyncEvent event) throws IOException {

		}

		@Override
		public void onError(AsyncEvent event) throws IOException {

		}

		@Override
		public void onStartAsync(AsyncEvent event) throws IOException {

		}
	}


}