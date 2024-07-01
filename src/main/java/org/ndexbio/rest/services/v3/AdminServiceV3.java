package org.ndexbio.rest.services.v3;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.SQLException;
import java.util.Base64;

import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;

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
