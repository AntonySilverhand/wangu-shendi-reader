package org.wanshu.reader.core.net;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public interface HttpConnectionFactory {
    HttpURLConnection openConnection(URL url) throws IOException;
}
