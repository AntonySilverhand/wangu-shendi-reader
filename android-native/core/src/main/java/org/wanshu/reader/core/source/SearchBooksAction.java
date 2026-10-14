package org.wanshu.reader.core.source;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;
import java.util.concurrent.CancellationException;
import org.wanshu.reader.core.model.BookSummary;
import org.wanshu.reader.core.net.CancellationToken;
import org.wanshu.reader.core.net.HttpFetchResponse;
import org.wanshu.reader.core.net.SourceHttpClient;
import org.wanshu.reader.core.queue.SchedulerAction;
import org.wanshu.reader.core.queue.SearchGate;

/** One upstream search (POST), spaced from other searches by the gate. Runs on the scheduler's worker. */
public class SearchBooksAction implements SchedulerAction<List<BookSummary>> {

    private final SourceHttpClient client;
    private final SearchGate gate;
    private final String normalizedQuery;

    public SearchBooksAction(SourceHttpClient client, SearchGate gate, String normalizedQuery) {
        this.client = client;
        this.gate = gate;
        this.normalizedQuery = normalizedQuery;
    }

    /** The form body for a search; spaces use %20 like encodeURIComponent. */
    public static String formBody(String normalizedQuery) {
        try {
            return "s=" + URLEncoder.encode(normalizedQuery, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public List<BookSummary> execute(CancellationToken token) throws Exception {
        if (normalizedQuery == null || SourceParser.normalizeSearchQuery(normalizedQuery) == null) {
            throw new IllegalArgumentException("invalid search query");
        }
        if (gate != null && !gate.awaitTurn(token)) {
            throw new CancellationException("search cancelled while waiting for its turn");
        }
        HttpFetchResponse resp = client.postForm(SourceUrls.SEARCH_URL, formBody(normalizedQuery), token);
        if (!resp.isSuccessful()) {
            throw new IOException("HTTP error " + resp.getStatusCode() + " on search");
        }
        List<BookSummary> results = SourceParser.parseSearchResults(resp.getBody());
        if (results == null) {
            throw new IOException("unexpected search page");
        }
        return results;
    }
}
