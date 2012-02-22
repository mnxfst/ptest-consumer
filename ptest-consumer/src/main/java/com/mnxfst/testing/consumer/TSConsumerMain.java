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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executors;

import org.apache.log4j.Logger;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;

import com.mnxfst.testing.consumer.cmd.CommandLineOption;
import com.mnxfst.testing.consumer.cmd.CommandLineProcessor;

/**
 * Provides a single entry point to the ptest consumer 
 * @author ckreutzfeldt
 * @since 21.02.2012
 */
public class TSConsumerMain {

	private static final Logger logger = Logger.getLogger(TSConsumerMain.class.getName());
	
	public static final String CMD_OPT_PORT = "port";
	public static final String CMD_OPT_PORT_SHORT = "p";
	public static final String CMD_OPT_THREAD_POOL_SIZE = "poolSize";
	public static final String CMD_OPT_THREAD_POOL_SIZE_SHORT = "ps";
	public static final String CMD_OPT_HOSTNAME = "hostname";
	public static final String CMD_OPT_HOSTNAME_SHORT = "h";
	public static final String CMD_OPT_CONFIG_FILE = "cfgFile";
	public static final String CMD_OPT_CONFIG_FILE_SHORT = "cf";
	
	private static final String CLI_VALUE_MAP_PORT_KEY = "port";
	private static final String CLI_VALUE_MAP_THREAD_POOL_SIZE_KEY = "threadPoolSize";
	private static final String CLI_VALUE_MAP_HOSTNAME_KEY = "hostname";
	private static final String CLI_VALUE_MAP_CONFIG_FILENAME_KEY = "cfgFileName";
	
	/**
	 * Starts up the consumer
	 * @param args
	 */
	public static void main(String[] args) {
		new TSConsumerMain().execute(args);
	}
	
	/**
	 * Executes the consumer :-)
	 * @param args
	 */
	protected void execute(String[] args) {
		
		CommandLineProcessor commandLineProcessor = new CommandLineProcessor();
		Map<String, Serializable> commandLineValues = commandLineProcessor.parseCommandLine(TSConsumerMain.class.getName(), args, getCommandLineOptions());
		if(commandLineValues != null && !commandLineValues.isEmpty()) {
			
			Long port = (Long)commandLineValues.get(CLI_VALUE_MAP_PORT_KEY);
			Long threadPoolSize = (Long)commandLineValues.get(CLI_VALUE_MAP_THREAD_POOL_SIZE_KEY);
			String hostname = (String)commandLineValues.get(CLI_VALUE_MAP_HOSTNAME_KEY);
			
			String additionalConfigFile = (String)commandLineValues.get(CLI_VALUE_MAP_CONFIG_FILENAME_KEY);
			Properties additionalProps = null;
			if(additionalConfigFile != null && !additionalConfigFile.isEmpty())
				additionalProps = loadAdditionalConfigProperties(additionalConfigFile);			
			
			ChannelFactory channelFactory = null;
			if(threadPoolSize != null && threadPoolSize.longValue() > 0)
				channelFactory = new NioServerSocketChannelFactory(Executors.newFixedThreadPool(threadPoolSize.intValue()), Executors.newFixedThreadPool(threadPoolSize.intValue()));
			else
				channelFactory = new NioServerSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool());
			
			ServerBootstrap serverBootstrap = new ServerBootstrap(channelFactory);
			serverBootstrap.setPipelineFactory(new JMSConsumerPipelineFactory(hostname, measuringPoint));
			serverBootstrap.setOption("child.tcpNoDelay", true);
			serverBootstrap.setOption("child.keepAlive", true);			
			serverBootstrap.bind(new InetSocketAddress(port.intValue()));
			
			logger.info("JMS consumer successfully started and listening on port '"+port.intValue()+"' for incoming http connections. See documentation for further details.");
		} else {		
			System.exit(-1);
		}	
	}
	
	/**
	 * Loads additional properties from a referenced file
	 * @param filename
	 * @return
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	protected Properties loadAdditionalConfigProperties(String filename) throws FileNotFoundException, IOException {
		Properties properties = new Properties();
		properties.load(new FileInputStream(filename));
		return properties;
	}

	/**
	 * Returns the available command-line options 
	 * @return
	 */
	protected List<CommandLineOption> getCommandLineOptions() {
	
		List<CommandLineOption> options = new ArrayList<CommandLineOption>();
		options.add(new CommandLineOption(CMD_OPT_PORT, CMD_OPT_PORT_SHORT, true, true, Long.class, "Communication port for http server", CLI_VALUE_MAP_PORT_KEY, "Missing value for required option '"+CMD_OPT_PORT+"' ("+CMD_OPT_PORT_SHORT+")"));
		options.add(new CommandLineOption(CMD_OPT_THREAD_POOL_SIZE, CMD_OPT_THREAD_POOL_SIZE_SHORT, false, true, Long.class, "Sized used for setting up the server socket thread pool (optional)", CLI_VALUE_MAP_THREAD_POOL_SIZE_KEY, null));
		options.add(new CommandLineOption(CMD_OPT_HOSTNAME, CMD_OPT_HOSTNAME_SHORT, true, true, String.class, "Name of the running host", CLI_VALUE_MAP_HOSTNAME_KEY, "Missing value for required option '"+CMD_OPT_HOSTNAME+"' ("+CMD_OPT_HOSTNAME_SHORT+")"));
		options.add(new CommandLineOption(CMD_OPT_CONFIG_FILE, CMD_OPT_CONFIG_FILE_SHORT, false, true, String.class, "Name of property file containing additional configuration options", CLI_VALUE_MAP_CONFIG_FILENAME_KEY, "Missing value for required option '"+CMD_OPT_CONFIG_FILE+"' ("+CMD_OPT_CONFIG_FILE_SHORT+")"));
		return options;
	}

}
