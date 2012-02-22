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

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import junit.framework.Assert;

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

	@Test
	public void testExtractMultiParameterValues() throws HttpRequestProcessingException {
		
		TSConsumerChannelUpstreamHandler handler = new TSConsumerChannelUpstreamHandler(null, 0, 0, null);
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
		TSConsumerChannelUpstreamHandler handler = new TSConsumerChannelUpstreamHandler(null, 0, 0, null);

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
		TSConsumerChannelUpstreamHandler handler = new TSConsumerChannelUpstreamHandler(null, 0, 0, null);

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
	
}
