/**
 * Copyright (C) 2012 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.main;

import org.apache.commons.cli.*;
import org.apache.log4j.Logger;
import org.dom4j.QName;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.common.Version;
import org.orbeon.oxf.pipeline.CommandLineExternalContext;
import org.orbeon.oxf.pipeline.InitUtils;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.PipelineEngine;
import org.orbeon.oxf.pipeline.api.PipelineEngineFactory;
import org.orbeon.oxf.pipeline.api.ProcessorDefinition;
import org.orbeon.oxf.processor.Processor;
import org.orbeon.oxf.processor.ProcessorOutput;
import org.orbeon.oxf.processor.serializer.URLSerializer;
import org.orbeon.oxf.properties.Properties;
import org.orbeon.oxf.resources.ResourceManagerWrapper;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.util.PipelineUtils;
import org.orbeon.oxf.webapp.ProcessorService;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.dom4j.LocationData;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.orbeon.oxf.pipeline.InitUtils.createProcessor;

/**
 * This is a simple command-line interface to the pipeline engine.
 *
 * The command-line interface also illustrates how to use the basic pipeline engine APIs.
 *
 * This code performs the following major steps:
 *
 * 1. Parse the command-line arguments
 * 2. Initialize a resource manager
 * 3. Initialize OXF Properties
 * 4. Initialize logger based on properties
 * 5. Build a processor definition object
 * 6. Initialize a PipelineContext
 * 7. Run the pipeline
 * 8. Display exceptions if needed
 */
public class OPS {

    private static Logger logger = Logger.getLogger(OPS.class);

    private String resourceManagerSandbox;
    private String[] otherArgs;

    private ProcessorDefinition processorDefinition;
    private String[] inputs;
    private String[] outputs;

    public OPS(String[] args) {
        // 1. Parse the command-line arguments
        parseArgs(args);
    }

    public void init() {

        // Initialize a basic logging configuration until the resource manager is setup
        LoggerFactory.initBasicLogger();

        // 2. Initialize resource manager
        // Resources are first searched in a file hierarchy, then from the classloader
        final Map<String, String> props = new LinkedHashMap<String, String>();
        props.put("oxf.resources.factory", "org.orbeon.oxf.resources.PriorityResourceManagerFactory");
        if (resourceManagerSandbox != null) {
            // Use a sandbox
            props.put("oxf.resources.filesystem.sandbox-directory", resourceManagerSandbox);
        }
        props.put("oxf.resources.priority.1", "org.orbeon.oxf.resources.FilesystemResourceManagerFactory");
        props.put("oxf.resources.priority.2", "org.orbeon.oxf.resources.ClassLoaderResourceManagerFactory");
        if (logger.isInfoEnabled())
            logger.info("Initializing Resource Manager with: " + props);
        ResourceManagerWrapper.init(props);

        // 3. Initialize properties with default properties file.
        Properties.init(Properties.DEFAULT_PROPERTIES_URI);

        // 4. Initialize log4j (using the properties this time)
        LoggerFactory.initLogger();

        // 5. Build processor definition from command-line parameters
        if (otherArgs != null && otherArgs.length == 1) {
            // Assume the pipeline processor and a config input
            processorDefinition = new ProcessorDefinition();
            processorDefinition.setName(new QName("pipeline", XMLConstants.OXF_PROCESSORS_NAMESPACE));

            final String configURL;
            if (!NetUtils.urlHasProtocol(otherArgs[0])) {
                // URL is considered relative to current directory
                try {
                    // Create absolute URL, and switch to the oxf: protocol
                    String fileURL = new URL(new File(".").toURI().toURL(), otherArgs[0]).toExternalForm();
                    configURL = "oxf:" + fileURL.substring(fileURL.indexOf(':') + 1);
                } catch (MalformedURLException e) {
                    throw new OXFException(e);
                }
            } else {
                configURL = otherArgs[0];
            }

            processorDefinition.addInput("config", configURL);

            // Add additional inputs if any
            for (int i=0; inputs!=null && i < inputs.length; i ++) {
                String input = inputs[i];
                int iEqual = input.indexOf("=");
                if (iEqual <= 0 || iEqual >= input.length() - 1) {
                    throw new OXFException("Input \"" + input + "\" doesn't follow the syntax <name>=<url>");
                }
                processorDefinition.addInput(input.substring(0, iEqual ), input.substring(iEqual + 1));
            }
        } else {
            throw new OXFException("No main processor definition found.");
        }
    }

    public void parseArgs(String[] args) {
        final Options options = new Options();
        {
            final Option o = new Option("r", "root", true, "Specifies the resource manager root");
            o.setRequired(false);
            options.addOption(o);
        }
        {
            final Option o = new Option("v", "version", false, "Displays the version of Orbeon Forms");
            o.setRequired(false);
            options.addOption(o);
        }
        {
            final Option o = new Option("i", "input", true, "Map an input to a URL (<input name>=<URL>)");
            o.setRequired(false);
            options.addOption(o);
        }
        {
            final Option o = new Option("o", "output", true, "Map an output to a URL (<output name>=<URL>)");
            o.setRequired(false);
            options.addOption(o);
        }

        try {
            // Parse the command line options
            final CommandLine cmd = new PosixParser().parse(options, args, true);

            // Get resource manager root if any
            resourceManagerSandbox = cmd.getOptionValue('r');
            // Get inputs and outputs if any
            inputs = cmd.getOptionValues('i');
            outputs = cmd.getOptionValues('o');

            // Check for remaining args
            otherArgs = cmd.getArgs();

            // Print version if asked
            if (cmd.hasOption('v')) {
                System.out.println(Version.getVersionString());
                // Terminate if there is no other argument and no pipeline URL
                if (!cmd.hasOption('r') && (otherArgs == null || otherArgs.length == 0))
                    System.exit(0);
            }

            if (otherArgs == null || otherArgs.length != 1) {
                new HelpFormatter().printHelp("Pipeline URL is required", options);
                System.exit(1);
            }

        } catch (MissingArgumentException e) {
            new HelpFormatter().printHelp("Missing argument", options);
            System.exit(1);
        } catch (UnrecognizedOptionException e) {
            new HelpFormatter().printHelp("Unrecognized option", options);
            System.exit(1);
        } catch (MissingOptionException e) {
            new HelpFormatter().printHelp("Missing option", options);
            System.exit(1);
        } catch (Exception e) {
            new HelpFormatter().printHelp("Unknown error", options);
            System.exit(1);
        }
    }

    public void start() {

        // 6. Initialize a PipelineContext
        final PipelineContext pipelineContext = new PipelineContext();

        // Some processors may require a JNDI context. In general, this is not required.
        final Context jndiContext;
        try {
            jndiContext = new InitialContext();
        } catch (NamingException e) {
            throw new OXFException(e);
        }
        pipelineContext.setAttribute(ProcessorService.JNDI_CONTEXT, jndiContext);

        try {
            // 7. Run the pipeline from the processor definition created earlier. An ExternalContext
            // is supplied for those processors using external contexts, such as most serializers.

            if (outputs == null) {
                // No outputs to connect: just execute the pipeline
                PipelineEngine pipelineEngine = PipelineEngineFactory.instance();
                pipelineEngine.executePipeline(processorDefinition, new CommandLineExternalContext(), pipelineContext, logger);
            } else {
                // At least one output to connect...
                InitUtils.initializeProcessorDefinitions();
                Processor processor = createProcessor(processorDefinition);
                processor.reset(pipelineContext);
                // Loop over outputs
                for (int i=0;  i < outputs.length; i ++) {
                    String outputArg = outputs[i];
                    int iEqual = outputArg.indexOf("=");
                    if (iEqual <= 0 || iEqual >= outputArg.length() - 1) {
                        throw new OXFException("Output \"" + outputArg + "\" doesn't follow the syntax <name>=<url>");
                    }
                    String outputName = outputArg.substring(0, iEqual );
                    URL url = URLFactory.createURL(outputArg.substring(iEqual + 1));
                    // Create the output
                    ProcessorOutput output = processor.createOutput(outputName);
                    // Connect to an URLserializer
                    URLSerializer urlSerializer = new URLSerializer();
                    PipelineUtils.connect(processor, output.getName(), urlSerializer, "data");
                    // Serialize
                    urlSerializer.serialize(pipelineContext, url);                    
                }

            }
        } catch (Exception e) {
            // 8. Display exceptions if needed
            final LocationData locationData = ValidationException.getRootLocationData(e);
            Throwable throwable = OXFException.getRootThrowable(e);
            final String message = locationData == null
                    ? "Exception with no location data"
                    : "Exception at " + locationData.toString();
            logger.error(message, throwable);
            System.exit(1);
        }
    }

    public static void main(String[] args) {
        try {
            final OPS orbeon = new OPS(args);
            orbeon.init();
            orbeon.start();
        } catch (Exception e) {
            System.out.println(e.getMessage());
            OXFException.getRootThrowable(e).printStackTrace();
            System.exit(1);
        }
    }
}
