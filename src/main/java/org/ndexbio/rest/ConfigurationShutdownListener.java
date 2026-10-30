package org.ndexbio.rest;

import java.io.IOException;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Closes the webapp's {@link Configuration} - and with it the shared Solr client - when the
 * webapp stops.
 *
 * <p>{@code contextDestroyed} runs after every servlet and filter has been destroyed, so the
 * client is closed after its last user: the REST dispatcher has already stopped its task
 * processors, and the MCP servlets have finished their requests.</p>
 */
public class ConfigurationShutdownListener implements ServletContextListener {

	private static final Logger logger = LoggerFactory.getLogger(ConfigurationShutdownListener.class);

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		Configuration configuration = Configuration.getInstance();
		if (configuration == null) {
			return;
		}
		try {
			configuration.close();
			logger.info("Configuration closed.");
		} catch (IOException e) {
			logger.error("Failed to close configuration: " + e.getMessage(), e);
		}
	}
}
