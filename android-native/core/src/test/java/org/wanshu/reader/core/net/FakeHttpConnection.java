package org.wanshu.reader.core.net;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class FakeHttpConnection extends HttpURLConnection {

    private final String expectedMethod;
    private final int responseCode;
    private final byte[] responseData;
    private final Map<String, String> headers;
    private final ByteArrayOutputStream writtenBody = new ByteArrayOutputStream();
    private boolean disconnected = false;

    public FakeHttpConnection(URL url, int responseCode, byte[] responseData, Map<String, String> headers) {
        this(url, "GET", responseCode, responseData, headers);
    }

    /** A route that only answers the given HTTP method; any other method gets 405. */
    public FakeHttpConnection(URL url, String expectedMethod, int responseCode, byte[] responseData, Map<String, String> headers) {
        super(url);
        this.expectedMethod = expectedMethod;
        this.responseCode = responseCode;
        this.responseData = responseData != null ? responseData : new byte[0];
        this.headers = headers != null ? headers : new HashMap<String, String>();
    }

    private boolean methodMatches() {
        return expectedMethod.equals(getRequestMethod());
    }

    @Override
    public int getResponseCode() {
        return methodMatches() ? responseCode : 405;
    }

    @Override
    public String getHeaderField(String name) {
        if (name == null) return null;
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (name.equalsIgnoreCase(e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        if (getResponseCode() >= 400) {
            throw new IOException("HTTP error " + getResponseCode());
        }
        return new ByteArrayInputStream(responseData);
    }

    @Override
    public InputStream getErrorStream() {
        if (!methodMatches()) {
            return new ByteArrayInputStream(new byte[0]);
        }
        if (responseCode >= 400) {
            return new ByteArrayInputStream(responseData);
        }
        return null;
    }

    @Override
    public OutputStream getOutputStream() {
        return writtenBody;
    }

    @Override
    public void disconnect() {
        disconnected = true;
    }

    @Override
    public boolean usingProxy() {
        return false;
    }

    @Override
    public void connect() throws IOException {
        // No-op
    }

    public boolean isDisconnected() {
        return disconnected;
    }

    /** Bytes the client wrote as the request body. */
    public byte[] getWrittenBody() {
        return writtenBody.toByteArray();
    }

    /** The HTTP method the client set. */
    public String getRecordedMethod() {
        return getRequestMethod();
    }

    public String getRequestHeader(String name) {
        return getRequestProperty(name);
    }
}
