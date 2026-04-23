package org.ndexbio.rest.mcp;

import java.io.IOException;
import java.io.InputStream;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import org.ndexbio.model.object.NdexObjectUpdateStatus;
import org.ndexbio.rest.services.v3.NetworkServiceV3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Accepts the CX2 multipart POST that follows a successful request_network_upload tool call.
 * Validates the single-use upload token from the query parameter, then delegates directly
 * to NetworkServiceV3 create or update. Registered at /mcp/upload with multipart config
 * set programmatically in McpServletContextListener (not via @MultipartConfig, which is
 * ignored for programmatically registered servlets in Tomcat).
 */
public class UploadPreSignedServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(UploadPreSignedServlet.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .addMixIn(NdexObjectUpdateStatus.class, NdexObjectUpdateStatusMixIn.class);

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String token = request.getParameter("upload_token");
        if (token == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing upload token");
            return;
        }
        UploadFileRequest uploadReq = UploadService.getInstance().resolveToken(token);
        if (uploadReq == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired upload token");
            return;
        }
        request.setAttribute("User", uploadReq.user());
        Part part = request.getPart("CXNetworkStream");
        if (part == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing CXNetworkStream part");
            return;
        }
        try (InputStream cx2Stream = part.getInputStream()) {
            NetworkServiceV3 svc = createNetworkService(request);
            NdexObjectUpdateStatus result;
            if (uploadReq.networkId() == null) {
                result = svc.createNetworkFromInputStream(cx2Stream, uploadReq.visibility(),
                                                          uploadReq.extraNodeIndex(), uploadReq.folderId());
            } else {
                result = svc.updateNetworkFromInputStream(uploadReq.networkId(), cx2Stream, null, null);
            }
            response.setContentType("application/json");
            MAPPER.writeValue(response.getWriter(), result);
        } catch (Exception e) {
            logger.error("UploadPreSignedServlet: upload failed", e);
            if (!response.isCommitted()) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
            }
        }
    }

    protected NetworkServiceV3 createNetworkService(HttpServletRequest request) {
        return new NetworkServiceV3(request);
    }
}
