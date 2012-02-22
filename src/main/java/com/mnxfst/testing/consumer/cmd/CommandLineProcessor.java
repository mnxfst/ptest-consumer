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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;

/**
 * Processes the command-line and returns either a map containing provided key/value
 * pairs or prints out an error message.
 * @author ckreutzfeldt
 * @since 21.02.2012
 */
public class CommandLineProcessor {

	/**
	 * Parses the provides command-line for contained values. If the command-line does not match
	 * the requirements imposed by the options list or any other error occurs, the method prints 
	 * out a help message plus an error hint to System.out and returns null 
	 * @param args
	 * @param commandLineOptions
	 * @return
	 */
	public Map<String, Serializable> parseCommandLine(String applicationTitle, String[] args, List<CommandLineOption> commandLineOptions) {
		 
		// if the command line options are empty, write an error message and thrown an exception
		if(commandLineOptions == null || commandLineOptions.isEmpty()) {
			printMessage(applicationTitle, null, "Command-line options missing");
			return null;
		}

		// get command line options as processable object
		Options cliOptions = new Options();
		for(CommandLineOption o : commandLineOptions)
			cliOptions.addOption(o.asCliOption());
		
		// parse the command-line
		CommandLine cmdLine = null;
		try {
			cmdLine = new PosixParser().parse(cliOptions, args);
		} catch(ParseException e) {
			printMessage(applicationTitle, cliOptions, "Failed to parse command-line. Error: " + e.getMessage());
			return null;
		}

		// iterate through configured command-line options and parse out values from provided command-line values
		Map<String, Serializable> commandLineValues = new HashMap<String, Serializable>();
		for(CommandLineOption o : commandLineOptions) {
			try {
				if(o.getValueClass() == Long.class) {
					// extract value and insert into result map only if the value is valid --> if the option is required and no value
					// has been provided, an exception will be thrown thus it is not required to analyze this here
					Long value = extractLongValue(cmdLine, o.getCommandLineOption(), o.getCommandLineOptionShort(), o.isRequired());
					if(value != null)
						commandLineValues.put(o.getValueMapName(), value);
				} else if(o.getValueClass() == String.class) {
					// extract value and insert into result map only if the value is valid --> if the option is required and no value
					// has been provided, an exception will be thrown thus it is not required to analyze this here
					String value = extractStringValue(cmdLine, o.getCommandLineOption(), o.getCommandLineOptionShort(), o.isRequired());
					if(value != null)
						commandLineValues.put(o.getValueMapName(), value);
				}
			} catch(CommandLineProcessingException e) {
				printMessage(applicationTitle, cliOptions, o.getMissingValueMessage());
				return null;
			}
		}
				
		return commandLineValues;
		
	}
	
	/**
	 * Prints out a message to standard out
	 * @param applicationTitle
	 * @param options
	 * @param additionalMessage
	 */
	protected void printMessage(String applicationTitle, Options options, String additionalMessage) {
		if(options != null) {
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp(applicationTitle, options );
		} else {
			System.out.println("Missing internal command-line description. Please contact your administrator!");
		}
		if(additionalMessage != null && !additionalMessage.isEmpty())
			System.out.println("\n" + additionalMessage);
	}
	
	/**
	 * Extracts a long value from the named command-line option
	 * @param cmd
	 * @param opt
	 * @param shortOpt
	 * @return
	 * @throws CommandLineProcessingException
	 */
	protected Long extractLongValue(CommandLine cmd, String opt, String shortOpt, boolean required) throws CommandLineProcessingException {		
		String tmp = cmd.getOptionValue(opt);
		if(tmp == null || tmp.isEmpty())
			tmp = cmd.getOptionValue(shortOpt);
		if(required && (tmp == null || tmp.isEmpty()))
			throw new CommandLineProcessingException("Missing value for required option '"+opt+"' ('"+shortOpt+"')");

		if(tmp != null && !tmp.trim().isEmpty()) {
			try {
				return Long.valueOf(tmp.trim());
			} catch(NumberFormatException e) {
				throw new CommandLineProcessingException("Value for required option '"+opt+"' ('"+shortOpt+"') does not represent a valid numerical value: " + tmp);
			}
		} 
		return null;
	}
	
	/**
	 * Extracts a long value from the named command-line option
	 * @param cmd
	 * @param opt
	 * @param shortOpt
	 * @return
	 * @throws CommandLineProcessingException
	 */
	protected String extractStringValue(CommandLine cmd, String opt, String shortOpt, boolean required) throws CommandLineProcessingException {		
		String tmp = cmd.getOptionValue(opt);
		if(tmp == null || tmp.isEmpty())
			tmp = cmd.getOptionValue(shortOpt);
		if(required && (tmp == null || tmp.isEmpty()))
			throw new CommandLineProcessingException("Missing value for required option '"+opt+"' ('"+shortOpt+"')");

		return (tmp != null ? tmp.trim() : null);
	}
	
}
