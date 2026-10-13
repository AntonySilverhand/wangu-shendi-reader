package org.wanshu.reader.ui.reader;

public final class ReaderFailureRunnable implements Runnable {
    private final ReaderController controller;
    private final String message;
    private final long generation;
    public ReaderFailureRunnable(ReaderController controller, String message, long generation) {
        this.controller = controller; this.message = message; this.generation = generation;
    }
    @Override public void run() { controller.applyFailure(message, generation); }
}
