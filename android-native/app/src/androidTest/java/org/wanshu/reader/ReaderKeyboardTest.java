package org.wanshu.reader;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wanshu.reader.core.text.TextBlockBuilder;
import org.wanshu.reader.ui.reader.ReaderView;
import static org.junit.Assert.*;

/** Exercises the real Views without opening databases or contacting the source. */
@RunWith(AndroidJUnit4.class)
public class ReaderKeyboardTest implements Runnable, View.OnClickListener {
    private ReaderView reader;

    @Test public void shortcutsRespectEditingModifiersRepeatsAndSearchComposition() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(this);
    }

    @Override public void run() {
        Context context = new ContextThemeWrapper(InstrumentationRegistry.getInstrumentation().getTargetContext(), R.style.Theme_WanguReader);
        reader = new ReaderView(context);
        ReaderHomeClickCounter prev = new ReaderHomeClickCounter(), next = new ReaderHomeClickCounter();
        ReaderHomeClickCounter toc = new ReaderHomeClickCounter(), bookmarks = new ReaderHomeClickCounter(), settings = new ReaderHomeClickCounter();
        reader.setNavigationListeners(prev, null, next);
        reader.setTopBarActionListeners(toc, bookmarks, settings);
        reader.setSearchListeners(this, null, null, this, null);
        reader.showContent("键盘测试", new TextBlockBuilder().buildBlocks(Collections.singletonList("正文。")), true, true, 0);

        assertTrue(press(KeyEvent.KEYCODE_DPAD_LEFT)); assertEquals(1, prev.clicks);
        assertTrue(press(KeyEvent.KEYCODE_DPAD_RIGHT)); assertEquals(1, next.clicks);
        assertTrue(press(KeyEvent.KEYCODE_T)); assertEquals(1, toc.clicks);
        assertTrue(press(KeyEvent.KEYCODE_B)); assertEquals(1, bookmarks.clicks);
        assertTrue(press(KeyEvent.KEYCODE_S)); assertTrue(press(KeyEvent.KEYCODE_COMMA)); assertEquals(2, settings.clicks);
        assertTrue(reader.handleReaderKey(KeyEvent.KEYCODE_T, key(KeyEvent.KEYCODE_T, 2, 0)));
        assertEquals("holding a key must not reopen a panel", 1, toc.clicks);
        assertFalse(reader.handleReaderKey(KeyEvent.KEYCODE_T, key(KeyEvent.KEYCODE_T, 0, KeyEvent.META_CTRL_ON)));
        assertFalse(reader.handleReaderKey(KeyEvent.KEYCODE_T, key(KeyEvent.KEYCODE_T, 0, KeyEvent.META_ALT_ON)));
        assertFalse(reader.handleReaderKey(KeyEvent.KEYCODE_T, key(KeyEvent.KEYCODE_T, 0, KeyEvent.META_META_ON)));
        assertFalse(press(KeyEvent.KEYCODE_TAB));
        assertFalse("system Home is not chapter Home", press(KeyEvent.KEYCODE_HOME));

        assertTrue(press(KeyEvent.KEYCODE_F)); assertTrue(reader.isSearchBarVisible());
        assertFalse(press(KeyEvent.KEYCODE_T)); assertFalse(press(KeyEvent.KEYCODE_DPAD_LEFT));
        assertFalse("Home in an editor belongs to the editor", press(KeyEvent.KEYCODE_MOVE_HOME));
        InputConnection input = reader.getSearchEditText().onCreateInputConnection(new EditorInfo());
        assertNotNull(input);
        input.setComposingText("拼", 1);
        assertFalse("Esc must not interrupt IME composition", press(KeyEvent.KEYCODE_ESCAPE));
        assertTrue(reader.isSearchBarVisible());
        input.finishComposingText();
        assertTrue(press(KeyEvent.KEYCODE_ESCAPE)); assertFalse(reader.isSearchBarVisible());
        assertEquals(1, toc.clicks); assertEquals(1, prev.clicks);

        assertTrue(press(KeyEvent.KEYCODE_MOVE_HOME)); assertTrue(press(KeyEvent.KEYCODE_MOVE_END));
        reader.showLoading();
        assertFalse("hidden old content must not receive edge jumps", press(KeyEvent.KEYCODE_MOVE_END));
        reader.setVisibility(View.GONE);
        assertFalse(press(KeyEvent.KEYCODE_T));
    }

    @Override public void onClick(View view) {
        if (reader.isSearchBarVisible()) reader.hideSearchBar(); else reader.showSearchBar();
    }
    private boolean press(int code) { return reader.handleReaderKey(code, key(code, 0, 0)); }
    private KeyEvent key(int code, int repeat, int modifiers) {
        return new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, code, repeat, modifiers);
    }
}
