package org.ndexbio.rest.mcp;

import org.ndexbio.rest.Configuration;

/**
 * Production implementation of {@link ConfigLocator} that delegates to the
 * {@link Configuration} singleton. Returns {@code null} when the singleton has
 * not been initialised (e.g. during startup or in unit-test scope).
 */
public class DefaultConfigLocator implements ConfigLocator {

    @Override
    public String getHostURI() {
        Configuration cfg = Configuration.getInstance();
        return cfg != null ? cfg.getHostURI() : null;
    }
}
