package org.ndexbio.rest.server;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher;

public class StandaloneServer {

	
	public static void main(String[] args) {
		Server server = new Server(8080);
		ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
		context.setContextPath("/");
		ServletHolder h = new ServletHolder(new HttpServletDispatcher());
		h.setInitParameter("javax.ws.rs.Application", "org.ndexbio.rest.NdexRestApi");
		context.addServlet(h, "/*");
		server.setHandler(context);
		try {
		server.start();
		server.join();
		} catch (Exception e) {
		e.printStackTrace();
		}
	}
	

}
