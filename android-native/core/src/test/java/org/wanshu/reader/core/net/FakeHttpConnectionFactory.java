package org.wanshu.reader.core.net;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FakeHttpConnectionFactory implements HttpConnectionFactory {

    private final Map<String, List<FakeHttpConnection>> routes = new HashMap<String, List<FakeHttpConnection>>();
    private final Map<String, IOException> errorRoutes = new HashMap<String, IOException>();
    private final List<String> requestedUrls = new ArrayList<String>();
    private final List<FakeHttpConnection> openedConnections = new ArrayList<FakeHttpConnection>();

    public void addResponse(String url, int code, byte[] data, Map<String, String> headers) {
        addResponse("GET", url, code, data, headers);
    }

    /** Registers a route that answers only the given method (other methods get 405). */
    public void addResponse(String method, String url, int code, byte[] data, Map<String, String> headers) {
        if (!routes.containsKey(url)) {
            routes.put(url, new ArrayList<FakeHttpConnection>());
        }
        try {
            routes.get(url).add(new FakeHttpConnection(new URL(url), method, code, data, headers));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void addError(String url, IOException error) {
        errorRoutes.put(url, error);
    }

    public List<String> getRequestedUrls() {
        return requestedUrls;
    }

    /** Every connection handed out, in order (also the default-404 ones). */
    public List<FakeHttpConnection> getOpenedConnections() {
        return openedConnections;
    }

    @Override
    public HttpURLConnection openConnection(URL url) throws IOException {
        String urlStr = url.toString();
        requestedUrls.add(urlStr);
        if (errorRoutes.containsKey(urlStr)) {
            throw errorRoutes.get(urlStr);
        }
        List<FakeHttpConnection> list = routes.get(urlStr);
        FakeHttpConnection conn;
        if (list != null && !list.isEmpty()) {
            conn = list.remove(0);
        } else {
            // Default 404
            conn = new FakeHttpConnection(url, 404, "Not Found".getBytes(), null);
        }
        openedConnections.add(conn);
        return conn;
    }
}
