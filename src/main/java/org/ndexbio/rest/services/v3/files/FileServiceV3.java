package org.ndexbio.rest.services.v3.files;

import java.util.List;
import java.util.UUID;

import org.ndexbio.common.models.dao.postgresql.NetworkDAO;
import org.ndexbio.cx2.aspect.element.core.CxMetadata;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.FileCount;
import org.ndexbio.rest.filters.BasicAuthenticationFilter;
import org.ndexbio.rest.services.NdexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/v3/files")
public class FileServiceV3 extends NdexService {

	protected static Logger accLogger = LoggerFactory.getLogger(BasicAuthenticationFilter.accessLoggerName);

	public FileServiceV3(@Context HttpServletRequest httpRequest) {
		super(httpRequest);
	}

	@PermitAll
	@GET
	@Path("/count")
	@Produces("application/json")
	public Response getCount() throws Exception {
		FileCount count = new FileCount();
		
		return 	Response.ok().type(MediaType.APPLICATION_JSON_TYPE).entity(count).build();

	}
	

}
