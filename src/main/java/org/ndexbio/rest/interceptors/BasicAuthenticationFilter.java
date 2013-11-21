package org.ndexbio.rest.interceptors;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

import javax.annotation.security.DenyAll;
import javax.annotation.security.PermitAll;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Request;
import javax.ws.rs.ext.Provider;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.jboss.resteasy.core.Headers;
import org.jboss.resteasy.core.ResourceMethodInvoker;
import org.jboss.resteasy.core.ServerResponse;
import org.jboss.resteasy.spi.HttpRequest;
import org.jboss.resteasy.util.Base64;
import org.ndexbio.rest.domain.IBaseTerm;
import org.ndexbio.rest.domain.IFunctionTerm;
import org.ndexbio.rest.domain.ITerm;
import org.ndexbio.rest.domain.IUser;

import com.orientechnologies.common.log.OLogManager;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentPool;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.exception.ODatabaseException;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.OCommandSQL;
import com.tinkerpop.blueprints.impls.orient.OrientBaseGraph;
import com.tinkerpop.blueprints.impls.orient.OrientGraph;
import com.tinkerpop.frames.FramedGraph;
import com.tinkerpop.frames.FramedGraphFactory;
import com.tinkerpop.frames.modules.gremlingroovy.GremlinGroovyModule;
import com.tinkerpop.frames.modules.typedgraph.TypedGraphModuleBuilder;

/*
 * class represents a RestEasy request filter that will validate
 * a user supplied credentials against those stored in the OrientDb database
 * 
 * this class is a modest refactoring of the legacy BasicAuthentication class.
 * the major change is that it now implements the ContainerRequestFilter interface
 */
@Provider
public class BasicAuthenticationFilter implements ContainerRequestFilter {

	private static final String AUTHORIZATION_PROPERTY = "Authorization";
	private static final String AUTHENTICATION_SCHEME = "Basic";
	private static final ServerResponse ACCESS_DENIED = new ServerResponse(
			"Invalid username or password.", 401, new Headers<Object>());
	private static final ServerResponse ACCESS_FORBIDDEN = new ServerResponse(
			"Forbidden.", 403, new Headers<Object>());
	private static final ServerResponse SERVER_ERROR = new ServerResponse(
			"Internal server error.", 500, new Headers<Object>());

	@Override
	public void filter(ContainerRequestContext requestContext)
			throws IOException {
		ResourceMethodInvoker methodInvoker = (ResourceMethodInvoker) requestContext
				.getProperty("org.jboss.resteasy.core.ResourceMethodInvoker");
		Method method = methodInvoker.getMethod();
		if (!method.isAnnotationPresent(PermitAll.class)) {
			// test for deny all level of access
			if (method.isAnnotationPresent(DenyAll.class)) {
				requestContext.abortWith(ACCESS_DENIED);
				return;
			}
			try {

				String[] authInfo = resolveUserCredentials(requestContext);
				if (ArrayUtils.isEmpty(authInfo))
					if (!ArrayUtils.isEmpty(authInfo)
							&& authenticateUser(authInfo)) {
						return;
					} else {
						requestContext.abortWith(ACCESS_DENIED);
						return;
					}
			} catch (Exception e) {
				requestContext.abortWith(SERVER_ERROR);
				return;
			}

		}

	}

	/*
	 * private method to resolve username and password from authorization
	 * property and return username and password as a String array
	 */
	private String[] resolveUserCredentials(
			ContainerRequestContext requestContext) throws IOException {
		final MultivaluedMap<String, String> headers = requestContext
				.getHeaders();
		final List<String> authHeader = headers.get(AUTHORIZATION_PROPERTY);
		if (CollectionUtils.isEmpty(authHeader)) {
			return null;
		}
		final String encodedAuthInfo = authHeader.get(0).replaceFirst(
				AUTHENTICATION_SCHEME + " ", "");
		String decodedAuthInfo;

		decodedAuthInfo = new String(Base64.decode(encodedAuthInfo));

		return decodedAuthInfo.split(":");

	}

	/**************************************************************************
	 * Authenticates the user against the OrientDB database.
	 * 
	 * @param authInfo
	 *            A string array containing the username/password.
	 * @throws Exception
	 *             Various exceptions if accessing the database fails.
	 * @returns True if the user is authenticated, false otherwise.
	 **************************************************************************/
	private boolean authenticateUser(final String[] authInfo) throws Exception {
		final FramedGraphFactory graphFactory = new FramedGraphFactory(
				new GremlinGroovyModule(), new TypedGraphModuleBuilder()
						.withClass(ITerm.class).withClass(IFunctionTerm.class)
						.withClass(IBaseTerm.class).build());

		ODatabaseDocumentTx ndexDatabase = null;
		try {
			// TODO: Refactor this to connect using a configurable
			// username/password, and database
			ndexDatabase = ODatabaseDocumentPool.global().acquire(
					"plocal:/ndex", "admin", "admin");
			final FramedGraph<OrientBaseGraph> orientDbGraph = graphFactory
					.create((OrientBaseGraph) new OrientGraph(ndexDatabase));

			Collection<ODocument> usersFound = ndexDatabase.command(
					new OCommandSQL("select from xUser where username equals "
							+ authInfo[0])).execute();
			if (usersFound.size() < 1)
				return false;

			IUser authUser = orientDbGraph.getVertex(usersFound.toArray()[0],
					IUser.class);

			if (authInfo[1] != authUser.getPassword())
				return false;

			return true;
		} catch (Exception e) {
			OLogManager.instance().error(this,
					"Cannot access database: " + "ndex" + ".",
					ODatabaseException.class, e);
		} finally {
			if (ndexDatabase != null)
				ndexDatabase.close();
		}

		return false;
	}

}
