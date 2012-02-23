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

import java.util.Map;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpContentCompressor;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;

import com.mnxfst.testing.consumer.handler.TSConsumerChannelUpstreamHandler;

/**
 * Provides a pipeline factory implementation defining a handling process for incoming requests
 * @author ckreutzfeldt
 * @since 21.02.2012
 */
public class TSConsumerPipelineFactory implements ChannelPipelineFactory {

	private String hostname = null;
	private int port = 0;
	private int socketThreadPoolSize = 0;
	private Map<String, String> additionalProperties = null;
	private Map<String, String> configuredRequestHandlers = null;
	
	/**
	 * Initializes the consumer pipeline factory
	 * @param hostname
	 * @param port
	 * @param socketThreadPoolSize
	 * @param additionalProperties
	 */
	public TSConsumerPipelineFactory(String hostname, int port, int socketThreadPoolSize, Map<String, String> additionalProperties, Map<String, String> configuredRequestHandlers) {
		this.hostname = hostname;
		this.port = port;
		this.socketThreadPoolSize = socketThreadPoolSize;
		this.additionalProperties = additionalProperties;
		this.configuredRequestHandlers = configuredRequestHandlers;
	}
	
	/**
	 * @see org.jboss.netty.channel.ChannelPipelineFactory#getPipeline()
	 */
	public ChannelPipeline getPipeline() throws Exception {
		ChannelPipeline channelPipeline = Channels.pipeline();

		channelPipeline.addLast("decoder", new HttpRequestDecoder());
		channelPipeline.addLast("aggregator", new HttpChunkAggregator(1048576));
		channelPipeline.addLast("encoder", new HttpResponseEncoder());
		channelPipeline.addLast("deflater", new HttpContentCompressor());
		channelPipeline.addLast("handler", new TSConsumerChannelUpstreamHandler(hostname, port, socketThreadPoolSize, additionalProperties, configuredRequestHandlers));
		
		return channelPipeline;
	}

}
