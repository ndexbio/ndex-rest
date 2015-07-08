/**
 * Copyright (c) 2013, 2015, The Regents of the University of California, The Cytoscape Consortium
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */
package org.ndexbio.rest.server;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.ndexbio.common.access.NdexDatabase;
import org.ndexbio.rest.NdexHttpServletDispatcher;



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
		
		
		// From http://logback.qos.ch/manual/configuration.html:
		// Logback relies on a configuration library called Joran, part of logback-core. Logback's default configuration 
		// mechanism invokes JoranConfigurator on the default configuration file it finds on the class path. If you wish 
		// to override logback's default configuration mechanism for whatever reason, you can do so by invoking 
		// JoranConfigurator directly.

		// In this module (StandaloneServer.java), we are starting the embedded Jetty server and we need 
		// to programatically initialize logback with configuration file located in the same directory as the source code.
		// The code below is taken from http://logback.qos.ch/faq.html
		/*
		LoggerContext c = (LoggerContext) LoggerFactory.getILoggerFactory(); 
		JoranConfigurator jc = new JoranConfigurator(); 
		jc.setContext(c); 
		c.reset(); 
		 
		LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
	    StatusPrinter.print(lc);
		*/
		// override default configuration 
	    /*
		try {
            jc.doConfigure("src/main/java/org/ndexbio/rest/server/jetty-logback.xml");
		} catch (JoranException e1) {
            e1.printStackTrace();
			System.exit(1);
		} 
		*/
	    
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
