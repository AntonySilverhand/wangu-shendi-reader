package org.wanshu.reader.core.net;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class FakeHttpConnection extends HttpURLConnection {

    private final int responseCode;
    private final byte[] responseData;
    private final Map<String, String> headers;
    private boolean disconnected = false;

    public FakeHttpConnection(URL url, int responseCode, byte[] responseData, Map<String, String> headers) {
        super(url);
        this.responseCode = responseCode;
        this.responseData = responseData != null ? responseData : new byte[0];
        this.headers = headers != null ? headers : new HashMap<String, String>();
    }

    @Override
    public int getResponseCode() {
        return responseCode;
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
        if (responseCode >= 400) {
            throw new IOException("HTTP error " + responseCode);
        }
        return new ByteArrayInputStream(responseData);
    }

    @Override
    public InputStream getErrorStream() {
        if (responseCode >= 400) {
            return new ByteArrayInputStream(responseData);
        }
        return null;
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
}
