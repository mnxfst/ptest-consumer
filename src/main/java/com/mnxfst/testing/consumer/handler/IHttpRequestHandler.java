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

import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.jboss.netty.handler.codec.http.HttpRequest;

import com.mnxfst.testing.consumer.exception.HttpRequestProcessingException;

/**
 * Provides a general interface to all consumer type specific handler implementations 
 * @author ckreutzfeldt
 * @since 22.02.2012
 */
public interface IHttpRequestHandler extends Runnable {

	/**
	 * Initializes hte handler using the provided properties set
	 * @param configuration
	 */
	public void initialize(Properties configuration, Map<String, List<String>> queryParameters) throws HttpRequestProcessingException;
	
	/**
	 * Allows to further process the request
	 * @param request
	 * @param queryParameters
	 * @return holds the response
	 * @throws HttpRequestProcessingException
	 */
	public byte[] processRequest(HttpRequest request, Map<String, List<String>> queryParameters) throws HttpRequestProcessingException;
	
	/**
	 * Shutsdown the http request handler
	 * @throws HttpRequestProcessingException
	 */
	public void shutdown() throws HttpRequestProcessingException;
	
	/**
	 * Returns the current handler statistics
	 * @return
	 */
	public HttpRequestHandlerStatistics getHandlerStatistics();
	
	/**
	 * Returns the consumer identifier
	 * @return
	 */
	public String getId();
	
	/**
	 * Sets the consumer identifier
	 * @param id
	 */
	public void setId(String id);
	
	/**
	 * Returns a short name describing the handler
	 * @return
	 */
	public String getType();
	
	public void setType(String type);
}
