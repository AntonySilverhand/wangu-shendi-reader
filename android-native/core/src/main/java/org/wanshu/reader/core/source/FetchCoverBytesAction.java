package org.wanshu.reader.core.source;

import java.io.IOException;
import org.wanshu.reader.core.net.CancellationToken;
import org.wanshu.reader.core.net.HttpFetchResponse;
import org.wanshu.reader.core.net.SourceHttpClient;
import org.wanshu.reader.core.queue.SchedulerAction;

/** Downloads a cover picture (image content type, at most 1 MiB). Runs on the scheduler's worker. */
public class FetchCoverBytesAction implements SchedulerAction<byte[]> {

    private final String bookId;
    private final SourceHttpClient client;

    public FetchCoverBytesAction(String bookId, SourceHttpClient client) {
        SourceUrls.assertBookId(bookId);
        this.bookId = bookId;
        this.client = client;
    }

    @Override
    public byte[] execute(CancellationToken token) throws Exception {
        HttpFetchResponse resp = client.fetchBytes(SourceUrls.coverUrl(bookId), token, SourceHttpClient.MAX_IMAGE_BYTES);
        if (!resp.isSuccessful() || resp.getBodyBytes().length == 0) {
            throw new IOException("no cover (HTTP " + resp.getStatusCode() + ")");
        }
        return resp.getBodyBytes();
    }
}
