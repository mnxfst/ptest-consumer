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
package com.mnxfst.testing.consumer.handler.async;

import java.util.List;
import java.util.Map;

import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.HttpRequest;

/**
 * Provides a common interface for all implementations receiving incoming requests 
 * to a specific context
 * @author ckreutzfeldt
 * @since 23.02.2012
 */
public interface IContextRequestHandler {

	/**
	 * Initializes the handler
	 * @param configOptions
	 */
	public void initialize(Map<String, String> configOptions);
	
	/**
	 * Processes an incoming http request
	 * @param httpRequest
	 * @param requestParameters
	 * @param keepAlive
	 * @param event
	 */
	public void processRequest(HttpRequest httpRequest, Map<String, List<String>> requestParameters, boolean keepAlive, MessageEvent event);
	
}
