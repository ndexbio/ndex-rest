package org.ndexbio.rest.mcp;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import org.easymock.EasyMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ndexbio.model.object.NdexObjectUpdateStatus;
import org.ndexbio.model.object.User;
import org.ndexbio.rest.services.v3.NetworkServiceV3;

import static org.easymock.EasyMock.*;
import static org.junit.jupiter.api.Assertions.*;

class TestUploadPreSignedServlet {

    private HttpServletRequest  request;
    private HttpServletResponse response;

    @BeforeEach
    void setUp() {
        request  = EasyMock.createMock(HttpServletRequest.class);
        response = EasyMock.createMock(HttpServletResponse.class);
        UploadService.getInstance().clearForTest();
    }

    @AfterEach
    void tearDown() {
        UploadService.getInstance().clearForTest();
    }

    // ── 401 paths ─────────────────────────────────────────────────────────────

    @Test
    void doPost_returns401_whenTokenParamMissing() throws Exception {
        expect(request.getParameter("upload_token")).andReturn(null).once();
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing upload token");
        expectLastCall().once();
        replay(request, response);

        new UploadPreSignedServlet().doPost(request, response);
        verify(request, response);
    }

    @Test
    void doPost_returns401_whenTokenInvalid() throws Exception {
        expect(request.getParameter("upload_token")).andReturn("bogus-token").once();
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired upload token");
        expectLastCall().once();
        replay(request, response);

        new UploadPreSignedServlet().doPost(request, response);
        verify(request, response);
    }

    // ── 400 path ──────────────────────────────────────────────────────────────

    @Test
    void doPost_returns400_whenCXStreamPartMissing() throws Exception {
        User user = EasyMock.createMock(User.class);
        UploadFileRequest uploadReq = new UploadFileRequest(user, null, null, null, null,
                                                            System.currentTimeMillis());
        String token = UploadService.getInstance().createToken(uploadReq);

        expect(request.getParameter("upload_token")).andReturn(token).once();
        request.setAttribute("User", user);
        expectLastCall().once();
        expect(request.getPart("CXNetworkStream")).andReturn(null).once();
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing CXNetworkStream part");
        expectLastCall().once();
        replay(request, response);

        new UploadPreSignedServlet().doPost(request, response);
        verify(request, response);
    }

    // ── create path ───────────────────────────────────────────────────────────

    @Test
    void doPost_callsCreate_whenNetworkIdIsNull() throws Exception {
        User user = EasyMock.createMock(User.class);
        UploadFileRequest uploadReq = new UploadFileRequest(user, null, "PUBLIC", "geneSymbol",
                                                            "folder-123", System.currentTimeMillis());
        String token = UploadService.getInstance().createToken(uploadReq);

        Part part = EasyMock.createMock(Part.class);
        expect(part.getInputStream()).andReturn(new ByteArrayInputStream(new byte[0])).once();

        NdexObjectUpdateStatus status = EasyMock.createNiceMock(NdexObjectUpdateStatus.class);
        NetworkServiceV3 mockSvc = EasyMock.createMock(NetworkServiceV3.class);
        expect(mockSvc.createNetworkFromInputStream(
                anyObject(), eq("PUBLIC"), eq("geneSymbol"), eq("folder-123")))
            .andReturn(status).once();

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        expect(request.getParameter("upload_token")).andReturn(token).once();
        request.setAttribute("User", user);
        expectLastCall().once();
        expect(request.getPart("CXNetworkStream")).andReturn(part).once();
        response.setContentType("application/json");
        expectLastCall().once();
        expect(response.getWriter()).andReturn(pw).once();
        replay(request, response, part, mockSvc, status);

        UploadPreSignedServlet servlet = new UploadPreSignedServlet() {
            @Override
            protected NetworkServiceV3 createNetworkService(HttpServletRequest req) {
                return mockSvc;
            }
        };
        servlet.doPost(request, response);
        verify(request, response, part, mockSvc, status);
    }

    @Test
    void doPost_callsUpdate_whenNetworkIdIsPresent() throws Exception {
        User user = EasyMock.createMock(User.class);
        String networkId = "f93f402c-86d4-11e7-a10d-0ac135e8bacf";
        UploadFileRequest uploadReq = new UploadFileRequest(user, networkId, null, null, null,
                                                            System.currentTimeMillis());
        String token = UploadService.getInstance().createToken(uploadReq);

        Part part = EasyMock.createMock(Part.class);
        expect(part.getInputStream()).andReturn(new ByteArrayInputStream(new byte[0])).once();

        NdexObjectUpdateStatus status = EasyMock.createNiceMock(NdexObjectUpdateStatus.class);
        NetworkServiceV3 mockSvc = EasyMock.createMock(NetworkServiceV3.class);
        expect(mockSvc.updateNetworkFromInputStream(eq(networkId), anyObject(), isNull(), isNull()))
            .andReturn(status).once();

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        expect(request.getParameter("upload_token")).andReturn(token).once();
        request.setAttribute("User", user);
        expectLastCall().once();
        expect(request.getPart("CXNetworkStream")).andReturn(part).once();
        response.setContentType("application/json");
        expectLastCall().once();
        expect(response.getWriter()).andReturn(pw).once();
        replay(request, response, part, mockSvc, status);

        UploadPreSignedServlet servlet = new UploadPreSignedServlet() {
            @Override
            protected NetworkServiceV3 createNetworkService(HttpServletRequest req) {
                return mockSvc;
            }
        };
        servlet.doPost(request, response);
        verify(request, response, part, mockSvc, status);
    }

    // ── attribute / content-type verification ─────────────────────────────────

    @Test
    void doPost_setsUserOnRequest() throws Exception {
        User user = EasyMock.createMock(User.class);
        UploadFileRequest uploadReq = new UploadFileRequest(user, null, null, null, null,
                                                            System.currentTimeMillis());
        String token = UploadService.getInstance().createToken(uploadReq);

        Part part = EasyMock.createMock(Part.class);
        expect(part.getInputStream()).andReturn(new ByteArrayInputStream(new byte[0])).once();

        NdexObjectUpdateStatus status = EasyMock.createNiceMock(NdexObjectUpdateStatus.class);
        NetworkServiceV3 mockSvc = EasyMock.createMock(NetworkServiceV3.class);
        expect(mockSvc.createNetworkFromInputStream(anyObject(), isNull(), isNull(), isNull()))
            .andReturn(status).once();

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        expect(request.getParameter("upload_token")).andReturn(token).once();
        // Capture the setAttribute call to verify user is set
        request.setAttribute(eq("User"), same(user));
        expectLastCall().once();
        expect(request.getPart("CXNetworkStream")).andReturn(part).once();
        response.setContentType("application/json");
        expectLastCall().once();
        expect(response.getWriter()).andReturn(pw).once();
        replay(request, response, part, mockSvc, status);

        UploadPreSignedServlet servlet = new UploadPreSignedServlet() {
            @Override
            protected NetworkServiceV3 createNetworkService(HttpServletRequest req) {
                return mockSvc;
            }
        };
        servlet.doPost(request, response);
        verify(request, response, part, mockSvc, status);
    }

    @Test
    void doPost_setsJsonContentType() throws Exception {
        User user = EasyMock.createMock(User.class);
        UploadFileRequest uploadReq = new UploadFileRequest(user, null, null, null, null,
                                                            System.currentTimeMillis());
        String token = UploadService.getInstance().createToken(uploadReq);

        Part part = EasyMock.createMock(Part.class);
        expect(part.getInputStream()).andReturn(new ByteArrayInputStream(new byte[0])).once();

        NdexObjectUpdateStatus status = EasyMock.createNiceMock(NdexObjectUpdateStatus.class);
        NetworkServiceV3 mockSvc = EasyMock.createMock(NetworkServiceV3.class);
        expect(mockSvc.createNetworkFromInputStream(anyObject(), isNull(), isNull(), isNull()))
            .andReturn(status).once();

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        expect(request.getParameter("upload_token")).andReturn(token).once();
        request.setAttribute(eq("User"), same(user));
        expectLastCall().once();
        expect(request.getPart("CXNetworkStream")).andReturn(part).once();
        // This is the assertion: response.setContentType must be called with "application/json"
        response.setContentType("application/json");
        expectLastCall().once();
        expect(response.getWriter()).andReturn(pw).once();
        replay(request, response, part, mockSvc, status);

        UploadPreSignedServlet servlet = new UploadPreSignedServlet() {
            @Override
            protected NetworkServiceV3 createNetworkService(HttpServletRequest req) {
                return mockSvc;
            }
        };
        servlet.doPost(request, response);
        verify(request, response, part, mockSvc, status);
    }
}
