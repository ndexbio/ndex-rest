package org.ndexbio.rest.mcp;

/**
 * Abstracts access to the server's configured HostURI so tools can be tested
 * without initialising the real Configuration singleton.
 */
@FunctionalInterface
public interface ConfigLocator {

    /**
     * Returns the HostURI from the active server configuration (e.g. {@code "https://www.ndexbio.org"}),
     * or {@code null} if the configuration is unavailable.
     */
    String getHostURI();
}
