package org.wanshu.reader.data.repository;

import org.wanshu.reader.core.model.ChapterResult;
import org.wanshu.reader.core.net.CancellationToken;
import org.wanshu.reader.core.net.SourceHttpClient;
import org.wanshu.reader.core.queue.SchedulerAction;
import org.wanshu.reader.core.source.ChapterAssembler;
import org.wanshu.reader.core.source.HttpPageFetcher;
import org.wanshu.reader.core.util.Base64Decoder;

public class FetchChapterSchedulerAction implements SchedulerAction<ChapterResult> {

    private final String bookId;
    private final String chapterId;
    private final SourceHttpClient client;
    private final Base64Decoder decoder;

    public FetchChapterSchedulerAction(
            String bookId,
            String chapterId,
            SourceHttpClient client,
            Base64Decoder decoder
    ) {
        this.bookId = bookId;
        this.chapterId = chapterId;
        this.client = client;
        this.decoder = decoder;
    }

    @Override
    public ChapterResult execute(CancellationToken token) throws Exception {
        HttpPageFetcher pageFetcher = new HttpPageFetcher(client, decoder, token);
        ChapterAssembler assembler = new ChapterAssembler(pageFetcher);
        return assembler.assemble(bookId, chapterId);
    }
}
