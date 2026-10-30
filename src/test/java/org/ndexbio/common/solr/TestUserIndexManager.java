package org.ndexbio.common.solr;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;

import java.util.Collection;
import java.util.Map;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.easymock.Capture;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link UserIndexManager}: every operation goes through the shared-client wrapper
 * against the {@code ndex-users} core, and nothing commits explicitly (Solr autoCommit does).
 */
@RunWith(JUnit4.class)
public class TestUserIndexManager {

	private static final String CORE = "ndex-users";
	private static final String USER_ID = "8b9e7a2c-0000-11ee-8a13-005056ae23aa";

	@Test
	public void createCoreIfNotExistsUsesTheUsersConfigSet() throws Exception {
		SolrClientWrapper wrapper = createMock(SolrClientWrapper.class);
		wrapper.createCoreIfNeeded(CORE);
		expectLastCall();
		replay(wrapper);

		new UserIndexManager(wrapper).createCoreIfNotExists();

		verify(wrapper);
	}

	@Test
	public void dropIndexDropsTheUsersCore() throws Exception {
		SolrClientWrapper wrapper = createMock(SolrClientWrapper.class);
		wrapper.dropCore(CORE);
		expectLastCall();
		replay(wrapper);

		new UserIndexManager(wrapper).dropIndex();

		verify(wrapper);
	}

	@Test
	public void searchUsersQueriesTheUsersCore() throws Exception {
		SolrDocumentList results = new SolrDocumentList();
		QueryResponse response = createMock(QueryResponse.class);
		expect(response.getResults()).andReturn(results);
		SolrClientWrapper wrapper = createMock(SolrClientWrapper.class);
		Capture<SolrQuery> query = Capture.newInstance();
		expect(wrapper.query(eq(CORE), capture(query))).andReturn(response);
		replay(wrapper, response);

		assertSame(results, new UserIndexManager(wrapper).searchUsers("smith", 25, 50));

		verify(wrapper);
		assertEquals(Integer.valueOf(25), query.getValue().getRows());
		assertEquals(Integer.valueOf(50), query.getValue().getStart());
		assertEquals("edismax", query.getValue().get("defType"));
		assertEquals("uuid^10 userName^5 firstName^3 lastName^3 displayName^2 description",
				query.getValue().get("qf"));
	}

	@Test
	public void searchingEverythingSortsByUuid() throws Exception {
		QueryResponse response = createMock(QueryResponse.class);
		expect(response.getResults()).andReturn(new SolrDocumentList());
		SolrClientWrapper wrapper = createMock(SolrClientWrapper.class);
		Capture<SolrQuery> query = Capture.newInstance();
		expect(wrapper.query(eq(CORE), capture(query))).andReturn(response);
		replay(wrapper, response);

		new UserIndexManager(wrapper).searchUsers("*:*", 0, -1);

		assertEquals("uuid desc", query.getValue().get("sort"));
		assertEquals(null, query.getValue().getRows());
	}

	@Test
	public void addUserAddsOneDocumentWithOnlyTheFieldsGiven() throws Exception {
		SolrClientWrapper wrapper = createMock(SolrClientWrapper.class);
		Capture<Collection<SolrInputDocument>> docs = Capture.newInstance();
		wrapper.add(eq(CORE), capture(docs));
		expectLastCall();
		replay(wrapper);

		new UserIndexManager(wrapper).addUser(USER_ID, "jsmith", "Jane", null, "Jane S", null);

		verify(wrapper);
		assertEquals(1, docs.getValue().size());
		SolrInputDocument doc = docs.getValue().iterator().next();
		assertEquals(USER_ID, doc.getFieldValue(UserIndexManager.UUID));
		assertEquals("jsmith", doc.getFieldValue("userName"));
		assertEquals("Jane", doc.getFieldValue("firstName"));
		assertEquals("Jane S", doc.getFieldValue("displayName"));
		assertFalse(doc.containsKey("lastName"));
		assertFalse(doc.containsKey("description"));
	}

	@Test
	public void updateUserSendsAtomicSetsForEveryField() throws Exception {
		SolrClientWrapper wrapper = createMock(SolrClientWrapper.class);
		Capture<Collection<SolrInputDocument>> docs = Capture.newInstance();
		wrapper.add(eq(CORE), capture(docs));
		expectLastCall();
		replay(wrapper);

		new UserIndexManager(wrapper).updateUser(USER_ID, "jsmith", "Jane", "Smith", "Jane S", "bio");

		verify(wrapper);
		SolrInputDocument doc = docs.getValue().iterator().next();
		assertEquals(USER_ID, doc.getFieldValue(UserIndexManager.UUID));
		assertEquals(Map.of("set", "Smith"), doc.getFieldValue("lastName"));
		assertEquals(Map.of("set", "bio"), doc.getFieldValue("description"));
	}

	@Test
	public void deleteUserDeletesWithoutAnExplicitCommit() throws Exception {
		SolrClientWrapper wrapper = createMock(SolrClientWrapper.class);
		wrapper.delete(CORE, USER_ID, false);
		expectLastCall();
		replay(wrapper);

		new UserIndexManager(wrapper).deleteUser(USER_ID);

		verify(wrapper);
	}

	/** A request-path user write reports Solr being down the way every other Solr call does. */
	@Test(expected = SolrUnavailableException.class)
	public void addUserPassesSolrUnavailabilityThrough() throws Exception {
		SolrClientWrapper wrapper = createMock(SolrClientWrapper.class);
		wrapper.add(eq(CORE), anyObject());
		expectLastCall().andThrow(new SolrUnavailableException(new java.net.ConnectException()));
		replay(wrapper);

		new UserIndexManager(wrapper).addUser(USER_ID, "jsmith", null, null, null, null);
	}
}
