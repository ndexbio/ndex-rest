package org.ndexbio.rest.services.v3;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.security.PermitAll;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.ndexbio.common.models.dao.postgresql.NetworkDAO;
import org.ndexbio.common.persistence.CX2NetworkLoader;
import org.ndexbio.cx2.aspect.element.core.CxAttributeDeclaration;
import org.ndexbio.cx2.aspect.element.core.CxEdge;
import org.ndexbio.cx2.aspect.element.core.DeclarationEntry;
import org.ndexbio.cxio.aspects.datamodels.EdgeAttributesElement;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.network.query.FilterCriterion;
import org.ndexbio.rest.Configuration;
import org.ndexbio.rest.filters.BasicAuthenticationFilter;
import org.ndexbio.rest.services.NdexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;


@Path("/v3/search")

public class SearchServiceV3 extends NdexService  {

	static Logger accLogger = LoggerFactory.getLogger(BasicAuthenticationFilter.accessLoggerName);
	
	public SearchServiceV3(@Context HttpServletRequest httpRequest) {
		super(httpRequest);
	}

	
	@PermitAll
	@POST
	@Path("/networks/{networkId}/edges")
	//@Produces("application/json")
	
	public Response getEdges(	@PathParam("networkId") final String networkId,
				@DefaultValue("-1") @QueryParam("size") int limit,
				@DefaultValue("desc") @QueryParam("order") String order,
				@DefaultValue("cx2")  @QueryParam("format") String format,
				final FilterCriterion queryParameter) throws SQLException, NdexException, JsonParseException, JsonMappingException, IOException
	{
				
		UUID networkUUID = UUID.fromString(networkId);
	    	
		try (NetworkDAO dao = new NetworkDAO()) {
			if ( !dao.isReadable(networkUUID, getLoggedInUserId())) {
				throw new UnauthorizedOperationException("User doesn't have access to this network.");
			}
					
			EdgeFilter filter = new EdgeFilter(networkId, queryParameter, limit, order);
			Set<CxEdge> result = filter.filterTopN();
			if (format.equals("cx2"))
				return Response.ok().type(MediaType.APPLICATION_JSON_TYPE).entity(result).build();
			else if ( format.equals("cx")) {
				String aspectFile = Configuration.getInstance().getNdexRoot() + File.separator + "data" +File.separator + networkId
								+ File.separator + CX2NetworkLoader.cx2AspectDirName+  File.separator + CxAttributeDeclaration.ASPECT_NAME;
				File vsFile = new File( aspectFile );
				ObjectMapper om = new ObjectMapper();
				CxAttributeDeclaration[] ds = om.readValue(vsFile, CxAttributeDeclaration[].class); 
						
				Map<String, DeclarationEntry> edgeAttrDecls= 
						ds[0].getDeclarations().get(CxEdge.ASPECT_NAME);
				return Response.ok().type(MediaType.APPLICATION_JSON_TYPE).entity(
								cvtToCXlist(result, edgeAttrDecls)).build();

			} else 
				throw new NdexException ("Invalid value for parameter 'format'.");
			} 
	}
		
	private static List<Map<String,Object>> cvtToCXlist(Set<CxEdge> cx2Set, Map<String, DeclarationEntry> edgeAttrDecls) {
		List<Map<String,Object>> result = new ArrayList<>(cx2Set.size());
			
		for ( CxEdge e : cx2Set) {
			HashMap<String, Object> m = new HashMap<>(2);
			m.put("e", e.getCX1Edge(edgeAttrDecls));
			List<EdgeAttributesElement> attrs = e.getAttributesAsCX1List(edgeAttrDecls);
			if ( !attrs.isEmpty())
				m.put("a", attrs);
			result.add(m);
		}
		return result;
	}


}
