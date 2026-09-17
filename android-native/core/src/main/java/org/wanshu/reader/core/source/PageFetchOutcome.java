package org.wanshu.reader.core.source;

public class PageFetchOutcome {
    private final PageFetchOutcomeKind kind;
    private final RawChapterPage page;
    private final Throwable error;

    private PageFetchOutcome(PageFetchOutcomeKind kind, RawChapterPage page, Throwable error) {
        this.kind = kind;
        this.page = page;
        this.error = error;
    }

    public static PageFetchOutcome ok(RawChapterPage page) {
        return new PageFetchOutcome(PageFetchOutcomeKind.OK, page, null);
    }

    public static PageFetchOutcome notFound() {
        return new PageFetchOutcome(PageFetchOutcomeKind.NOT_FOUND, null, null);
    }

    public static PageFetchOutcome failed(Throwable error) {
        return new PageFetchOutcome(PageFetchOutcomeKind.FAILED, null, error);
    }

    public PageFetchOutcomeKind getKind() {
        return kind;
    }

    public RawChapterPage getPage() {
        return page;
    }

    public Throwable getError() {
        return error;
    }
}
