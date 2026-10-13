package org.wanshu.reader;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import org.wanshu.reader.core.text.TextBlockBuilder;
import org.wanshu.reader.ui.Ui;
import org.wanshu.reader.ui.reader.ReaderView;
import java.util.Collections;
import static org.junit.Assert.*;

/** Geometry assertions, not a substitute for attached-window/IME/device screenshot testing. */
public final class ReaderChromeContractRunnable implements Runnable {
    private final Context base;
    public ReaderChromeContractRunnable(Context base) { this.base = base; }
    @Override public void run() {
        int[] widths = {240, 320, 412, 600, 840, 1600};
        int[] heights = {320, 390, 915};
        float[] scales = {1f, 1.5f, 2f};
        for (float scale : scales) {
            Configuration config = new Configuration(base.getResources().getConfiguration()); config.fontScale = scale;
            Context ctx = new ContextThemeWrapper(base.createConfigurationContext(config), R.style.Theme_WanguReader);
            for (int width : widths) for (int height : heights) {
                ReaderView reader = new ReaderView(ctx);
                ReaderHomeClickCounter clicks = new ReaderHomeClickCounter(); reader.setBackClickListener(clicks);
                reader.showContent("非常长的章节标题，不应挤走返回主页入口", new TextBlockBuilder().buildBlocks(Collections.singletonList("正文测试段落。")), false, false, 0);
                measure(reader, Ui.dp(ctx, width), Ui.dp(ctx, height));
                checkHome(reader, clicks);
                assertTrue("body must retain reading space", reader.getRecyclerView().getMeasuredHeight() >= Ui.dp(ctx, 48));
                reader.showSearchBar(); reader.setSearchMatchCount(1, 500);
                measure(reader, Ui.dp(ctx, width), Ui.dp(ctx, height)); checkHome(reader, clicks);
                assertTrue("search must not eliminate body", reader.getRecyclerView().getMeasuredHeight() > 0);
                reader.hideSearchBar(); reader.showError("网络不可用；仍可返回书架。网络不可用；仍可返回书架。");
                measure(reader, Ui.dp(ctx, width), Ui.dp(ctx, height)); checkHome(reader, clicks);
            }
        }
    }
    private void measure(ReaderView view, int width, int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
    }
    private void checkHome(ReaderView reader, ReaderHomeClickCounter clicks) {
        View home = findHome(reader); assertNotNull("permanent labelled Home button", home);
        assertTrue(home.getMeasuredHeight() >= Ui.dp(reader.getContext(), 48));
        Rect bounds = new Rect(0, 0, home.getWidth(), home.getHeight()); reader.offsetDescendantRectToMyCoords(home, bounds);
        assertTrue(bounds.left >= 0 && bounds.right <= reader.getWidth());
        assertTrue(bounds.top >= 0 && bounds.bottom <= reader.getHeight());
        int before = clicks.clicks; home.performClick(); assertEquals(before + 1, clicks.clicks);
    }
    private View findHome(View view) {
        if (view.getVisibility() != View.VISIBLE) return null;
        if (view instanceof Button && "‹ 书架".contentEquals(((Button) view).getText())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View result = findHome(group.getChildAt(i)); if (result != null) return result;
            }
        }
        return null;
    }
}
