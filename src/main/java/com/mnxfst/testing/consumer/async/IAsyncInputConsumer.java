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
package com.mnxfst.testing.consumer.async;

import java.util.List;
import java.util.Map;

import com.mnxfst.testing.consumer.exception.AsyncInputConsumerException;

/**
 * Common interface to all implementations consuming incoming messages asynchronously
 * @author ckreutzfeldt
 * @since 23.02.2012
 */
public interface IAsyncInputConsumer extends Runnable {

	/**
	 * Initializes the async input consumer
	 * @param properties
	 * @throws AsyncInputConsumerException
	 */
	public void initialize(Map<String, List<String>> properties) throws AsyncInputConsumerException;

	/**
	 * Returns the statistical information collected so far
	 * @return
	 * @throws AsyncInputConsumerException
	 */
	public AsyncInputConsumerStatistics getConsumerStatistics();
	
	/**
	 * Shuts down the current async consumer
	 * @throws AsyncInputConsumerException
	 */
	public void shutdown() throws AsyncInputConsumerException;
	
	/**
	 * Returns the unique input consumer identifier
	 * @return
	 */
	public String getId();
	
	/**
	 * Sets the unique input consumer identifier
	 * @param id
	 */
	public void setId(String id);
	
	/**
	 * Returns the consumer type
	 * @return
	 */
	public String getType(); 
	
	/**
	 * Sets the consumer type
	 * @param type
	 */
	public void setType(String type);
	
	
}
