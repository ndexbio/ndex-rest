package org.ndexbio.rest.mcp;

import java.io.IOException;
import java.io.InputStream;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.rest.services.v3.NetworkServiceV3;

public class DownloadPreSignedServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String token = request.getParameter("download_token");
        if (token == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing download token");
            return;
        }
        DownloadFileRequest downloadReq = DownloadTokenService.getInstance().resolveToken(token);
        if (downloadReq == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired download token");
            return;
        }
        request.setAttribute("User", downloadReq.user());
        try {
            NetworkServiceV3 svc = createNetworkService(request);
            jakarta.ws.rs.core.Response jaxrs =
                svc.getCX2Network(downloadReq.networkId(), false, downloadReq.accessKey(), null, null);
            response.setContentType("application/json");
            try (InputStream in = (InputStream) jaxrs.getEntity()) {
                in.transferTo(response.getOutputStream());
            }
        } catch (UnauthorizedOperationException | SecurityException e) {
            if (!response.isCommitted()) response.sendError(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
        } catch (Exception e) {
            if (!response.isCommitted()) response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    protected NetworkServiceV3 createNetworkService(HttpServletRequest request) {
        return new NetworkServiceV3(request);
    }
}
