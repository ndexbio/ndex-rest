package org.ndexbio.rest.services;

import java.util.List;
import java.util.Map;

import javax.annotation.security.PermitAll;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;

import org.ndexbio.common.exceptions.NdexException;
import org.ndexbio.common.models.object.NdexStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;
@Path("/admin")
public class AdminService extends NdexService {
	private static final Logger logger = LoggerFactory.getLogger(AdminService.class);

	public AdminService(@Context HttpServletRequest httpRequest)
    {
        super(httpRequest);
	}

	/**************************************************************************
	 * 
	 * Gets status for the service.
	 **************************************************************************/

	@GET
	@PermitAll
	@Path("/status")
	@Produces("application/json")
	public NdexStatus getStatus() throws NdexException

	{

		try {

			setupDatabase();
			NdexStatus status = new NdexStatus();
		/*	status.setNetworkCount(this.getClassCount("network"));
			status.setUserCount(this.getClassCount("user"));
			status.setGroupCount(this.getClassCount("group")); */
		    
			status.setNetworkCount(1);
			status.setUserCount(1);
			status.setGroupCount(0);
			Map<String,String> props = status.getProperties();
			props.put("ServerResultLimit", "10000");
			status.setProperties(props);
			return status;
		} finally {

			teardownDatabase();

		}

	}

/*	private Integer getClassCount(String className) {

		final List<ODocument> classCountResult = _ndexDatabase
				.query(new OSQLSynchQuery<ODocument>(
						"SELECT COUNT(*) as count FROM " + className));

		final Long count = classCountResult.get(0).field("count");

		Integer classCount = count != null ? count.intValue() : null;

		return classCount;

	} */

}
