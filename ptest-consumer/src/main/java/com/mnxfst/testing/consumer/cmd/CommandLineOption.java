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

package com.mnxfst.testing.consumer.cmd;

import java.io.Serializable;

import org.apache.commons.cli.Option;
import org.apache.commons.lang.builder.ToStringBuilder;


/**
 * Provides information about a single command-line option
 * @author ckreutzfeldt
 * @since 21.02.2012
 */
public class CommandLineOption {

	private String commandLineOption = null;
	private String commandLineOptionShort = null;
	private boolean required = false;
	private boolean argumentProvided = false;
	private String description = null;
	private String valueMapName = null; 
	private String missingValueMessage = null;
	private Class<? extends Serializable> valueClass = null;
	
	public CommandLineOption(String commandLineOption, String commandLineOptionShort, boolean required, boolean argumentProvided, Class<? extends Serializable> valueClass, String description, String valueMapName, String missingValueMessage) {
		this.commandLineOption = commandLineOption;
		this.commandLineOptionShort = commandLineOptionShort;
		this.argumentProvided = argumentProvided;
		this.required = required;
		this.description = description;
		this.missingValueMessage = missingValueMessage;
		this.valueClass = valueClass;
		this.valueMapName = valueMapName;
	}

	public String getCommandLineOption() {
		return commandLineOption;
	}

	public String getCommandLineOptionShort() {
		return commandLineOptionShort;
	}

	public String getDescription() {
		return description;
	}

	public boolean isRequired() {
		return required;
	}

	public String getMissingValueMessage() {
		return missingValueMessage;
	}
		
	public Class<? extends Serializable> getValueClass() {
		return valueClass;
	}

	public String getValueMapName() {
		return valueMapName;
	}

	public boolean isArgumentProvided() {
		return argumentProvided;
	}

	/**
	 * Returns the entity as {@link Option apache commons cli option}
	 * @return
	 */
	public Option asCliOption() {
		return new Option(commandLineOptionShort, commandLineOption, argumentProvided, description);
	}
	
	/**
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		return new ToStringBuilder(this)
			.append("commandLineOption", this.commandLineOption)
			.append("commandLineOptionShort", this.commandLineOptionShort)
			.append("required", this.required)
			.append("argumentProvided", this.argumentProvided)
			.append("description", this.description)
			.append("valueClass", this.valueClass)
			.append("valueMapName", this.valueMapName)
			.append("missingValueMessage", this.missingValueMessage).toString();
	}
	
}
