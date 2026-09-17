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

    public void addResponse(String url, int code, byte[] data, Map<String, String> headers) {
        if (!routes.containsKey(url)) {
            routes.put(url, new ArrayList<FakeHttpConnection>());
        }
        try {
            routes.get(url).add(new FakeHttpConnection(new URL(url), code, data, headers));
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

    @Override
    public HttpURLConnection openConnection(URL url) throws IOException {
        String urlStr = url.toString();
        requestedUrls.add(urlStr);
        if (errorRoutes.containsKey(urlStr)) {
            throw errorRoutes.get(urlStr);
        }
        List<FakeHttpConnection> list = routes.get(urlStr);
        if (list != null && !list.isEmpty()) {
            return list.remove(0);
        }
        // Default 404
        return new FakeHttpConnection(url, 404, "Not Found".getBytes(), null);
    }
}
