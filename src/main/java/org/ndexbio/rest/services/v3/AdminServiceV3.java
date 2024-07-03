package org.ndexbio.rest.services.v3;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.SQLException;
import java.util.Base64;

import javax.annotation.security.PermitAll;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;

import org.ndexbio.common.models.dao.postgresql.UserDAO;
import org.ndexbio.model.exceptions.BadRequestException;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.NdexStatus;
import org.ndexbio.model.object.User;
import org.ndexbio.rest.Configuration;
import org.ndexbio.rest.services.AdminServiceV2;
import org.ndexbio.rest.services.NdexOpenFunction;
import org.ndexbio.rest.services.NdexService;


@Path("/v3/admin")

public class AdminServiceV3 extends NdexService {

	public AdminServiceV3(@Context HttpServletRequest httpRequest) {
		super(httpRequest);
		// TODO Auto-generated constructor stub
	}
	
	@GET
	@PermitAll
	@NdexOpenFunction
	@Path("/status")
	@Produces("application/json")
	public NdexStatus getStatus(
			@DefaultValue("short") @QueryParam("format") String format) throws NdexException, SQLException	{
		AdminServiceV2 adminService = new AdminServiceV2(this._httpRequest );
		return adminService.getStatus(format);
	}
	
	
	@PUT
	@PermitAll
	@Path("user")
	@Produces("application/json")
	public void updatePassword(	
			@QueryParam("key") final String accessKey,
			User newUser
			) throws Exception {
		
		   String clientIp = this._httpRequest.getRemoteAddr();
	       String clientHostname = clientIp;
	        
		   //resolve host name
	        try {
	            InetAddress inetAddress = InetAddress.getByName(clientIp);
	            clientHostname = inetAddress.getHostName();
	            String hostStr = Configuration.getInstance().getProperty("ALLOWED_ADMIN_HOSTS");
				if (hostStr == null || hostStr.length() == 0)
					throw new NdexException(
							"ALLOWED_ADMIN_HOSTS property is not set in ndex.properties file. Please contact your system administrator.");
	            String[] hosts = hostStr.split("[,\\s]+");
	            boolean isAllowed = false;
	            for (String host : hosts)
                	isAllowed = isAllowed || host.equals(clientHostname);
	            if (!isAllowed)
	            	throw new UnauthorizedOperationException("Host " + clientHostname + " is not allowed to access this service.");
	            
	        } catch (UnknownHostException e) {
	            // Handle the exception
	            e.printStackTrace();
	        }
		
	        System.out.println("Update pswd requect from client with hostname: " + clientHostname);
	        
	    	if (accessKey == null || accessKey.length() == 0) {
				throw new BadRequestException("key is missing.");
			}
	    	
			String pswd = Configuration.getInstance().getSystemUserPassword();
			//getbase64 decoded password from the key
			String pwsd2 =new String(Base64.getDecoder().decode(accessKey));
			if ( !pswd.equals(pwsd2))
                throw new UnauthorizedOperationException("Access key is not valid.");
			
			
			
			//update user's password
			try (UserDAO dao = new UserDAO ()) {
				User u = dao.getUserByAccountName(newUser.getUserName(), true, false);
				dao.setNewPassword(u.getExternalId(),newUser.getPassword());
				dao.commit();
			}
	        
	    }
		

}
