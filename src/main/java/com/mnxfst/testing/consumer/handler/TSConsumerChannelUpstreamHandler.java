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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.xml.parsers.DocumentBuilder;
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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Text;

import com.mnxfst.testing.consumer.TSConsumerMain;
import com.mnxfst.testing.consumer.exception.HttpRequestProcessingException;

/**
 * Implements a http request handler for the {@link TSConsumerMain}
 * @author ckreutzfeldt
 * @since 21.02.2012
 */
public class TSConsumerChannelUpstreamHandler extends SimpleChannelUpstreamHandler {
	
	private static final Logger logger = Logger.getLogger(TSConsumerChannelUpstreamHandler.class);
	
	private static final String CFG_OPT_SOAP_PREFIX = "consumer.soap.";
	
	/////////////////////////////////////////////////////////////////////////////////////////////
	// dedicated context paths
	private static final String SERVER_CONTEXT_PATH_CONSUMER_CONTROLLER = "/consumer";
	
	/////////////////////////////////////////////////////////////////////////////////////////////
	// available request parameters required for starting/stopping/collecting stats from consumer 
	private static final String REQUEST_PARAM_OP_CODE_START_CONSUMER = "start";
	private static final String REQUEST_PARAM_OP_CODE_STOP_CONSUMER = "stop";
	private static final String REQUEST_PARAM_OP_CODE_COLLECT_CONSUMER_STATS = "collectStats";
	private static final String REQUEST_PARAM_CONSUMER_ID = "consumerId";
	/////////////////////////////////////////////////////////////////////////////////////////////
	
	/////////////////////////////////////////////////////////////////////////////////////////////
	// general response codes
	
	private static final int ERROR_CODE_NO_CONSUMER_TYPES_FOUND = 1;
	private static final int ERROR_CODE_NO_CONSUMER_IDENTIFIERS_FOUND = 2;
	private static final int ERROR_CODE_COLLECTING_STATS_FAILED = 3;
	private static final int ERROR_CODE_CONSUMER_START_FAILED = 4;
	private static final int ERROR_CODE_CONSUMER_STOP_FAILED = 5;
	private static final int ERROR_CODE_UNKNOWN_OP_CODE = 6;

	private static final int CONSUMER_SHUTDOWN_STATE_SUCCESS = 1;
	private static final int CONSUMER_SHUTDOWN_STATE_UNKNOWN_ID = 2;
	private static final int CONSUMER_SHUTDOWN_STATE_FAILED = 3;
	/////////////////////////////////////////////////////////////////////////////////////////////

	/////////////////////////////////////////////////////////////////////////////////////////////
	// response xml tags
	private static final String CONSUMER_RESPONSE_ROOT_ELEMENT = "tsConsumerResponse"; 
	private static final String CONSUMER_RESPONSE_STATS_ROOT_ELEMENT = "statistics";
	private static final String CONSUMER_RESPONSE_SINGLE_CONSUMER_STAT_ELEMENT = "consumerStats";
	private static final String CONSUMER_RESPONSE_ERRORS_ROOT_ELEMENT = "errors";
	private static final String CONSUMER_RESPONSE_ERROR_ELEMENT = "error";
	private static final String CONSUMER_RESPONSE_ERROR_ID_ELEMENT = "id";
	private static final String CONSUMER_RESPONSE_ERROR_MSG_ELEMENT = "msg";	
	private static final String CONSUMER_RESPONSE_SHUTDOWN_ROOT_ELEMENT = "shutdownConsumers";
	private static final String CONSUMER_RESPONSE_SHUTDOWN_CONSUMER_ELEMENT = "consumer";
	private static final String CONSUMER_RESPONSE_SHUTDOWN_CONSUMER_ID_ELEMENT = "id";
	private static final String CONSUMER_RESPONSE_SHUTDOWN_CONSUMER_STATE_ELEMENT = "state";
	private static final String CONSUMER_RESPONSE_START_ROOT_ELEMENT = "startConsumers";
	private static final String CONSUMER_RESPONSE_START_CONSUMER_ELEMENT = "consumer";
	private static final String CONSUMER_RESPONSE_START_CONSUMER_ID_ELEMENT = "id";
	private static final String CONSUMER_RESPONSE_START_CONSUMER_TYPE_ELEMENT = "type";
	/////////////////////////////////////////////////////////////////////////////////////////////

	/////////////////////////////////////////////////////////////////////////////////////////////
	// holds all configured request handlers and provides thread-safe access to them
	private static ConcurrentMap<String, IHttpRequestHandler> runningConsumers = new ConcurrentHashMap<String, IHttpRequestHandler>();	
	private static ConcurrentMap<String, Class<? extends IHttpRequestHandler>> availableConsumers = new ConcurrentHashMap<String, Class<? extends IHttpRequestHandler>>();

	private String hostname = null;
	private int port = 0;
	private int socketThreadPoolSize = 0;
	private Properties additionalProperties = null;
	
	private DocumentBuilder documentBuilder = null;
	private Transformer documentTransformer = null;
	
	private static ExecutorService consumerExecutorService = Executors.newCachedThreadPool(); // TODO tweak here for performance
	
	private int mockCallCount = 0;
	/////////////////////////////////////////////////////////////////////////////////////////////
	
	/**
	 * Initializes the http request handler
	 * @param hostname
	 * @param port
	 * @param socketThreadPoolSize
	 * @param additionalProperties
	 */
	public TSConsumerChannelUpstreamHandler(String hostname, int port, int socketThreadPoolSize, Properties additionalProperties, Map<String, Class<? extends IHttpRequestHandler>> consumers) {
		
		this.hostname = hostname;
		this.port = port;
		this.socketThreadPoolSize = socketThreadPoolSize;
		this.additionalProperties = additionalProperties;
		
		if(consumers == null || consumers.isEmpty())
			throw new RuntimeException("No consumers provided");
		
		try {
			this.documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		} catch(ParserConfigurationException e) {
			throw new RuntimeException("Failed to initialize consumer channel upstream handler. Error: " + e.getMessage(), e);
		}
		
		try {
			this.documentTransformer = TransformerFactory.newInstance().newTransformer();
		} catch (TransformerConfigurationException e) {
			throw new RuntimeException("Failed to initialize document transformer. Error: " + e.getMessage(), e);
		} catch (TransformerFactoryConfigurationError e) {
			throw new RuntimeException("Failed to initialize document transformer. Error: " + e.getMessage(), e);
		}
		
		StringBuffer consumerStr = new StringBuffer();		
		for(Iterator<String> iter = consumers.keySet().iterator(); iter.hasNext();) {
			String consumerType = iter.next();
			availableConsumers.putIfAbsent(consumerType, consumers.get(consumerType));
			consumerStr.append(consumers.get(consumerType).getName()).append(" (").append(consumerType).append(")");
			if(iter.hasNext())
				consumerStr.append(", ");
		}
		
		
		
		logger.info("consumer[host="+hostname+", port="+port+", socketThreadPoolSize="+socketThreadPoolSize+", consumers="+consumerStr.toString()+"]");
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
		
		if(uri.startsWith(SERVER_CONTEXT_PATH_CONSUMER_CONTROLLER)) {
			Document responseDocument = documentBuilder.newDocument();
			Element responseRootElement = responseDocument.createElement(CONSUMER_RESPONSE_ROOT_ELEMENT);
			Map<Integer, String> errors = new HashMap<Integer, String>();
			
			
			if(queryParams.containsKey(REQUEST_PARAM_OP_CODE_START_CONSUMER)) {
				
				if(logger.isDebugEnabled())
					logger.debug("Incoming request for starting a consumer");
				
				// extract the types to fire up consumer for
				String[] consumerTypes = null;
				try {
					consumerTypes = extractMultiParameterValues(REQUEST_PARAM_OP_CODE_START_CONSUMER, queryParams);
				}  catch(HttpRequestProcessingException e) {
					errors.put(ERROR_CODE_NO_CONSUMER_TYPES_FOUND, "No consumer types provided");
				}
				
				// validate the set of consumer types and instantiate the referenced ones
				if(consumerTypes != null && consumerTypes.length > 0) {
					try {
						Element startConsumerElement = startConsumer(consumerTypes, responseDocument, queryParams);
						responseRootElement.appendChild(startConsumerElement);
						
						logger.info(consumerTypes.length + " consumer(s) successfully started");
					} catch(HttpRequestProcessingException e) {
						errors.put(ERROR_CODE_CONSUMER_START_FAILED, e.getMessage()); 
					}
				}
	
			} else if(queryParams.containsKey(REQUEST_PARAM_OP_CODE_STOP_CONSUMER)) {
				
				if(logger.isDebugEnabled())
					logger.debug("Incoming request for shutting down a consumer");
				
				// extract the identifier of those consumers to shutdown
				String[] consumerIds = null;
				try {
					consumerIds = extractMultiParameterValues(REQUEST_PARAM_OP_CODE_STOP_CONSUMER, queryParams);
				} catch(HttpRequestProcessingException e) {
					errors.put(ERROR_CODE_NO_CONSUMER_IDENTIFIERS_FOUND, "No consumer identifiers provided");
				}
				
				// validate consumer identifier array and collect statistical information from the associated instances
				if(consumerIds != null && consumerIds.length > 0) {
					try {
						Element stopConsumerElement = shutdownConsumer(consumerIds, responseDocument);
						responseRootElement.appendChild(stopConsumerElement);
					} catch(HttpRequestProcessingException e) {
						errors.put(ERROR_CODE_CONSUMER_STOP_FAILED, e.getMessage());
					}
				}
	
			} else if(queryParams.containsKey(REQUEST_PARAM_OP_CODE_COLLECT_CONSUMER_STATS)) {
	
				// extract the identifier of those consumers to collect statistical information form
				String[] consumerIds = null;
				try {
					consumerIds = extractMultiParameterValues(REQUEST_PARAM_OP_CODE_COLLECT_CONSUMER_STATS, queryParams);
				} catch(HttpRequestProcessingException e) {
					errors.put(ERROR_CODE_NO_CONSUMER_IDENTIFIERS_FOUND, "No consumer identifiers provided");
				}
				
				// validate consumer identifier array and collect statistical information from the associated instances
				if(consumerIds != null && consumerIds.length > 0) {
					try {
						Element statsResponseElement = collectHandlerStatistics(consumerIds, responseDocument);
						responseRootElement.appendChild(statsResponseElement);
					} catch(HttpRequestProcessingException e) {
						errors.put(ERROR_CODE_COLLECTING_STATS_FAILED, e.getMessage());
					}
				}
			} else {
				// report error
				errors.put(ERROR_CODE_UNKNOWN_OP_CODE, "No valid op-code provided");
			}
	
			// add error response
			if(errors != null && !errors.isEmpty()) {
				Element errorsElement = createErrorElement(errors, responseDocument);
				responseRootElement.appendChild(errorsElement);
			}
				
			
			responseDocument.appendChild(responseRootElement);
			
			sendResponse(convertDocument(responseDocument), keepAlive, event);
		} else if(uri.startsWith("/ESP-Mock-0.0.1/PlaceOrderWS")) {
			
			mockCallCount = mockCallCount + 1;
			String responsestr = "<response>"+mockCallCount+"</response>";
						
			byte[] response = responsestr.getBytes();
			sendResponse(response, keepAlive, event);
			
		} else {
			sendResponse("<Error/>".getBytes(), keepAlive, event);
		}
	}
	
	/**
	 * Starts the referenced consumer types and returns an xml document containing all required information for controlling the service
	 * @param consumerTypes
	 * @param responseDocument
	 * @return
	 * @throws HttpRequestProcessingException
	 */
	protected Element startConsumer(String[] consumerTypes, Document responseDocument, Map<String, List<String>> queryParams) throws HttpRequestProcessingException {
		
		// create response root element
		Element startConsumersRootElement = responseDocument.createElement(CONSUMER_RESPONSE_START_ROOT_ELEMENT);
		
		for(int i = 0; i < consumerTypes.length; i++) {			
			
			Class<? extends IHttpRequestHandler> consumerHandler = availableConsumers.get(consumerTypes[i]);
			if(consumerHandler != null) {
				
				IHttpRequestHandler ch = null;
				try {
					ch = (IHttpRequestHandler)consumerHandler.newInstance();
				} catch (InstantiationException e) {
					throw new HttpRequestProcessingException("Failed to instantiate consumer of type '"+consumerTypes[i]+"'");
				} catch (IllegalAccessException e) {
					throw new HttpRequestProcessingException("Failed to instantiate consumer of type '"+consumerTypes[i]+"'");					
				}
					
				ch.setType(consumerTypes[i]);
				ch.setId(new com.eaio.uuid.UUID().toString());
				ch.initialize(additionalProperties, queryParams);
				runningConsumers.putIfAbsent(ch.getId(), ch);
				consumerExecutorService.execute(ch);
				
				Element startConsumerElement = responseDocument.createElement(CONSUMER_RESPONSE_START_CONSUMER_ELEMENT);
				
				Element consumerIdElement = responseDocument.createElement(CONSUMER_RESPONSE_START_CONSUMER_ID_ELEMENT);
				Text consumerIdText = responseDocument.createTextNode(ch.getId());
				consumerIdElement.appendChild(consumerIdText);
				
				Element consumerTypeElement = responseDocument.createElement(CONSUMER_RESPONSE_START_CONSUMER_TYPE_ELEMENT);
				Text consumerTypeText = responseDocument.createTextNode(ch.getType());
				consumerTypeElement.appendChild(consumerTypeText);
				
				startConsumerElement.appendChild(consumerIdElement);
				startConsumerElement.appendChild(consumerTypeElement);
				
				startConsumersRootElement.appendChild(startConsumerElement);
			} else {
				throw new HttpRequestProcessingException("No consumer class found for type '"+consumerTypes[i]+"'");
			}
 			
		}
		
		return startConsumersRootElement;
		
	}
	
	/**
	 * Shuts down the referenced consumers
	 * @param consumerIds
	 * @param responseDocument
	 * @return
	 * @throws HttpRequestProcessingException
	 */
	protected Element shutdownConsumer(String[] consumerIds, Document responseDocument) throws HttpRequestProcessingException {
		
		// create shutdown response root element
		Element shutdownRootElement = responseDocument.createElement(CONSUMER_RESPONSE_SHUTDOWN_ROOT_ELEMENT);
		
		for(int i = 0; i < consumerIds.length; i++) {
			
			IHttpRequestHandler requestHandler = runningConsumers.get(consumerIds[i]);
			
			// create response elements
			Element shutdownElement = responseDocument.createElement(CONSUMER_RESPONSE_SHUTDOWN_CONSUMER_ELEMENT);
			Element consumerIdElement = responseDocument.createElement(CONSUMER_RESPONSE_SHUTDOWN_CONSUMER_ID_ELEMENT);
			Element stateElement = responseDocument.createElement(CONSUMER_RESPONSE_SHUTDOWN_CONSUMER_STATE_ELEMENT);
			
			// add consumer id to consumer id element
			Text consumerIdText = responseDocument.createTextNode(consumerIds[i]);
			consumerIdElement.appendChild(consumerIdText);
			
			Text stateText = null;
			if(requestHandler != null) {
				try {
					requestHandler.shutdown();
					stateText = responseDocument.createTextNode(String.valueOf(CONSUMER_SHUTDOWN_STATE_SUCCESS));
				} catch(HttpRequestProcessingException e) {
					stateText = responseDocument.createTextNode(String.valueOf(CONSUMER_SHUTDOWN_STATE_FAILED));
				}
			} else {
				stateText = responseDocument.createTextNode(String.valueOf(CONSUMER_SHUTDOWN_STATE_UNKNOWN_ID));
			}
			// add state text
			stateElement.appendChild(stateText);
			
			// append consumer id and state to shutdown element
			shutdownElement.appendChild(consumerIdElement);
			shutdownElement.appendChild(stateElement);
			
			shutdownRootElement.appendChild(shutdownElement);
		}
		
		return shutdownRootElement;
		
	}
	
	/**
	 * Collects the statistics for the referenced consumers, creates a response xml element and returns the result.
	 * The result will be contained in the provided element. The caller must add the element to the document. Beside
	 * that the caller must ensure that the provided input is neither null nor empty.
	 * @param consumerIds
	 * @return
	 * @throws HttpRequestProcessingException
	 * @throws TransformerFactoryConfigurationError 
	 * @throws TransformerConfigurationException 
	 */
	protected Element collectHandlerStatistics(String[] consumerIds, Document responseDocument) throws HttpRequestProcessingException {
		
		// create statistics root element
		Element statsRootElement = responseDocument.createElement(CONSUMER_RESPONSE_STATS_ROOT_ELEMENT);

		for(int i = 0; i < consumerIds.length; i++) {
			
			IHttpRequestHandler consumer = runningConsumers.get(consumerIds[i]);
			if(consumer == null)
				throw new HttpRequestProcessingException("No such consumer: " + consumerIds[i]);
			
			Element consumerStatElement = responseDocument.createElement(CONSUMER_RESPONSE_SINGLE_CONSUMER_STAT_ELEMENT);
			consumerStatElement.setAttribute("consumerId" , consumerIds[i]);
			consumerStatElement.setAttribute("id", consumer.getId());
			consumerStatElement.setAttribute("type", consumer.getType());
			
			HttpRequestHandlerStatistics stats = consumer.getHandlerStatistics();
			if(stats != null) {
				// TODO implement
			}
			
			statsRootElement.appendChild(consumerStatElement);
		}
		
		return statsRootElement;		
	}

	/////////////////////////////////////// REQUEST SPECIFC METHODS ///////////////////////////////////////

	/**
	 * Returns an array of strings containing the values received for the referenced parameter 
	 * @param parameter
	 * @param queryParams
	 * @return
	 * @throws HttpRequestProcessingException thrown in case there are no values 
	 */
	protected String[] extractMultiParameterValues(String parameter, Map<String, List<String>> queryParams) throws HttpRequestProcessingException {
		
		List<String> values = queryParams.get(parameter);
		if(values != null && !values.isEmpty()) {
			String[] result = new String[values.size()];
			for(int i = 0; i < values.size(); i++) {
				String v = values.get(i);				
				result[i] = (v != null ? v.trim() : "");
			}
			return result;
		}
		
		throw new HttpRequestProcessingException("Parameter '"+parameter+"' references no values");
	}
	
	/**
	 * @see org.jboss.netty.channel.SimpleChannelUpstreamHandler#exceptionCaught(org.jboss.netty.channel.ChannelHandlerContext, org.jboss.netty.channel.ExceptionEvent)
	 */
	public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
		super.exceptionCaught(ctx, e);
		logger.error("Exception raised during http request processing: " + e.getCause().getMessage(), e.getCause());
	}
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	/////////////////////////////////////// RESPONSE DOCUMENT SPECIFC METHODS ///////////////////////////////////////

	/**
	 * Converts a provided document into its byte array representation
	 * @param document
	 * @return
	 * @throws HttpRequestProcessingException
	 */
	protected byte[] convertDocument(Document document) throws HttpRequestProcessingException {
		
		try {
			ByteArrayOutputStream result = new ByteArrayOutputStream();
			Source input = new DOMSource(document);
			Result output = new StreamResult(result);
			documentTransformer.transform(input, output);
			return result.toByteArray();
		} catch(TransformerConfigurationException e) {
			throw new HttpRequestProcessingException("Failed to convert provided document into a processable representation. Error: " + e.getMessage());
		} catch (TransformerException e) {
			throw new HttpRequestProcessingException("Failed to convert provided document into a processable representation. Error: " + e.getMessage());
		}		
	}

	
	/**
	 * Creates a response element from the provided set of error code / error message mappings
	 * @param errorCodeMessages
	 * @return
	 */
	protected Element createErrorElement(Map<Integer, String> errorCodeMessages, Document responseDocument) {
		
		// create error response root element
		Element errorRootElement = responseDocument.createElement(CONSUMER_RESPONSE_ERRORS_ROOT_ELEMENT);
		
		// step through identifiers and fetch associated messages
		for(Integer id : errorCodeMessages.keySet()) {			
			String msg = errorCodeMessages.get(id);
			
			// create required elements
			Element errorElement = responseDocument.createElement(CONSUMER_RESPONSE_ERROR_ELEMENT);
			Element errorIdElement = responseDocument.createElement(CONSUMER_RESPONSE_ERROR_ID_ELEMENT);
			Element errorMsgElement = responseDocument.createElement(CONSUMER_RESPONSE_ERROR_MSG_ELEMENT);
			
			Text idTextElement = responseDocument.createTextNode(id.toString());
			Text msgTextElement = responseDocument.createTextNode(msg);
			
			// set node values for id and message
			errorIdElement.appendChild(idTextElement);
			errorMsgElement.appendChild(msgTextElement);
			
			// append id and msg element to error element
			errorElement.appendChild(errorIdElement);
			errorElement.appendChild(errorMsgElement);
			
			// append error element to errors root
			errorRootElement.appendChild(errorElement);
		}
		
		return errorRootElement;		
	}
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
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
