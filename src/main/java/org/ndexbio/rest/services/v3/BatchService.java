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
package org.ndexbio.rest.services.v3;

import java.io.IOException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.ndexbio.common.importexport.ImporterExporterEntry;
import org.ndexbio.common.models.dao.FolderDAO;
import org.ndexbio.common.models.dao.ShortcutDAO;
import org.ndexbio.common.models.dao.postgresql.GroupDAO;
import org.ndexbio.common.models.dao.postgresql.PostgresNetworkDAO;
import org.ndexbio.common.models.dao.postgresql.TaskDAO;
import org.ndexbio.common.models.dao.postgresql.UserDAO;
import org.ndexbio.model.exceptions.BadRequestException;
import org.ndexbio.model.exceptions.ForbiddenOperationException;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.object.FileType;
import org.ndexbio.model.object.Group;
import org.ndexbio.model.object.MoveNetworksRequest;
import org.ndexbio.model.object.NetworkExportRequestV2;
import org.ndexbio.model.object.FileVisibilityRequest;
import org.ndexbio.model.object.Status;
import org.ndexbio.model.object.Task;
import org.ndexbio.model.object.TaskType;
import org.ndexbio.model.object.User;
import org.ndexbio.model.object.network.NetworkSummary;
import org.ndexbio.model.object.network.NetworkSummaryFormat;
import org.ndexbio.model.object.network.NetworkSummaryV3;
import org.ndexbio.rest.Configuration;
import org.ndexbio.rest.filters.BasicAuthenticationFilter;
import org.ndexbio.rest.services.NdexService;
import org.ndexbio.task.NdexServerQueue;
import org.ndexbio.task.NetworkExportTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.oas.annotations.Operation;

@Path("/v3/batch")
public class BatchService extends NdexService {
	
	
//	static Logger logger = LoggerFactory.getLogger(BatchServiceV2.class);
	static Logger accLogger = LoggerFactory.getLogger(BasicAuthenticationFilter.accessLoggerName);

	/**************************************************************************
	 * Injects the HTTP request into the base class to be used by
	 * getLoggedInUser().
	 * 
	 * @param httpRequest
	 *            The HTTP request injected by RESTEasy's context.
	 **************************************************************************/
	public BatchService(@Context HttpServletRequest httpRequest) {
		super(httpRequest);
	}
	
	
	@PermitAll
	@POST
	@Path("/networks/summary")
	@Operation(summary = "Get Network Summaries By UUIDs (V3)", description = "Returns a JSON array of NetworkSummaryV3 objects selected by the POSTed JSON array of Network UUIDs. Supports different summary formats and access keys for private networks.")
	@Produces("application/json")
	public List<NetworkSummaryV3> getNetworkSummaries(
			@QueryParam("accesskey") String accessKey,
			List<String> networkIdStrs,
			@DefaultValue("FULL") @QueryParam("format") String format)
			throws IllegalArgumentException, NdexException, SQLException, JsonParseException, JsonMappingException, IOException {

		try {
			NetworkSummaryFormat fmt = NetworkSummaryFormat.valueOf(format.toUpperCase());

			if (networkIdStrs == null)
				throw new ForbiddenOperationException("A network UUID list is required.");

			if (networkIdStrs.size() > 2000)
				throw new NdexException("You can only send up to 2000 network ids in this function.");

			accLogger.info("[data]\t[uuidcounts:" + networkIdStrs.size() + "]");

			try (PostgresNetworkDAO dao = new PostgresNetworkDAO()) {
				UUID userId = getLoggedInUserId();
				return dao.getNetworkV3SummariesByIdStrList(networkIdStrs, userId, accessKey, fmt);
			}
		} catch (IllegalArgumentException e) {
			throw new BadRequestException("Format " + format + " is unsupported. Error message: " + e.getMessage());
		}
	}	
	
	@POST
	@Path("/networks/move")
	@Consumes(MediaType.APPLICATION_JSON)
	@Operation(
	    summary = "Move networks to a folder",
	    description = "Moves a list of networks to the specified target folder. User must be the owner of the networks."
	)
	public void moveNetworksToFolder(final MoveNetworksRequest request) throws Exception {
	    if (request == null || request.getNetworks() == null || request.getNetworks().isEmpty()) {
	        throw new BadRequestException("Request must contain a target folder UUID and a non-empty list of network UUIDs.");
	    }

	    UUID userId = getLoggedInUserId();
	    if (userId == null) {
	        throw new NdexException("User must be logged in to move networks.");
	    }

	    try (PostgresNetworkDAO networkDao = new PostgresNetworkDAO()) {
	        for (UUID netId : request.getNetworks()) {
	        	if (!networkDao.isAdmin(netId, userId)) {
	                throw new NdexException("User does not own network " + netId);
	            }

	            networkDao.setNetworkFolder(netId, request.getTargetFolder());
	        }
	        networkDao.commit();
	    }

	    return ;
	}
	
    @POST
    @Path("/files/setvisibility")
    @Operation(summary = "Set File Visibility", description = "Set the visibility (PUBLIC or PRIVATE) for a batch of files (networks, folders, etc.). User must be the owner of the files.")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public void setVisibility(FileVisibilityRequest request) throws Exception {
        if (request == null || request.getVisibility() == null || request.getFiles() == null) {
            throw new NdexException("Missing required parameters: visibility and items.");
        }

        UUID userId = getLoggedInUserId();
        if (userId == null) {
            throw new NdexException("User is not logged in.");
        }

        for (Map.Entry<UUID, FileType> item : request.getFiles().entrySet()) {
            UUID uuid = item.getKey();
            FileType type = item.getValue();

            switch (type) {
                case NETWORK:
                    try (PostgresNetworkDAO dao = new PostgresNetworkDAO()) {
                        if (!dao.isAdmin(uuid, userId)) {
                            throw new NdexException("Not the owner of network " + uuid);
                        }
                        dao.updateNetworkVisibility(uuid, request.getVisibility(), true);
                        dao.commit();
                    }
                    break;
                case FOLDER:
                    try (FolderDAO dao = Configuration.getInstance().getDAOFactory().getFolderDAO()) {
                        if (!dao.isFolderOwner(uuid, userId)) {
                            throw new NdexException("Not the owner of folder " + uuid);
                        }
                        dao.setFolderVisibility(uuid, request.getVisibility());
                        dao.commit();
                    }
                    break;
                case SHORTCUT:
                    try (ShortcutDAO dao = Configuration.getInstance().getDAOFactory().getShortcutDAO()) {
                        if (!dao.isShortcutOwner(uuid, userId)) {
                            throw new NdexException("Not the owner of shortcut " + uuid);
                        }
                        dao.setShortcutVisibility(uuid, request.getVisibility());
                        dao.commit();
                    }
                    break;
                default:
                    throw new NdexException("Unsupported file type: " + type);
            }
        }

        return ;
    }

	

}
