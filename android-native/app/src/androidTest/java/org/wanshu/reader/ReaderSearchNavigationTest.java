package org.wanshu.reader;

import android.content.Context;
import android.os.Looper;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wanshu.reader.core.model.ChapterResult;
import org.wanshu.reader.core.queue.RequestScheduler;
import org.wanshu.reader.core.text.TextBlockBuilder;
import org.wanshu.reader.data.AppExecutors;
import org.wanshu.reader.data.MainThreadExecutor;
import org.wanshu.reader.data.content.ContentDatabase;
import org.wanshu.reader.data.personal.PersonalDatabase;
import org.wanshu.reader.data.repository.ContentRepository;
import org.wanshu.reader.data.repository.PersonalRepository;
import org.wanshu.reader.data.repository.ReaderRepository;
import org.wanshu.reader.navigation.AppNavigator;
import org.wanshu.reader.navigation.BookRoute;
import org.wanshu.reader.ui.reader.ReaderController;
import org.wanshu.reader.ui.reader.ReaderView;
import static org.junit.Assert.*;

/** Real controller + serial worker; no reader observation or remote requests are started. */
@RunWith(AndroidJUnit4.class)
public class ReaderSearchNavigationTest implements Runnable {
    private PersonalDatabase personal;
    private ContentDatabase content;
    private RequestScheduler scheduler;
    private ExecutorService worker;
    private AppExecutors executors;
    private ReaderController controller;
    private ReaderView view;
    private int step;
    private CountDownLatch barrier;

    @Before public void setup() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        personal = Room.inMemoryDatabaseBuilder(context, PersonalDatabase.class).build();
        content = Room.inMemoryDatabaseBuilder(context, ContentDatabase.class).build();
        scheduler = new RequestScheduler();
        worker = Executors.newSingleThreadExecutor();
        executors = new AppExecutors(worker, worker, worker, worker, new MainThreadExecutor());
    }
    @After public void cleanup() throws Exception {
        onUi(9); drain(); scheduler.shutdown(); worker.shutdownNow(); personal.close(); content.close();
    }
    @Test public void windowsWrapAndRapidMovesOrChangedQueriesCannotPublishOldPositions() throws Exception {
        onUi(0); drain(); onUi(1);
        onUi(2); drain(); onUi(3);
        onUi(4); drain(); onUi(5);
        onUi(6); drain(); onUi(7);
        onUi(8); drain();
    }
    private void onUi(int value) {
        step = value; InstrumentationRegistry.getInstrumentation().runOnMainSync(this);
    }
    private void drain() throws Exception {
        barrier = new CountDownLatch(1); worker.execute(this);
        assertTrue("worker did not finish", barrier.await(10, TimeUnit.SECONDS));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
    @Override public void run() {
        if (Looper.myLooper() != Looper.getMainLooper()) { barrier.countDown(); return; }
        switch (step) {
            case 0:
                Context context = new ContextThemeWrapper(InstrumentationRegistry.getInstrumentation().getTargetContext(), R.style.Theme_WanguReader);
                view = new ReaderView(context);
                AppNavigator navigator = new AppNavigator();
                ContentRepository repository = new ContentRepository(content, worker, new TextBlockBuilder());
                controller = new ReaderController(view, new ReaderRepository(repository, scheduler, null, null),
                        new PersonalRepository(personal, worker), navigator, executors, new TextBlockBuilder());
                BookRoute route = navigator.openReader("local-search-test", "1"); controller.open(route);
                StringBuilder text = new StringBuilder(); for (int i = 0; i < 10003; i++) text.append("😀剑 ");
                ChapterResult chapter = new ChapterResult(route.getBookId(), "1", "测试", Collections.singletonList(text.toString()), null, null, 1, text.length(), true, Collections.<Integer>emptyList());
                controller.applyChapter(chapter, controller.buildBlocks(chapter), route.getGeneration(), true);
                controller.toggleSearch(); view.getSearchEditText().setText("剑"); controller.executeSearch("剑"); break;
            case 1: assertCount("1/10003"); assertTrue(view.getAdapter().getBlocks().size() > 1); break;
            case 2: for (int i = 0; i < 500; i++) controller.nextSearchMatch(); break;
            case 3: assertCount("501/10003"); break;
            case 4:
                controller.prevSearchMatch(); controller.nextSearchMatch(); controller.nextSearchMatch(); controller.prevSearchMatch();
                break;
            case 5: assertCount("501/10003"); break;
            case 6:
                for (int i = 0; i < 501; i++) controller.prevSearchMatch();
                break;
            case 7:
                assertCount("10003/10003"); controller.nextSearchMatch();
                view.getSearchEditText().setText("无此词"); controller.executeSearch("无此词"); break;
            case 8:
                assertCount("0/0"); view.getSearchEditText().setText("剑"); controller.executeSearch("剑");
                controller.closeSearch(); assertFalse(view.isSearchBarVisible()); assertCount("0/0"); break;
            case 9: if (controller != null) controller.dispose(); break;
            default: fail("unexpected step");
        }
    }
    private void assertCount(String expected) { assertTrue("expected search counter " + expected, containsText(view, expected)); }
    private boolean containsText(View child, String expected) {
        if (child instanceof TextView && expected.contentEquals(((TextView) child).getText())) return true;
        if (child instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) child;
            for (int i = 0; i < group.getChildCount(); i++) if (containsText(group.getChildAt(i), expected)) return true;
        }
        return false;
    }
}
