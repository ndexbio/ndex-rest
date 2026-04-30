package org.ndexbio.rest.mcp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.Response;

import org.easymock.EasyMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ndexbio.model.object.User;
import org.ndexbio.rest.services.v3.NetworkServiceV3;

import static org.easymock.EasyMock.*;
import static org.junit.jupiter.api.Assertions.*;

class TestDownloadPreSignedServlet {

    private HttpServletRequest  request;
    private HttpServletResponse response;

    @BeforeEach
    void setUp() {
        request  = EasyMock.createMock(HttpServletRequest.class);
        response = EasyMock.createMock(HttpServletResponse.class);
        DownloadTokenService.getInstance().clearForTest();
    }

    @AfterEach
    void tearDown() {
        DownloadTokenService.getInstance().clearForTest();
    }

    private ServletOutputStream outputStreamFor(ByteArrayOutputStream bos) {
        return new ServletOutputStream() {
            @Override public void write(int b) throws IOException { bos.write(b); }
            @Override public boolean isReady() { return true; }
            @Override public void setWriteListener(WriteListener wl) {}
        };
    }

    // ── 401 paths ─────────────────────────────────────────────────────────────

    @Test
    void doGet_returns401_whenTokenParamMissing() throws Exception {
        expect(request.getParameter("download_token")).andReturn(null).once();
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing download token");
        expectLastCall().once();
        replay(request, response);

        new DownloadPreSignedServlet().doGet(request, response);
        verify(request, response);
    }

    @Test
    void doGet_returns401_whenTokenInvalid() throws Exception {
        expect(request.getParameter("download_token")).andReturn("bogus-token").once();
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired download token");
        expectLastCall().once();
        replay(request, response);

        new DownloadPreSignedServlet().doGet(request, response);
        verify(request, response);
    }

    // ── happy path ────────────────────────────────────────────────────────────

    @Test
    void doGet_callsGetCX2NetworkAndStreamsResponse() throws Exception {
        User user = EasyMock.createMock(User.class);
        String networkId = "f93f402c-86d4-11e7-a10d-0ac135e8bacf";
        String accessKey = "key-abc";
        DownloadFileRequest downloadReq = new DownloadFileRequest(user, networkId, accessKey,
                                                                  System.currentTimeMillis());
        String token = DownloadTokenService.getInstance().createToken(downloadReq);

        byte[] cx2Bytes = "[]".getBytes();
        Response jaxrsResponse = Response.ok(new ByteArrayInputStream(cx2Bytes)).build();

        NetworkServiceV3 mockSvc = EasyMock.createMock(NetworkServiceV3.class);
        expect(mockSvc.getCX2Network(eq(networkId), eq(false), eq(accessKey), isNull(), isNull()))
            .andReturn(jaxrsResponse).once();

        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        expect(request.getParameter("download_token")).andReturn(token).once();
        request.setAttribute("User", user);
        expectLastCall().once();
        response.setContentType("application/json");
        expectLastCall().once();
        expect(response.getOutputStream()).andReturn(outputStreamFor(bos)).once();
        replay(request, response, mockSvc);

        DownloadPreSignedServlet servlet = new DownloadPreSignedServlet() {
            @Override
            protected NetworkServiceV3 createNetworkService(HttpServletRequest req) {
                return mockSvc;
            }
        };
        servlet.doGet(request, response);
        verify(request, response, mockSvc);
        assertArrayEquals(cx2Bytes, bos.toByteArray());
    }

    @Test
    void doGet_setsUserOnRequest() throws Exception {
        User user = EasyMock.createMock(User.class);
        DownloadFileRequest downloadReq = new DownloadFileRequest(user, "net-id", null,
                                                                  System.currentTimeMillis());
        String token = DownloadTokenService.getInstance().createToken(downloadReq);

        Response jaxrsResponse = Response.ok(new ByteArrayInputStream(new byte[0])).build();

        NetworkServiceV3 mockSvc = EasyMock.createMock(NetworkServiceV3.class);
        expect(mockSvc.getCX2Network(anyString(), eq(false), isNull(), isNull(), isNull()))
            .andReturn(jaxrsResponse).once();

        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        expect(request.getParameter("download_token")).andReturn(token).once();
        request.setAttribute(eq("User"), same(user));
        expectLastCall().once();
        response.setContentType("application/json");
        expectLastCall().once();
        expect(response.getOutputStream()).andReturn(outputStreamFor(bos)).once();
        replay(request, response, mockSvc);

        DownloadPreSignedServlet servlet = new DownloadPreSignedServlet() {
            @Override
            protected NetworkServiceV3 createNetworkService(HttpServletRequest req) {
                return mockSvc;
            }
        };
        servlet.doGet(request, response);
        verify(request, response, mockSvc);
    }

    @Test
    void doGet_setsJsonContentType() throws Exception {
        User user = EasyMock.createMock(User.class);
        DownloadFileRequest downloadReq = new DownloadFileRequest(user, "net-id", null,
                                                                  System.currentTimeMillis());
        String token = DownloadTokenService.getInstance().createToken(downloadReq);

        Response jaxrsResponse = Response.ok(new ByteArrayInputStream(new byte[0])).build();

        NetworkServiceV3 mockSvc = EasyMock.createMock(NetworkServiceV3.class);
        expect(mockSvc.getCX2Network(anyString(), eq(false), isNull(), isNull(), isNull()))
            .andReturn(jaxrsResponse).once();

        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        expect(request.getParameter("download_token")).andReturn(token).once();
        request.setAttribute(eq("User"), same(user));
        expectLastCall().once();
        // This is the assertion: setContentType must be called with "application/json"
        response.setContentType("application/json");
        expectLastCall().once();
        expect(response.getOutputStream()).andReturn(outputStreamFor(bos)).once();
        replay(request, response, mockSvc);

        DownloadPreSignedServlet servlet = new DownloadPreSignedServlet() {
            @Override
            protected NetworkServiceV3 createNetworkService(HttpServletRequest req) {
                return mockSvc;
            }
        };
        servlet.doGet(request, response);
        verify(request, response, mockSvc);
    }
}
