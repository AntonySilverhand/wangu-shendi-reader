package org.wanshu.reader.data.repository;

import org.junit.Test;
import org.wanshu.reader.core.model.ChapterResult;
import org.wanshu.reader.core.net.CancellationToken;
import org.wanshu.reader.core.queue.RequestPriority;
import java.util.Collections;
import static org.junit.Assert.*;

public class EnsureCompleteScopeTest {
    private EnsureCompleteCacheCheckCallback callback(String book, ChapterCallbackRecorder recorder, CancellationToken scope) {
        // Null network dependencies intentionally fail if these paths ever enqueue an upstream request.
        return new EnsureCompleteCacheCheckCallback(book, "12", RequestPriority.HIGH, "reader", recorder, null, null, null, null, scope);
    }
    @Test public void missingLocalChapterNeverFallsBackToRemoteBook() {
        ChapterCallbackRecorder recorder = new ChapterCallbackRecorder();
        callback("local-test", recorder, new CancellationToken()).onSuccess(null);
        assertEquals(1, recorder.errors); assertEquals(0, recorder.successes);
        assertTrue(recorder.error.getMessage().contains("重新导入"));
    }
    @Test public void localDbFailureIsReportedWithoutNetwork() {
        ChapterCallbackRecorder recorder = new ChapterCallbackRecorder();
        Throwable error = new IllegalStateException("missing local DB");
        callback("local-test", recorder, new CancellationToken()).onError(error);
        assertSame(error, recorder.error);
    }
    @Test public void cancelledAsyncCacheMissCannotStartNetworkOrDeliverStaleUi() {
        ChapterCallbackRecorder recorder = new ChapterCallbackRecorder();
        CancellationToken scope = new CancellationToken(); scope.cancel();
        EnsureCompleteCacheCheckCallback callback = callback("36780", recorder, scope);
        callback.onSuccess(null); callback.onError(new IllegalStateException("late"));
        assertEquals(0, recorder.errors + recorder.successes);
    }
    @Test public void completeCachedChapterDoesNotEnqueueNetwork() {
        ChapterCallbackRecorder recorder = new ChapterCallbackRecorder();
        ChapterResult chapter = new ChapterResult("36780", "12", "正文", Collections.singletonList("完整内容"), null, null, 1, 4, true, Collections.<Integer>emptyList());
        callback("36780", recorder, new CancellationToken()).onSuccess(chapter);
        assertSame(chapter, recorder.result); assertEquals(1, recorder.successes);
    }
}
