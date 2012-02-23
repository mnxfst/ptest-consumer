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

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Text;

import com.eaio.uuid.UUID;
import com.mnxfst.testing.consumer.async.AsyncInputConsumerStatistics;
import com.mnxfst.testing.consumer.async.IAsyncInputConsumer;
import com.mnxfst.testing.consumer.exception.AsyncInputConsumerException;
import com.mnxfst.testing.consumer.exception.HttpRequestProcessingException;
import com.mnxfst.testing.consumer.handler.TSConsumerChannelUpstreamHandler;

/**
 * The asynchronous consumer context is in charge of initializing and ramping up asynchronous consumers
 * of any source, eg. JMS. Instances of this handler receive incoming requests, parse them for actions
 * requested and execute them
 * @author ckreutzfeldt
 * @since 23.02.2012
 */
public class AsyncConsumerContextHttpRequestHandler implements IContextRequestHandler {

	private static final Logger logger = Logger.getLogger(AsyncConsumerContextHttpRequestHandler.class);
	
	/////////////////////////////////////////////////////////////////////////////////////////////
	// cfg options
	private static final String CFG_OPT_CONSUMER_TYPE_PREFIX = "consumer.async.";
	
	/////////////////////////////////////////////////////////////////////////////////////////////
	// available request parameters required for starting/stopping/collecting stats from consumer 
	private static final String REQUEST_PARAM_OP_CODE_START_CONSUMER = "start";
	private static final String REQUEST_PARAM_OP_CODE_STOP_CONSUMER = "stop";
	private static final String REQUEST_PARAM_OP_CODE_COLLECT_CONSUMER_STATS = "collectStats";
	/////////////////////////////////////////////////////////////////////////////////////////////

	/////////////////////////////////////////////////////////////////////////////////////////////
	// response xml tags
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
	private static final String CONSUMER_RESPONSE_SHUTDOWN_CONSUMER_STATE_MESSAGE_ELEMENT = "msg";
	private static final String CONSUMER_RESPONSE_START_ROOT_ELEMENT = "startConsumers";
	private static final String CONSUMER_RESPONSE_START_CONSUMER_ELEMENT = "consumer";
	private static final String CONSUMER_RESPONSE_START_CONSUMER_ID_ELEMENT = "id";
	private static final String CONSUMER_RESPONSE_START_CONSUMER_TYPE_ELEMENT = "type";
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
	// tracking running async consumers
	// keeps track of all running async consumer instances
	private static ConcurrentMap<String, IAsyncInputConsumer> runningAsyncInputConsumers = new ConcurrentHashMap<String, IAsyncInputConsumer>();
	// keeps track of all available async consumers referenced by a unique type string
	private static ConcurrentMap<String, Class<? extends IAsyncInputConsumer>> availableAsyncInputConsumers = new ConcurrentHashMap<String, Class<? extends IAsyncInputConsumer>>();
	// provides a runtime async environment
	private static ExecutorService asyncConsumerExecutorService = Executors.newCachedThreadPool(); // TODO restrict number of available threads
	/////////////////////////////////////////////////////////////////////////////////////////////

	
	/////////////////////////////////////////////////////////////////////////////////////////////
	// required for response building 	
	private DocumentBuilder documentBuilder = null;
	private Transformer documentTransformer = null;
	/////////////////////////////////////////////////////////////////////////////////////////////

	// holds the configuration options provided on initialization -> mapping to list is required for faster merge process with optional parameters on instance creation
	private Map<String, List<String>> configurationOptions = new HashMap<String, List<String>>();
	
	/**
	 * Default constructor
	 */
	public AsyncConsumerContextHttpRequestHandler() {
	}
	
	/**
	 * @see com.mnxfst.testing.consumer.handler.async.IContextRequestHandler#initialize(java.util.Map)
	 */
	@SuppressWarnings("unchecked")
	public void initialize(Map<String, String> configOptions) {
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

		// copy config options
		for(String cfgKey : configOptions.keySet()) {
			String cfgValue = configOptions.get(cfgKey);
			if(cfgValue != null && !cfgKey.isEmpty()) {
				List<String> values = new ArrayList<String>();
				values.add(cfgValue);
				configurationOptions.put(cfgKey, values);
			}
			
			if(cfgKey.startsWith(CFG_OPT_CONSUMER_TYPE_PREFIX)) {
				String consumerType = cfgKey.substring(CFG_OPT_CONSUMER_TYPE_PREFIX.length());
				try {
					Class<? extends IAsyncInputConsumer> inputConsumer = (Class<? extends IAsyncInputConsumer>) Class.forName(cfgValue);
					availableAsyncInputConsumers.put(consumerType, inputConsumer);
					logger.info("Added asynchronous consumer: [type="+consumerType+", class="+cfgValue+"]");
				} catch(ClassNotFoundException e) {
					throw new RuntimeException("Asynchronous consumer class not found: " + cfgValue);
				}
			}	
		}
		
		
		
		logger.info("Successfully initialized " + AsyncConsumerContextHttpRequestHandler.class.getName());
	}

	/**
	 * @see com.mnxfst.testing.consumer.handler.async.IContextRequestHandler#processRequest(org.jboss.netty.handler.codec.http.HttpRequest, java.util.Map, boolean, org.jboss.netty.channel.MessageEvent)
	 */
	public void processRequest(HttpRequest httpRequest, Map<String, List<String>> queryParams, boolean keepAlive, MessageEvent event) {
	
//		// ensure that the provided http request as well as the request parameters are valid
//		if(httpRequest == null)
//			throw new HttpRequestProcessingException("Invalid incoming request: null");
//		if(queryParams == null)
//			throw new HttpRequestProcessingException("Invalid incoming request parameters: null");
		
		// create a new response object and root element
		Document responseDocument = documentBuilder.newDocument();
		Element responseRootElement = responseDocument.createElement(TSConsumerChannelUpstreamHandler.CONSUMER_RESPONSE_ROOT_ELEMENT);
		
		// map of errors which will be added to response in the end
		Map<Integer, String> errors = new HashMap<Integer, String>();
		
		// if the caller requests to start a new consumer, extract the required information and keep on going
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

			if(logger.isDebugEnabled())
				logger.debug("Incoming request for collecting consumer statistics");

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
		
		// finalize document, convert it into its byte representation and send it back as response
		responseDocument.appendChild(responseRootElement);		
		byte[] responseMessage = null;
		try {
			responseMessage = convertDocument(responseDocument);
		} catch(HttpRequestProcessingException e) {
			responseMessage = e.getMessage().getBytes();
		}
		sendResponse(responseMessage, keepAlive, event);
	}

	/////////////////////////////////////// CONSUMER ACTIONS //////////////////////////////////////////////
	
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
		
		// prepare configuration options
		Map<String, List<String>> configOptions = new HashMap<String, List<String>>(configurationOptions);
		configOptions.putAll(queryParams);
		
		Set<IAsyncInputConsumer> createdConsumers = new HashSet<IAsyncInputConsumer>();
		
		// iterate through the set of requested consumer types and instantiate the consumer
		for(int i = 0; i < consumerTypes.length; i++) {			
			try {
				IAsyncInputConsumer asyncInputConsumer = instantiateAsyncInputConsumer(consumerTypes[i], configOptions);
				createdConsumers.add(asyncInputConsumer);
			} catch(AsyncInputConsumerException e) {
				for(IAsyncInputConsumer c : createdConsumers) {
					try {
						c.shutdown();
					} catch(Exception e1) {
						logger.error("Failed to shutdown async consumer: " + e1.getMessage(), e1);
					}
				}
				throw new HttpRequestProcessingException("Failed to instantiate consumer for type '"+consumerTypes[i]+"'. Error: " + e.getMessage());
			} 
		}
		
		// if there are any consumers and there has been no previous exception, provide the consumers for the executor service
		if(!createdConsumers.isEmpty()) {
			for(IAsyncInputConsumer asyncInputConsumer : createdConsumers) {
			
				asyncConsumerExecutorService.execute(asyncInputConsumer);
				runningAsyncInputConsumers.putIfAbsent(asyncInputConsumer.getId(), asyncInputConsumer);
				
				Element startConsumerElement = responseDocument.createElement(CONSUMER_RESPONSE_START_CONSUMER_ELEMENT);
				
				Element consumerIdElement = responseDocument.createElement(CONSUMER_RESPONSE_START_CONSUMER_ID_ELEMENT);
				Text consumerIdText = responseDocument.createTextNode(asyncInputConsumer.getId());
				consumerIdElement.appendChild(consumerIdText);
				
				Element consumerTypeElement = responseDocument.createElement(CONSUMER_RESPONSE_START_CONSUMER_TYPE_ELEMENT);
				Text consumerTypeText = responseDocument.createTextNode(asyncInputConsumer.getType());
				consumerTypeElement.appendChild(consumerTypeText);
				
				startConsumerElement.appendChild(consumerIdElement);
				startConsumerElement.appendChild(consumerTypeElement);
				
				startConsumersRootElement.appendChild(startConsumerElement);
				
				if(logger.isDebugEnabled())
					logger.debug("asyncConsumer[id="+asyncInputConsumer.getId()+", type="+asyncInputConsumer.getType()+"] successfully started");
			}
		} else {
			throw new HttpRequestProcessingException("No consumers created");
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
			
			IAsyncInputConsumer asyncInputConsumer = runningAsyncInputConsumers.get(consumerIds[i]);
			
			// create response elements
			Element shutdownElement = responseDocument.createElement(CONSUMER_RESPONSE_SHUTDOWN_CONSUMER_ELEMENT);
			Element consumerIdElement = responseDocument.createElement(CONSUMER_RESPONSE_SHUTDOWN_CONSUMER_ID_ELEMENT);
			Element stateElement = responseDocument.createElement(CONSUMER_RESPONSE_SHUTDOWN_CONSUMER_STATE_ELEMENT);
			Element stateMessageElement = responseDocument.createElement(CONSUMER_RESPONSE_SHUTDOWN_CONSUMER_STATE_MESSAGE_ELEMENT);
			
			// add consumer id to consumer id element
			Text consumerIdText = responseDocument.createTextNode(consumerIds[i]);
			consumerIdElement.appendChild(consumerIdText);
			
			Text stateText = null;
			Text stateMessage = null;
			if(asyncInputConsumer != null) {
				try {
					asyncInputConsumer.shutdown();
					stateText = responseDocument.createTextNode(String.valueOf(CONSUMER_SHUTDOWN_STATE_SUCCESS));
					stateMessage = responseDocument.createTextNode("");					
				} catch(AsyncInputConsumerException e) {
					stateText = responseDocument.createTextNode(String.valueOf(CONSUMER_SHUTDOWN_STATE_FAILED));
					stateMessage = responseDocument.createTextNode(e.getMessage());
				}
			} else {
				stateText = responseDocument.createTextNode(String.valueOf(CONSUMER_SHUTDOWN_STATE_UNKNOWN_ID));
				stateMessage = responseDocument.createTextNode("");
			}
			// add state text
			stateElement.appendChild(stateText);
			stateMessageElement.appendChild(stateMessage);
			
			// append consumer id and state to shutdown element
			shutdownElement.appendChild(consumerIdElement);
			shutdownElement.appendChild(stateElement);
			shutdownElement.appendChild(stateMessageElement);
			
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
			
			IAsyncInputConsumer consumer = runningAsyncInputConsumers.get(consumerIds[i]);
			if(consumer == null)
				throw new HttpRequestProcessingException("No such consumer: " + consumerIds[i]);
			
			Element consumerStatElement = responseDocument.createElement(CONSUMER_RESPONSE_SINGLE_CONSUMER_STAT_ELEMENT);
			consumerStatElement.setAttribute("consumerId" , consumerIds[i]);
			consumerStatElement.setAttribute("id", consumer.getId());
			consumerStatElement.setAttribute("type", consumer.getType());
			
			AsyncInputConsumerStatistics stats = consumer.getConsumerStatistics();
			if(stats != null) {
				// TODO implement
			}
			
			statsRootElement.appendChild(consumerStatElement);
		}
		
		return statsRootElement;		
	}
	
	/**
	 * Creates an instance from the {@link IAsyncInputConsumer consumer class} referenced by the given type. The instance
	 * will be initialized using the provided configuration options
	 * @param consumerType
	 * @param configOptions
	 * @return
	 * @throws HttpRequestProcessingException
	 */
	protected IAsyncInputConsumer instantiateAsyncInputConsumer(String consumerType, Map<String, List<String>> configOptions) throws AsyncInputConsumerException {

		// validate the consumer type
		if(consumerType == null || consumerType.isEmpty())
			throw new AsyncInputConsumerException("Missing required consumer type");
		
		// fetch the consumer class referenced 
		Class<? extends IAsyncInputConsumer> consumerClazz = availableAsyncInputConsumers.get(consumerType);
		if(consumerClazz == null)
			throw new AsyncInputConsumerException("Consumer type '"+consumerType+"' does not reference an available consumer class");
		
		// instantiate the consumer, set the identifier and type and provide config options
		try {
			IAsyncInputConsumer instance = consumerClazz.newInstance();
			instance.setType(consumerType);
			instance.setId(new UUID().toString());
			instance.initialize(configOptions);
			return instance;
		} catch(InstantiationException e) {
			throw new AsyncInputConsumerException("Failed to instantiate consumer class '"+consumerClazz+"'. Error: " + e.getMessage());
		} catch (IllegalAccessException e) {
			throw new AsyncInputConsumerException("Failed to access consumer class '"+consumerClazz+"'. Error: " + e.getMessage());		
		}
		
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

	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
}
