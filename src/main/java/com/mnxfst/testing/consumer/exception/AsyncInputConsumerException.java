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
package com.mnxfst.testing.consumer.exception;

/**
 * Thrown in case anything fails during input processing in the context of an async consumer
 * or while consumer configuration
 * @author ckreutzfeldt
 * @since 23.02.2012
 */
public class AsyncInputConsumerException extends Exception {

	private static final long serialVersionUID = -7405534648499212487L;

	public AsyncInputConsumerException() {		
	}
	
	public AsyncInputConsumerException(String msg) {
		super(msg);
	}
	
	public AsyncInputConsumerException(Throwable cause) {
		super(cause);
	}
	
	public AsyncInputConsumerException(String msg, Throwable cause) {
		super(msg, cause);
	}	

}
