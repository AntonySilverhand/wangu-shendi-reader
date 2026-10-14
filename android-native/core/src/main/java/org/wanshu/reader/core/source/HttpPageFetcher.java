package org.wanshu.reader.core.source;

import java.io.IOException;
import org.wanshu.reader.core.net.CancellationToken;
import org.wanshu.reader.core.net.HttpFetchResponse;
import org.wanshu.reader.core.net.SourceHttpClient;
import org.wanshu.reader.core.util.Base64Decoder;

public class HttpPageFetcher implements PageFetcher {

    private final String bookId;
    private final SourceHttpClient client;
    private final Base64Decoder decoder;
    private final CancellationToken token;

    public HttpPageFetcher(String bookId, SourceHttpClient client, Base64Decoder decoder, CancellationToken token) {
        SourceUrls.assertBookId(bookId);
        this.bookId = bookId;
        this.client = client != null ? client : new SourceHttpClient();
        this.decoder = decoder;
        this.token = token;
    }

    public HttpPageFetcher(String bookId, SourceHttpClient client, Base64Decoder decoder) {
        this(bookId, client, decoder, null);
    }

    @Override
    public PageFetchOutcome fetch(String chapterId, int pageIndex) {
        String url;
        try {
            url = SourceUrls.buildChapterUrl(bookId, chapterId, pageIndex);
        } catch (Exception e) {
            return PageFetchOutcome.failed(e);
        }

        try {
            HttpFetchResponse resp = client.fetch(url, token);
            if (resp.isNotFound()) {
                return PageFetchOutcome.notFound();
            }
            if (!resp.isSuccessful()) {
                return PageFetchOutcome.failed(new IOException("HTTP error " + resp.getStatusCode() + " on " + url));
            }

            RawChapterPage page = SourceParser.parseChapterPage(bookId, resp.getBody(), chapterId, pageIndex, decoder);
            return PageFetchOutcome.ok(page);
        } catch (Throwable t) {
            return PageFetchOutcome.failed(t);
        }
    }
}
