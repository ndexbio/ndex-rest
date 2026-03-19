package org.ndexbio.rest.mcp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;

import org.easymock.EasyMock;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestMcpManifestServlet {

    // --- Helper: in-memory ServletOutputStream for response body capture ---

    private static class CapturingOutputStream extends ServletOutputStream {
        final ByteArrayOutputStream buf = new ByteArrayOutputStream();

        @Override public boolean isReady() { return true; }
        @Override public void setWriteListener(WriteListener l) {}
        @Override public void write(int b) { buf.write(b); }
        @Override public void write(byte[] b, int off, int len) { buf.write(b, off, len); }

        String text() { return buf.toString(StandardCharsets.UTF_8); }
    }

    // --- Helper: servlet subclasses for error-path injection ---

    private static McpManifestServlet servletWithNullStream() {
        return new McpManifestServlet() {
            @Override InputStream openManifestStream() { return null; }
        };
    }

    private static McpManifestServlet servletWithBrokenStream() {
        return new McpManifestServlet() {
            @Override InputStream openManifestStream() {
                return new InputStream() {
                    @Override public int read() throws IOException {
                        throw new IOException("simulated disk error");
                    }
                    @Override public byte[] readAllBytes() throws IOException {
                        throw new IOException("simulated disk error");
                    }
                };
            }
        };
    }

    // --- Success path (McpManifest.md present in src/test/resources) ---

    @Test
    void doGet_setsContentType_textMarkdown() throws Exception {
        CapturingOutputStream cos = new CapturingOutputStream();
        HttpServletResponse resp = EasyMock.mock(HttpServletResponse.class);
        resp.setContentType("text/markdown;charset=UTF-8");
        EasyMock.expectLastCall();
        EasyMock.expect(resp.getOutputStream()).andReturn(cos);
        EasyMock.replay(resp);

        new McpManifestServlet().doGet(null, resp);

        EasyMock.verify(resp);
    }

    @Test
    void doGet_writesManifestContent_whenResourceFound() throws Exception {
        CapturingOutputStream cos = new CapturingOutputStream();
        HttpServletResponse resp = EasyMock.mock(HttpServletResponse.class);
        resp.setContentType("text/markdown;charset=UTF-8");
        EasyMock.expectLastCall();
        EasyMock.expect(resp.getOutputStream()).andReturn(cos);
        EasyMock.replay(resp);

        new McpManifestServlet().doGet(null, resp);

        assertFalse(cos.text().isEmpty(), "response body must not be empty");
        assertTrue(cos.text().contains("NDEx MCP Server"), "body must contain manifest header");
        EasyMock.verify(resp);
    }

    @Test
    void doGet_doesNotSetExplicitStatus_onSuccess() throws Exception {
        // EasyMock strict mode: any unexpected call (e.g. setStatus) fails the test,
        // so passing here proves setStatus was not called on the success path.
        CapturingOutputStream cos = new CapturingOutputStream();
        HttpServletResponse resp = EasyMock.mock(HttpServletResponse.class);
        resp.setContentType("text/markdown;charset=UTF-8");
        EasyMock.expectLastCall();
        EasyMock.expect(resp.getOutputStream()).andReturn(cos);
        EasyMock.replay(resp);

        assertDoesNotThrow(() -> new McpManifestServlet().doGet(null, resp));
        EasyMock.verify(resp);
    }

    // --- 404 path (null stream) ---

    @Test
    void doGet_setsStatus404_whenResourceNotOnClasspath() throws Exception {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(body, true, StandardCharsets.UTF_8);
        HttpServletResponse resp = EasyMock.mock(HttpServletResponse.class);
        resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
        EasyMock.expectLastCall();
        resp.setContentType("text/plain;charset=UTF-8");
        EasyMock.expectLastCall();
        EasyMock.expect(resp.getWriter()).andReturn(writer);
        EasyMock.replay(resp);

        servletWithNullStream().doGet(null, resp);

        EasyMock.verify(resp);
    }

    @Test
    void doGet_writesNotFoundMessage_whenResourceNotOnClasspath() throws Exception {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(body, true, StandardCharsets.UTF_8);
        HttpServletResponse resp = EasyMock.mock(HttpServletResponse.class);
        resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
        EasyMock.expectLastCall();
        resp.setContentType("text/plain;charset=UTF-8");
        EasyMock.expectLastCall();
        EasyMock.expect(resp.getWriter()).andReturn(writer);
        EasyMock.replay(resp);

        servletWithNullStream().doGet(null, resp);

        writer.flush();
        String responseBody = body.toString(StandardCharsets.UTF_8);
        assertTrue(responseBody.contains("not found"), "body must mention 'not found'");
        EasyMock.verify(resp);
    }

    // --- 500 path (IOException from stream) ---

    // Helper: builds 500-path mock — setContentType + getOutputStream called before readAllBytes()
    // throws, then setStatus(500) + getWriter() called in catch block.
    private HttpServletResponse mock500Response(PrintWriter writer) throws Exception {
        CapturingOutputStream cos = new CapturingOutputStream();
        HttpServletResponse resp = EasyMock.mock(HttpServletResponse.class);
        resp.setContentType("text/markdown;charset=UTF-8");
        EasyMock.expectLastCall();
        EasyMock.expect(resp.getOutputStream()).andReturn(cos);
        resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        EasyMock.expectLastCall();
        EasyMock.expect(resp.getWriter()).andReturn(writer);
        EasyMock.replay(resp);
        return resp;
    }

    @Test
    void doGet_setsStatus500_whenIOExceptionThrown() throws Exception {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(body, true, StandardCharsets.UTF_8);
        HttpServletResponse resp = mock500Response(writer);

        servletWithBrokenStream().doGet(null, resp);

        EasyMock.verify(resp);
    }

    @Test
    void doGet_doesNotThrow_whenIOExceptionThrown() throws Exception {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(body, true, StandardCharsets.UTF_8);
        HttpServletResponse resp = mock500Response(writer);

        assertDoesNotThrow(() -> servletWithBrokenStream().doGet(null, resp));
        EasyMock.verify(resp);
    }

    @Test
    void doGet_writesErrorMessage_whenIOExceptionThrown() throws Exception {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(body, true, StandardCharsets.UTF_8);
        HttpServletResponse resp = mock500Response(writer);

        servletWithBrokenStream().doGet(null, resp);

        writer.flush();
        String responseBody = body.toString(StandardCharsets.UTF_8);
        assertTrue(responseBody.contains("simulated disk error"),
                "error body must include the IOException message");
        EasyMock.verify(resp);
    }
}
