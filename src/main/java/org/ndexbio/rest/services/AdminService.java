/**
 * Copyright (c) 2013, 2016, The Regents of the University of California, The Cytoscape Consortium
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
package org.ndexbio.rest.services;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;

import org.eclipse.jetty.server.Server;
import org.ndexbio.common.access.NdexDatabase;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.NdexStatus;
import org.ndexbio.rest.Configuration;
import org.ndexbio.rest.server.StandaloneServer;


@Path("/admin")
public class AdminService extends NdexService {
//	private static Logger logger = LoggerFactory.getLogger(AdminService.class);
	
//	static final String defaultPostEdgeLimit = "800000";
	
	private static final String postElementLimitProp = "ServerPostElementLimit";
	
	private static final String ndexServerVersion = "2.0";

	public AdminService(@Context HttpServletRequest httpRequest)
    {
        super(httpRequest);
	}

	/**************************************************************************
	 * 
	 * Gets status for the service.
	 * @throws NdexException 
	 * @throws SQLException 
	 **************************************************************************/

	@SuppressWarnings("static-method")
	@GET
	@PermitAll
	@NdexOpenFunction
	@Path("/status")
	@Produces("application/json")
	public NdexStatus getStatus() throws NdexException, SQLException	{

	//	logger.info("[start: Getting status]");
		
		try (Connection db =NdexDatabase.getInstance().getConnection()){
			
			NdexStatus status = new NdexStatus();
			status.setNetworkCount(AdminServiceV2.getClassCount(db,"network"));
			status.setUserCount(AdminServiceV2.getClassCount(db,"ndex_user"));
			status.setGroupCount(AdminServiceV2.getClassCount(db,"ndex_group")); 

			Map<String,Object> props = status.getProperties();
			
			String edgeLimit = Configuration.getInstance().getProperty(Configuration.networkPostEdgeLimit);
			if ( edgeLimit != null ) {
				try {
					int i = Integer.parseInt(edgeLimit);
					props.put(postElementLimitProp, Integer.toString(i));
				} catch( NumberFormatException e) {
//					logger.error("[Invalid value in server property {}]", Configuration.networkPostEdgeLimit);
			//		props.put("ServerPostEdgeLimit", "-1");  //defaultPostEdgeLimit);
				}
			} /* else {
				props.put(postElementLimitProp, "-1"); // defaultPostEdgeLimit);
			} */
		    
			props.put("ServerResultLimit", "10000");
			props.put("ServerVersion", ndexServerVersion);
			status.setProperties(props);
	//		logger.info("[end: Got status]");
			
			
			return status;
		} 
	}

	
	
	/*
	 * Shut down the server.  Currently it only works for Jetty.  We need it for our performance benchmarking.
	 * 
	 * In future we have to 
	 *    1) add support for Tomcat
	 *    2) only allow privileged users to shut down Tomcat.
	 */
	@GET
	@PermitAll
	@NdexOpenFunction
	@Path("/shutdown")
	@Produces("application/json")
	public void shutDown()	{
		logger.info("[start: shutdown server]");
		
		Server jettyServer = StandaloneServer.getJettyServer();
		if (null != jettyServer) {			
			stopJettyServer(jettyServer);			
			// the following log entry will not log since the server will be down; but let's still have it
			logger.info("[end: shutdown server]");			
			System.exit(0);
		}
	}
	
	private static void stopJettyServer(Server server) {
		if (null != server) {
	        try {
	    		NdexDatabase.close();
	        	server.stop();
	        } catch (Exception e) {/* */}
		}
		return;
	}

	
	/*private static int getClassCount(Connection db,String className) throws SQLException, NdexException {

		String queryStr = "select reltuples as cnt from pg_class where relname = ?";
		try (PreparedStatement st = db.prepareStatement(queryStr)) { 
			st.setString(1, className);
			try (ResultSet rs = st.executeQuery()) {
				if ( rs.next()) {
					return rs.getInt(1);
				}
			}
		}
		
		throw new NdexException("Failed to get Ndex db statistics.");

	} */
	

}
