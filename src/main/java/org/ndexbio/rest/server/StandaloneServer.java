/**
 *   Copyright (c) 2013, 2015
 *  	The Regents of the University of California
 *  	The Cytoscape Consortium
 *
 *   Permission to use, copy, modify, and distribute this software for any
 *   purpose with or without fee is hereby granted, provided that the above
 *   copyright notice and this permission notice appear in all copies.
 *
 *   THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 *   WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 *   MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
 *   ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 *   WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 *   ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
 *   OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */
package org.ndexbio.rest.server;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher;
import org.ndexbio.common.access.NdexAOrientDBConnectionPool;
import org.ndexbio.common.access.NdexDatabase;
import org.ndexbio.common.models.dao.orientdb.UserDAO;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.rest.NdexHttpServletDispatcher;
import org.ndexbio.task.Configuration;
import org.ndexbio.task.utility.DatabaseInitializer;

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import org.apache.log4j.PropertyConfigurator;
/*
 * This class is just for testing purpose at the moment.
 */
public class StandaloneServer {

	
	public static void main(String[] args) {
		
		//System.out.println("Log file location:" + StandaloneServer.class.getClassLoader().getResource("logging.properties"));
		
/*		Configuration configuration = null;
		try {
			configuration = Configuration.getInstance();
			//and initialize the db connections
			NdexAOrientDBConnectionPool.createOrientDBConnectionPool(
    			configuration.getDBURL(),
    			configuration.getDBUser(),
    			configuration.getDBPasswd(),40);
    	
			NdexDatabase db = new NdexDatabase (configuration.getHostURI());
    	
			System.out.println("Db created for " + NdexDatabase.getURIPrefix());
    	
			ODatabaseDocumentTx conn = db.getAConnection();
			UserDAO dao = new UserDAO(conn);
    	
			DatabaseInitializer.createUserIfnotExist(dao, configuration.getSystmUserName(), "support@ndexbio.org", 
    				configuration.getSystemUserPassword());
			conn.commit();
			conn.close();
			db.close();		
		} catch (NdexException e) {
			e.printStackTrace();
			throw e;
		}
 */   	

		Server server = new Server(8080);
		ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
		context.setContextPath("/ndexbio-rest");
		ServletHolder h = new ServletHolder(new NdexHttpServletDispatcher());
		h.setInitParameter("javax.ws.rs.Application", "org.ndexbio.rest.NdexRestApi");
		context.addServlet(h, "/*");
		server.setHandler(context);
		try {
			server.start();
			server.join();
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("Shutting down server");
		NdexDatabase.close();
	}
	

}
