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
package com.mnxfst.testing.consumer.handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import junit.framework.Assert;

import org.jboss.netty.channel.MessageEvent;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.mnxfst.testing.consumer.exception.HttpRequestProcessingException;

/**
 * Test case for {@link TSConsumerChannelUpstreamHandler}
 * @author ckreutzfeldt
 * @since 22.02.2012
 */
public class TestTSConsumerChannelUpstreamHandler {

	public class TestDummyRequestHandler implements IHttpRequestHandler {

		public void handleRequest(MessageEvent messageEvent,
				Map<String, List<String>> queryParameters)
				throws HttpRequestProcessingException {
			// TODO Auto-generated method stub
			
		}

		/* (non-Javadoc)
		 * @see com.mnxfst.testing.consumer.handler.IHttpRequestHandler#initialize(java.util.Properties)
		 */
		public void initialize(Properties configuration) {
			// TODO Auto-generated method stub
			
		}

		public void shutdown() throws HttpRequestProcessingException {
			// TODO Auto-generated method stub
			
		}

		public HttpRequestHandlerStatistics getHandlerStatistics() {
			// TODO Auto-generated method stub
			return null;
		}

		public String getId() {
			// TODO Auto-generated method stub
			return null;
		}

		/* (non-Javadoc)
		 * @see com.mnxfst.testing.consumer.handler.IHttpRequestHandler#setId(java.lang.String)
		 */
		public void setId(String id) {
			// TODO Auto-generated method stub
			
		}

		/* (non-Javadoc)
		 * @see com.mnxfst.testing.consumer.handler.IHttpRequestHandler#getType()
		 */
		public String getType() {
			// TODO Auto-generated method stub
			return null;
		}

		/* (non-Javadoc)
		 * @see com.mnxfst.testing.consumer.handler.IHttpRequestHandler#setType(java.lang.String)
		 */
		public void setType(String type) {
			// TODO Auto-generated method stub
			
		}

		public void run() {
			// TODO Auto-generated method stub
			
		}

		public void initialize(Properties configuration,
				Map<String, List<String>> queryParameters)
				throws HttpRequestProcessingException {
			// TODO Auto-generated method stub
			
		}		
	}
	
	@Test
	public void testExtractMultiParameterValues() throws HttpRequestProcessingException {
		
		Map<String, Class<? extends IHttpRequestHandler>> handlers = new HashMap<String, Class<? extends IHttpRequestHandler>>();
		handlers.put("test1", TestDummyRequestHandler.class);
		TSConsumerChannelUpstreamHandler handler = new TSConsumerChannelUpstreamHandler(null, 0, 0, null, handlers);
		try {
			handler.extractMultiParameterValues(null, null);
			Assert.fail("Invalid parameter values");
		} catch(NullPointerException e) {
			//
		}
				
		try {
			handler.extractMultiParameterValues("", null);
			Assert.fail("Invalid parameter values");
		} catch(NullPointerException e) {
			//
		}

		Map<String, List<String>> queryParams = new HashMap<String, List<String>>();
		try {
			handler.extractMultiParameterValues("", queryParams);
			Assert.fail("Invalid parameter values");
		} catch(HttpRequestProcessingException e) {
			//
		}
		
		List<String> values = new ArrayList<String>();
		queryParams.put("startConsumer", values);
		try {
			handler.extractMultiParameterValues("startConsumer", queryParams);
			Assert.fail("Invalid parameter values");
		} catch(HttpRequestProcessingException e) {
			//
		}

		values.add("jmsConnector");
		queryParams.put("startConsumer", values);
		String[] result = handler.extractMultiParameterValues("startConsumer", queryParams);
		Assert.assertNotNull("The result must not be null", result);
		Assert.assertEquals("The size of the result must be 1", 1, result.length);
		Assert.assertEquals("The result contains 'jmsConnector'", "jmsConnector", result[0]);

		values.add("soapConnector");
		queryParams.put("startConsumer", values);
		result = handler.extractMultiParameterValues("startConsumer", queryParams);
		Assert.assertNotNull("The result must not be null", result);
		Assert.assertEquals("The size of the result must be 2", 2, result.length);
		// test should be valid since the list keeps the elements in order and they are copied in order!
		Assert.assertEquals("The result contains 'jmsConnector'", "jmsConnector", result[0]);
		Assert.assertEquals("The result contains 'soapConnector'", "soapConnector", result[1]);
	}
	
	@Test
	public void testCollectHandlerStatistics() throws Exception {
		Map<String, Class<? extends IHttpRequestHandler>> handlers = new HashMap<String, Class<? extends IHttpRequestHandler>>();
		handlers.put("test1", TestDummyRequestHandler.class);
		TSConsumerChannelUpstreamHandler handler = new TSConsumerChannelUpstreamHandler(null, 0, 0, null, handlers);

		Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		Element rootNode = document.createElement("junit-test");

		try {
			handler.collectHandlerStatistics(new String[]{"test1", "test2"}, document);
			Assert.fail("No such consumer"); // TODO test
		} catch(HttpRequestProcessingException e) {
			//
		}
	}

	@Test
	public void testCreateErrorElement() throws HttpRequestProcessingException, ParserConfigurationException  {
		Map<String, Class<? extends IHttpRequestHandler>> handlers = new HashMap<String, Class<? extends IHttpRequestHandler>>();
		handlers.put("test1", TestDummyRequestHandler.class);
		TSConsumerChannelUpstreamHandler handler = new TSConsumerChannelUpstreamHandler(null, 0, 0, null, handlers);

		Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		Element rootNode = document.createElement("junit-test");
		
		Map<Integer, String> mapping = new HashMap<Integer, String>();
		
		Element element = handler.createErrorElement(mapping, document);
		rootNode.appendChild(element);
		document.appendChild(rootNode);
		
		System.out.println(new String(handler.convertDocument(document)));
		
		mapping.put(Integer.valueOf(1), "error-1");
		mapping.put(Integer.valueOf(2), "error-2");
		
		document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		rootNode = document.createElement("junit-test");
		element = handler.createErrorElement(mapping, document);
		rootNode.appendChild(element);
		document.appendChild(rootNode);
		
		System.out.println(new String(handler.convertDocument(document)));		
	}
	
	@Test
	public void testShutdownConsumer() throws HttpRequestProcessingException, ParserConfigurationException {
		
		Map<String, Class<? extends IHttpRequestHandler>> handlers = new HashMap<String, Class<? extends IHttpRequestHandler>>();
		handlers.put("test1", TestDummyRequestHandler.class);
		TSConsumerChannelUpstreamHandler handler = new TSConsumerChannelUpstreamHandler(null, 0, 0, null, handlers);
		Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		Element rootNode = document.createElement("junit-test");
		
		Map<Integer, String> mapping = new HashMap<Integer, String>();
		
		Element element = handler.shutdownConsumer(new String[]{"consumer1", "consumer2"}, document);
		rootNode.appendChild(element);
		document.appendChild(rootNode);
		
		System.out.println(new String(handler.convertDocument(document)));
		
		mapping.put(Integer.valueOf(1), "error-1");
		mapping.put(Integer.valueOf(2), "error-2");
		
		document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		rootNode = document.createElement("junit-test");
		element = handler.shutdownConsumer(new String[]{"consumer1", "consumer2"}, document);
		rootNode.appendChild(element);
		document.appendChild(rootNode);
		
		System.out.println("SHUTDOWN: " + new String(handler.convertDocument(document)));		
	}
	
}
