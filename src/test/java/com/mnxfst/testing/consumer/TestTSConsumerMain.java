/*
 *  ptest-server and client provides you with a performance test utility
 *  Copyright (C) 2012  Christian Kreutzfeldt <mnxfst@googlemail.com>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *  
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package com.mnxfst.testing.consumer;

import java.util.HashMap;
import java.util.Map;

import junit.framework.Assert;

import org.junit.Test;

/**
 * Test case for {@link TSConsumerMain}
 * @author ckreutzfeldt
 * @since 23.02.2012
 */
public class TestTSConsumerMain {

	@Test
	public void testExtractRequestHandlers() {
		
		TSConsumerMain m = new TSConsumerMain();
		Map<String, String> configuration = new HashMap<String, String>();
		
		Assert.assertNull(m.extractRequestHandlers(null));
		Assert.assertNull(m.extractRequestHandlers(configuration));
		
		configuration.put("context.request.", "test");
		Assert.assertNull(m.extractRequestHandlers(configuration));

		configuration.put("context.request.handlers", "test");
		Assert.assertTrue(m.extractRequestHandlers(configuration).isEmpty());

		configuration.put("context.request.handler.test", "test");
		Assert.assertTrue(m.extractRequestHandlers(configuration).isEmpty());

		configuration.put("context.request.handler.test", "test");
		configuration.put("context.request.handler.test.path", "/testPath");
		Assert.assertFalse(m.extractRequestHandlers(configuration).isEmpty());
		Assert.assertEquals("The value must be 'test'", "test", m.extractRequestHandlers(configuration).get("/testPath"));


		
	}
	
}
