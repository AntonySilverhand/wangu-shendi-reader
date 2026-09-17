package org.wanshu.reader.core.toc;

public class TocSearchPhase {
    private final TocSearchPhaseKind kind;
    private final String mode;
    private final int loadedPages;
    private final int totalPages;
    private final boolean complete;
    private final boolean exhausted;
    private final int failedPages;

    public TocSearchPhase(
            TocSearchPhaseKind kind,
            String mode,
            int loadedPages,
            int totalPages,
            boolean complete,
            boolean exhausted,
            int failedPages
    ) {
        this.kind = kind;
        this.mode = mode != null ? mode : "";
        this.loadedPages = loadedPages;
        this.totalPages = totalPages;
        this.complete = complete;
        this.exhausted = exhausted;
        this.failedPages = failedPages;
    }

    public static TocSearchPhase idle() {
        return new TocSearchPhase(TocSearchPhaseKind.IDLE, "", 0, 0, false, false, 0);
    }

    public static TocSearchPhase debouncing() {
        return new TocSearchPhase(TocSearchPhaseKind.DEBOUNCING, "", 0, 0, false, false, 0);
    }

    public static TocSearchPhase searching(String mode, int loadedPages, int totalPages) {
        return new TocSearchPhase(TocSearchPhaseKind.SEARCHING, mode, loadedPages, totalPages, false, false, 0);
    }

    public static TocSearchPhase done(boolean complete, boolean exhausted, int failedPages) {
        return new TocSearchPhase(TocSearchPhaseKind.DONE, "", 0, 0, complete, exhausted, failedPages);
    }

    public static TocSearchPhase cancelled() {
        return new TocSearchPhase(TocSearchPhaseKind.CANCELLED, "", 0, 0, false, false, 0);
    }

    public TocSearchPhaseKind getKind() {
        return kind;
    }

    public String getMode() {
        return mode;
    }

    public int getLoadedPages() {
        return loadedPages;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public boolean isComplete() {
        return complete;
    }

    public boolean isExhausted() {
        return exhausted;
    }

    public int getFailedPages() {
        return failedPages;
    }
}
