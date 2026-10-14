package org.wanshu.reader.core.source;

import java.io.IOException;
import org.wanshu.reader.core.model.BookInfo;
import org.wanshu.reader.core.net.CancellationToken;
import org.wanshu.reader.core.net.HttpFetchResponse;
import org.wanshu.reader.core.net.SourceHttpClient;
import org.wanshu.reader.core.queue.SchedulerAction;

/** Loads a book's detail page (TOC page 1) and reads its metadata. Runs on the scheduler's worker. */
public class FetchBookInfoAction implements SchedulerAction<BookInfo> {

    private final String bookId;
    private final SourceHttpClient client;

    public FetchBookInfoAction(String bookId, SourceHttpClient client) {
        SourceUrls.assertBookId(bookId);
        this.bookId = bookId;
        this.client = client;
    }

    @Override
    public BookInfo execute(CancellationToken token) throws Exception {
        HttpFetchResponse resp = client.fetch(SourceUrls.buildTocUrl(bookId, 1), token);
        if (!resp.isSuccessful()) {
            throw new IOException("HTTP error " + resp.getStatusCode() + " on book page");
        }
        BookInfo info = SourceParser.parseBookInfo(bookId, resp.getBody());
        if (info == null) {
            throw new IOException("book page has no book info");
        }
        return info;
    }
}
