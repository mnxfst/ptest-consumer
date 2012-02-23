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

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.log4j.Logger;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.handler.codec.http.QueryStringDecoder;
import org.jboss.netty.util.CharsetUtil;

import com.mnxfst.testing.consumer.TSConsumerMain;
import com.mnxfst.testing.consumer.handler.async.IContextRequestHandler;

/**
 * Implements a http request handler for the {@link TSConsumerMain}
 * @author ckreutzfeldt
 * @since 21.02.2012
 */
public class TSConsumerChannelUpstreamHandler extends SimpleChannelUpstreamHandler {
	
	private static final Logger logger = Logger.getLogger(TSConsumerChannelUpstreamHandler.class);
	
	private static final String CFG_OPT_SOAP_PREFIX = "consumer.soap.";
	
	public static final String CONSUMER_RESPONSE_ROOT_ELEMENT = "tsConsumerResponse"; 

	/////////////////////////////////////////////////////////////////////////////////////////////
	// context identification
	// dedicated context paths
	private static final String SERVER_CONTEXT_PATH_CONSUMER_CONTROLLER = "/consumer";
	// mapping: context -> context handler
	private static ConcurrentMap<String, IContextRequestHandler> contextRequestHandlers = new ConcurrentHashMap<String, IContextRequestHandler>();	
	/////////////////////////////////////////////////////////////////////////////////////////////
	


	/////////////////////////////////////////////////////////////////////////////////////////////
	// holds all configured request handlers and provides thread-safe access to them

	private String hostname = null;
	private int port = 0;
	private int socketThreadPoolSize = 0;
	private Map<String, String> additionalProperties = null;
	
	/////////////////////////////////////////////////////////////////////////////////////////////
	
	/**
	 * Initializes the http request handler.
	 * @param hostname
	 * @param port
	 * @param socketThreadPoolSize
	 * @param additionalProperties
	 * @param contextRequestHandler holds a mapping from a context (eg. /testSoap) towards a handler class used for processing requests towards this specific context
	 */
	@SuppressWarnings("unchecked")
	public TSConsumerChannelUpstreamHandler(String hostname, int port, int socketThreadPoolSize, Map<String, String> additionalProperties, Map<String, String> contextRequestHandler) {
		
		this.hostname = hostname;
		this.port = port;
		this.socketThreadPoolSize = socketThreadPoolSize;
		this.additionalProperties = additionalProperties;
		
		if(contextRequestHandler == null || contextRequestHandler.isEmpty())
			throw new RuntimeException("No context handlers provided");
		
		
		
		// ensure that only one thread accesses the map and do access it only if there are no handlers configured yet
		synchronized (contextRequestHandlers) {
			if(contextRequestHandlers.isEmpty()) {
				StringBuffer logStr = new StringBuffer();		
				for(Iterator<String> iter = contextRequestHandler.keySet().iterator(); iter.hasNext();) {			
					String contextPath = iter.next();
					String contextHandlerClassName = contextRequestHandler.get(contextPath);
					
					if(logger.isDebugEnabled())
						logger.debug("Attempting to instantiate a handler for context '"+contextPath+"': " + contextHandlerClassName);
					
					try {
						Class<? extends IContextRequestHandler> contextHandlerClazz = (Class<? extends IContextRequestHandler>) Class.forName(contextHandlerClassName);
						IContextRequestHandler contextHandler = contextHandlerClazz.newInstance();
						contextHandler.initialize(additionalProperties);
						contextRequestHandlers.putIfAbsent(contextPath, contextHandler);
					} catch(ClassNotFoundException e) {
						throw new RuntimeException("Context handler class '"+contextHandlerClassName+"' not found");
					} catch(InstantiationException e) {
						throw new RuntimeException("Context handler class '"+contextHandlerClassName+"' could not be instantiated. Error: " + e.getMessage());
					} catch(IllegalAccessException e) {
						throw new RuntimeException("Context handler class '"+contextHandlerClassName+"' could not be accessed. Error: " + e.getMessage());
					}
					
					// create log output
					logStr.append(contextHandlerClassName).append(" (").append(contextHandlerClassName).append(")");
					if(iter.hasNext())
						logStr.append(", ");

				}
				logger.info("consumer[host="+hostname+", port="+port+", socketThreadPoolSize="+socketThreadPoolSize+", consumers="+logStr.toString()+"]");
			}
		}
	}

	/**
	 * @see org.jboss.netty.channel.SimpleChannelUpstreamHandler#messageReceived(org.jboss.netty.channel.ChannelHandlerContext, org.jboss.netty.channel.MessageEvent)
	 */
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent event) throws Exception {

		// extract http request from incoming message, get keep alive attribute as it will be transferred to response and decode query string 		
		HttpRequest httpRequest = (HttpRequest)event.getMessage();
		
		boolean keepAlive = HttpHeaders.Values.KEEP_ALIVE.equalsIgnoreCase(httpRequest.getHeader(HttpHeaders.Names.CONNECTION));		
		QueryStringDecoder decoder = new QueryStringDecoder(httpRequest.getUri());

		// fetch query parameters
		Map<String, List<String>> queryParams = decoder.getParameters();
		
		// handle post request
		if(httpRequest.getMethod() == HttpMethod.POST) {
			decoder = new QueryStringDecoder("?" + httpRequest.getContent().toString(CharsetUtil.UTF_8));
			queryParams.putAll(decoder.getParameters());
		}
		String uri = httpRequest.getUri();
		int ctxStartIdx = uri.indexOf('/');
		if(ctxStartIdx != -1) {
			String responseMessage = null;
			boolean handlerFound = false;
			for(String ctxStr : contextRequestHandlers.keySet()) {
				if(uri.indexOf(ctxStr) != -1) {
					contextRequestHandlers.get(ctxStr).processRequest(httpRequest, queryParams, keepAlive, event);
					handlerFound = true;
				}
				if(handlerFound)
					break;
			}
			
			if(!handlerFound) {
				responseMessage = "<"+CONSUMER_RESPONSE_ROOT_ELEMENT+"><errors><error>no handler found for: "+uri+"</error></errors></"+CONSUMER_RESPONSE_ROOT_ELEMENT+">";
				sendResponse(responseMessage.getBytes(), keepAlive, event);
			}			
			
		} else {
			String responseMessage = "<"+CONSUMER_RESPONSE_ROOT_ELEMENT+"><errors><error>unknown context path: "+uri+"</error></errors></"+CONSUMER_RESPONSE_ROOT_ELEMENT+">";
			sendResponse(responseMessage.getBytes(), keepAlive, event);
		}
		
	}
	

	/**
	 * @see org.jboss.netty.channel.SimpleChannelUpstreamHandler#exceptionCaught(org.jboss.netty.channel.ChannelHandlerContext, org.jboss.netty.channel.ExceptionEvent)
	 */
	public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
		super.exceptionCaught(ctx, e);
		logger.error("Exception raised during http request processing: " + e.getCause().getMessage(), e.getCause());
	}

	/**
	 * Sends a response containing the given message to the calling client
	 * @param responseMessage
	 * @param keepAlive
	 * @param event
	 */
	protected void sendResponse(byte[] responseMessage, boolean keepAlive, MessageEvent event) {
		HttpResponse httpResponse = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
		
		httpResponse.setContent(ChannelBuffers.copiedBuffer(responseMessage));
		httpResponse.setHeader(HttpHeaders.Names.CONTENT_TYPE, "text/plain; charset=UTF-8");
		
		if(keepAlive)
			httpResponse.setHeader(HttpHeaders.Names.CONTENT_LENGTH, httpResponse.getContent().readableBytes());
		
		ChannelFuture future = event.getChannel().write(httpResponse);
		if(!keepAlive)
			future.addListener(ChannelFutureListener.CLOSE);
	}

}
