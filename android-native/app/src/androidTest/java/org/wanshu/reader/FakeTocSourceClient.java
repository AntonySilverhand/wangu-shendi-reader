package org.wanshu.reader;

import org.wanshu.reader.core.net.SourceHttpClient;
import org.wanshu.reader.core.net.CancellationToken;
import org.wanshu.reader.core.net.HttpFetchResponse;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

public final class FakeTocSourceClient extends SourceHttpClient {
    final AtomicInteger calls = new AtomicInteger();
    volatile boolean failSecond = true;
    @Override public HttpFetchResponse fetch(String url, CancellationToken token) throws IOException {
        calls.incrementAndGet();
        if (token.isCancelled()) throw new IOException("cancelled");
        boolean second = url.endsWith("/1.html");
        String html = "<select><option value=\"/book/36780/1.html\">2</option></select><dl>"
                + (second ? "<a href=\"/book/36780_1002.html\">第一百零一章 重逢</a>" : "<a href=\"/book/36780_1001.html\">第一章 开始</a>") + "</dl>";
        return new HttpFetchResponse(second && failSecond ? 502 : 200, html, null, "text/html", url);
    }
}
