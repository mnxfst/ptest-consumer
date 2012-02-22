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

import org.jboss.netty.channel.MessageEvent;

import com.mnxfst.testing.consumer.exception.HttpRequestProcessingException;

/**
 * Provides a general interface to all consumer type specific handler implementations 
 * @author ckreutzfeldt
 * @since 22.02.2012
 */
public interface IHttpRequestHandler {

	/**
	 * Processes an incoming http request according to consumer specific requirements
	 * @param messageEvent
	 * @param queryParameters
	 * @throws HttpRequestProcessingException
	 */
	public void handleRequest(MessageEvent messageEvent, Map<String, List<String>> queryParameters) throws HttpRequestProcessingException;
	
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
	 * Returns a short name describing the handler
	 * @return
	 */
	public String getName();
}
