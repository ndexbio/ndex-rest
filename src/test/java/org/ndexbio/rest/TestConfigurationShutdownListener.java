package org.ndexbio.rest;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.ndexbio.common.solr.SolrObjectFactory;

@RunWith(JUnit4.class)
public class TestConfigurationShutdownListener {

	@Rule
	public TemporaryFolder _tmpFolder = new TemporaryFolder();

	private Configuration savedInstance;

	@Before
	public void setUp() {
		savedInstance = Configuration.getInstance();
	}

	@After
	public void tearDown() {
		Configuration.setInstance(savedInstance);
	}

	/** Webapp shutdown closes the configuration, which releases the shared Solr client. */
	@Test
	public void contextDestroyedClosesTheConfiguration() throws Exception {
		Configuration config = createConfiguration();
		SolrObjectFactory realFactory = config.getSolrObjectFactory();
		SolrObjectFactory mockFactory = createMock(SolrObjectFactory.class);
		mockFactory.close();
		expectLastCall();
		replay(mockFactory);
		config.setSolrObjectFactory(mockFactory);

		new ConfigurationShutdownListener().contextDestroyed(null);

		verify(mockFactory);
		realFactory.close();
	}

	/** A failed close is logged, not thrown: the container is shutting down either way. */
	@Test
	public void contextDestroyedSurvivesAFailedClose() throws Exception {
		Configuration config = createConfiguration();
		SolrObjectFactory realFactory = config.getSolrObjectFactory();
		SolrObjectFactory mockFactory = createMock(SolrObjectFactory.class);
		mockFactory.close();
		expectLastCall().andThrow(new IOException("already closed"));
		replay(mockFactory);
		config.setSolrObjectFactory(mockFactory);

		new ConfigurationShutdownListener().contextDestroyed(null);

		verify(mockFactory);
		realFactory.close();
	}

	/** A webapp whose startup never built a configuration has nothing to close. */
	@Test
	public void contextDestroyedWithoutAConfigurationDoesNothing() {
		Configuration.setInstance(null);

		new ConfigurationShutdownListener().contextDestroyed(null);
	}

	private Configuration createConfiguration() throws Exception {
		File tmpFolder = _tmpFolder.newFolder();
		File configFile = new File(tmpFolder, "config");
		try (BufferedWriter bw = new BufferedWriter(new FileWriter(configFile))) {
			bw.write("NdexDBURL=somedburl\nNdexSystemUser=sysuser\nNdexSystemUserPassword=hithere\n");
			bw.write("NdexRoot=" + tmpFolder.getCanonicalPath() + "\nHostURI=http://localhost\n");
			bw.write("NDEX_KEY=effggekk\nDOI_CREATOR=CqmUkFe5kW5sJVMKNQYYLg==\nMigrationPassword=migration\n");
		}
		return Configuration.reCreateInstance(configFile.getCanonicalPath());
	}
}
