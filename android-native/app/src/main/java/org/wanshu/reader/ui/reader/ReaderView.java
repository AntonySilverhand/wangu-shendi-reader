package org.wanshu.reader.ui.reader;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.inputmethod.BaseInputConnection;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import org.wanshu.reader.ui.ActionFlowLayout;
import org.wanshu.reader.ui.Ui;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import org.wanshu.reader.core.text.TextBlock;
import org.wanshu.reader.ui.reader.adapter.ReaderBlockAdapter;
import org.wanshu.reader.ui.reader.anchor.AnchorSampleCallback;
import org.wanshu.reader.ui.reader.anchor.ReaderAnchorSampler;
import org.wanshu.reader.ui.reader.anchor.ReaderScrollListener;
import org.wanshu.reader.ui.theme.ReaderThemeConfig;
import org.wanshu.reader.ui.theme.ThemeColors;

public class ReaderView extends FrameLayout {
    private final LinearLayout headerBar;
    private final TextView headerTitleView;
    private final Button backButton;
    private final Button searchButton;
    private final Button bookmarkButton;
    private final Button tocButton;
    private final Button settingsButton;

    private final ActionFlowLayout actionsBar;
    private final LinearLayout searchBar;
    private float fontSizeSp = 18f;
    private int maxWidthChars = 36;
    private int pageMarginDp = 20;
    private final android.widget.EditText searchEditText;
    private final TextView searchCountView;
    private final Button searchPrevButton;
    private final Button searchNextButton;
    private final Button searchCloseButton;

    private final RecyclerView recyclerView;
    private final LinearLayoutManager layoutManager;
    private final ReaderBlockAdapter adapter;
    private final ReaderAnchorSampler anchorSampler;
    private ReaderScrollListener scrollListener;
    private ReaderRestoreAnchorRunnable pendingRestore;
    private View.OnClickListener prevChapterListener, nextChapterListener;

    private final LinearLayout loadingLayout;
    private final LinearLayout errorLayout;
    private final TextView errorMsgView;
    private final Button retryButton;
    private final Button errorBackButton;

    private ThemeColors currentThemeColors;

    public ReaderView(Context context) {
        super(context);
        this.currentThemeColors = ReaderThemeConfig.getThemeColors(ReaderThemeConfig.THEME_LIGHT);
        setBackgroundColor(currentThemeColors.background);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // 1. Header Bar
        headerBar = new LinearLayout(context);
        headerBar.setOrientation(LinearLayout.HORIZONTAL);
        headerBar.setGravity(Gravity.CENTER_VERTICAL);
        headerBar.setPadding(Ui.dp(context, 4), 0, Ui.dp(context, 8), 0);
        headerBar.setBackgroundColor(currentThemeColors.barBackground);

        backButton = Ui.button(context, "‹ 书架", null);
        backButton.setContentDescription("返回书架，切换书籍");
        headerBar.addView(backButton);

        headerTitleView = new TextView(context);
        headerTitleView.setTextSize(15f);
        headerTitleView.setTextColor(currentThemeColors.text);
        headerTitleView.setPadding(12, 0, 8, 0);
        headerTitleView.setSingleLine(true);
        headerTitleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f);
        headerBar.addView(headerTitleView, titleParams);

        root.addView(headerBar);
        actionsBar = new ActionFlowLayout(context);
        actionsBar.setBackgroundColor(currentThemeColors.barBackground);
        tocButton = Ui.button(context, "目录", null);
        searchButton = Ui.button(context, "搜索", null);
        bookmarkButton = Ui.button(context, "书签", null);
        settingsButton = Ui.button(context, "设置", null);
        actionsBar.addView(tocButton);
        actionsBar.addView(searchButton);
        actionsBar.addView(bookmarkButton);
        actionsBar.addView(settingsButton);
        root.addView(actionsBar);

        // 1.1 Search Bar (hidden by default)
        searchBar = new LinearLayout(context);
        searchBar.setOrientation(LinearLayout.VERTICAL);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        searchBar.setPadding(Ui.dp(context, 8), 0, Ui.dp(context, 8), 0);
        searchBar.setBackgroundColor(currentThemeColors.barBackground);
        searchBar.setVisibility(View.GONE);

        searchEditText = new android.widget.EditText(context);
        searchEditText.setHint("搜索本章...");
        searchEditText.setSingleLine(true);
        searchEditText.setTextSize(14f);
        searchEditText.setTextColor(currentThemeColors.text);
        searchEditText.setHintTextColor(Color.parseColor("#888888"));
        searchEditText.setMinHeight(Ui.dp(context, 48));
        LinearLayout.LayoutParams searchEditParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        searchBar.addView(searchEditText, searchEditParams);

        searchCountView = new TextView(context);
        searchCountView.setText("0/0");
        searchCountView.setTextSize(13f);
        searchCountView.setTextColor(currentThemeColors.text);
        searchCountView.setPadding(Ui.dp(context, 4), 0, Ui.dp(context, 4), 0);
        searchCountView.setMaxWidth(Ui.dp(context, 160));
        searchCountView.setSingleLine(true);
        searchCountView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        ActionFlowLayout searchActions = new ActionFlowLayout(context);
        searchActions.addView(searchCountView);
        searchPrevButton = Ui.button(context, "↑", null);
        searchPrevButton.setContentDescription("上一个匹配");
        searchActions.addView(searchPrevButton);
        searchNextButton = Ui.button(context, "↓", null);
        searchNextButton.setContentDescription("下一个匹配");
        searchActions.addView(searchNextButton);
        searchCloseButton = Ui.button(context, "×", null);
        searchCloseButton.setContentDescription("关闭搜索");
        searchActions.addView(searchCloseButton);
        searchBar.addView(searchActions);

        root.addView(searchBar);

        // Frame to hold loading, error, and content
        FrameLayout bodyFrame = new FrameLayout(context);
        bodyFrame.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1.0f));

        // 2. Loading Layout
        loadingLayout = new LinearLayout(context);
        loadingLayout.setOrientation(LinearLayout.VERTICAL);
        loadingLayout.setGravity(Gravity.CENTER);
        loadingLayout.setLayoutParams(new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        ProgressBar progressBar = new ProgressBar(context);
        loadingLayout.addView(progressBar);

        TextView loadingText = new TextView(context);
        loadingText.setText("正在加载正文...");
        loadingText.setPadding(0, 24, 0, 0);
        loadingText.setTextColor(Color.parseColor("#666666"));
        loadingLayout.addView(loadingText);
        bodyFrame.addView(loadingLayout);

        // 3. Error Layout
        errorLayout = new LinearLayout(context);
        errorLayout.setOrientation(LinearLayout.VERTICAL);
        errorLayout.setGravity(Gravity.CENTER);
        errorLayout.setPadding(Ui.dp(context, 16), Ui.dp(context, 8), Ui.dp(context, 16), Ui.dp(context, 8));
        errorLayout.setLayoutParams(new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        errorLayout.setVisibility(View.GONE);

        errorMsgView = new TextView(context);
        errorMsgView.setText("加载失败");
        errorMsgView.setTextSize(16f);
        errorMsgView.setTextColor(Color.parseColor("#B00020"));
        errorMsgView.setGravity(Gravity.CENTER);
        errorLayout.addView(errorMsgView);

        ActionFlowLayout errorBtnRow = new ActionFlowLayout(context);
        errorBtnRow.setPadding(0, Ui.dp(context, 8), 0, 0);

        retryButton = Ui.button(context, "重试", null);
        errorBtnRow.addView(retryButton);

        errorBackButton = Ui.button(context, "返回书架", null);
        errorBtnRow.addView(errorBackButton);

        errorLayout.addView(errorBtnRow);
        android.widget.ScrollView errorScroll = new android.widget.ScrollView(context);
        errorScroll.setFillViewport(true);
        errorScroll.addView(errorLayout, new android.widget.ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        bodyFrame.addView(errorScroll, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // 4. Content RecyclerView
        recyclerView = new RecyclerView(context);
        layoutManager = new LinearLayoutManager(context);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setItemAnimator(null);
        recyclerView.setClipToPadding(false);
        recyclerView.setLayoutParams(new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        recyclerView.setVisibility(View.GONE);

        adapter = new ReaderBlockAdapter();
        recyclerView.setAdapter(adapter);

        anchorSampler = new ReaderAnchorSampler(recyclerView, adapter);

        bodyFrame.addView(recyclerView);
        root.addView(bodyFrame);
        addView(root);
    }

    public void setAnchorCallback(AnchorSampleCallback callback) {
        if (scrollListener != null) {
            recyclerView.removeOnScrollListener(scrollListener);
        }
        scrollListener = new ReaderScrollListener(anchorSampler, callback);
        recyclerView.addOnScrollListener(scrollListener);
    }

    public void setChapterIdentity(String bookId, String chapterId) {
        anchorSampler.setChapter(bookId, chapterId);
    }

    public void setBackClickListener(View.OnClickListener listener) {
        backButton.setOnClickListener(listener);
        errorBackButton.setOnClickListener(listener);
    }

    public void setNavigationListeners(
            View.OnClickListener prevListener,
            View.OnClickListener shelfListener,
            View.OnClickListener nextListener
    ) {
        prevChapterListener = prevListener; nextChapterListener = nextListener;
        adapter.setListeners(prevListener, shelfListener, nextListener);
    }

    /** Called only for keys not already handled by the focused widget. */
    public boolean handleReaderKey(int keyCode, KeyEvent event) {
        if (getVisibility() != View.VISIBLE || event.getAction() != KeyEvent.ACTION_DOWN
                || event.isCtrlPressed() || event.isAltPressed() || event.isMetaPressed()) return false;
        if (isSearchBarVisible()) {
            if (keyCode != KeyEvent.KEYCODE_ESCAPE || BaseInputConnection.getComposingSpanStart(searchEditText.getText()) >= 0) return false;
            if (event.getRepeatCount() == 0) searchCloseButton.performClick();
            return true;
        }
        View focus = findFocus();
        if (focus != null && (focus.onCheckIsTextEditor() || (focus instanceof TextView && ((TextView) focus).hasSelection()))) return false;
        Button action;
        switch (keyCode) {
            case KeyEvent.KEYCODE_T: action = tocButton; break;
            case KeyEvent.KEYCODE_F: action = searchButton; break;
            case KeyEvent.KEYCODE_B: action = bookmarkButton; break;
            case KeyEvent.KEYCODE_S: case KeyEvent.KEYCODE_COMMA: action = settingsButton; break;
            case KeyEvent.KEYCODE_DPAD_LEFT: case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (event.isShiftPressed()) return false;
                View.OnClickListener listener = keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? prevChapterListener : nextChapterListener;
                if (listener == null) return false;
                if (event.getRepeatCount() == 0) listener.onClick(this);
                return true;
            case KeyEvent.KEYCODE_MOVE_HOME: case KeyEvent.KEYCODE_MOVE_END:
                if (event.isShiftPressed() || recyclerView.getVisibility() != View.VISIBLE || adapter.getItemCount() == 0) return false;
                if (event.getRepeatCount() == 0) {
                    pendingRestore = null;
                    recyclerView.stopScroll();
                    layoutManager.scrollToPositionWithOffset(keyCode == KeyEvent.KEYCODE_MOVE_HOME ? 0 : adapter.getItemCount() - 1, 0);
                }
                return true;
            default: return false;
        }
        if (event.getRepeatCount() == 0) action.performClick();
        return true;
    }

    public void setRetryClickListener(View.OnClickListener listener) {
        retryButton.setOnClickListener(listener);
    }

    public void setTypography(
            ThemeColors colors,
            float fontSizeSp,
            float lineSpacingMultiplier,
            int marginDp
    ) {
        if (colors != null) {
            this.currentThemeColors = colors;
            setBackgroundColor(colors.background);
            headerBar.setBackgroundColor(colors.barBackground);
            headerTitleView.setTextColor(colors.text);
            searchBar.setBackgroundColor(colors.barBackground);
            searchEditText.setTextColor(colors.text);
            searchCountView.setTextColor(colors.text);
        }
        org.wanshu.reader.core.model.ReadingAnchor anchor = anchorSampler.sampleAnchor();
        this.fontSizeSp = fontSizeSp > 0 ? fontSizeSp : 18f;
        this.pageMarginDp = Math.max(8, marginDp);
        actionsBar.setBackgroundColor(currentThemeColors.barBackground);
        Ui.colors(this, currentThemeColors);
        adapter.setTypography(currentThemeColors, fontSizeSp, lineSpacingMultiplier, marginDp);
        updateLineWidth(getWidth());
        if (anchor != null) restoreAnchor(anchor.getParagraphIndex(), anchor.getOffsetUtf16());
    }

    public void setMaxWidthChars(int chars) {
        maxWidthChars = Math.max(24, Math.min(60, chars));
        updateLineWidth(getWidth());
    }

    private void updateLineWidth(int width) {
        int contentWidth = Math.round(fontSizeSp * getResources().getDisplayMetrics().scaledDensity * maxWidthChars)
                + Ui.dp(getContext(), pageMarginDp * 2);
        int gutter = Math.max(0, (width - contentWidth) / 2);
        recyclerView.setPadding(gutter, 0, gutter, 0);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        org.wanshu.reader.core.model.ReadingAnchor anchor = anchorSampler == null ? null : anchorSampler.sampleAnchor();
        super.onSizeChanged(w, h, oldw, oldh);
        if (recyclerView != null) {
            updateLineWidth(w);
            if (anchor != null && !isSearchBarVisible()) restoreAnchor(anchor.getParagraphIndex(), anchor.getOffsetUtf16());
        }
    }

    public void cancelAnchorRestore() { pendingRestore = null; }

    public void restoreAnchor(int paragraph, int offset) {
        pendingRestore = new ReaderRestoreAnchorRunnable(this, paragraph, offset);
        // Jump before the next frame so the chapter top never flashes; defer only during layout.
        if (recyclerView.isInLayout() || !isAttachedToWindow()) recyclerView.post(pendingRestore);
        else pendingRestore.run();
    }
    boolean isCurrentRestore(ReaderRestoreAnchorRunnable request) { return pendingRestore == request; }

    public void showLoading() {
        pendingRestore = null;
        loadingLayout.setVisibility(View.VISIBLE);
        errorLayout.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);
    }

    public void showError(String message) {
        pendingRestore = null;
        loadingLayout.setVisibility(View.GONE);
        errorLayout.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        errorMsgView.setText(message != null ? "加载失败: " + message : "加载失败");
    }

    public void showContent(String title, List<TextBlock> blocks, boolean hasPrev, boolean hasNext) {
        showContent(title, blocks, hasPrev, hasNext, false);
    }

    public void showContent(String title, List<TextBlock> blocks, boolean hasPrev, boolean hasNext, boolean restricted) {
        loadingLayout.setVisibility(View.GONE);
        errorLayout.setVisibility(View.GONE);
        recyclerView.setVisibility(View.VISIBLE);

        pendingRestore = null;
        headerTitleView.setText(title != null ? title : "");
        adapter.setData(title, blocks, hasPrev, hasNext, restricted);
        recyclerView.scrollToPosition(0);
    }

    public void setSearchListeners(
            View.OnClickListener toggleListener,
            View.OnClickListener prevListener,
            View.OnClickListener nextListener,
            View.OnClickListener closeListener,
            android.text.TextWatcher textWatcher
    ) {
        searchButton.setOnClickListener(toggleListener);
        searchPrevButton.setOnClickListener(prevListener);
        searchNextButton.setOnClickListener(nextListener);
        searchCloseButton.setOnClickListener(closeListener);
        if (textWatcher != null) {
            searchEditText.addTextChangedListener(textWatcher);
        }
    }

    public void setTopBarActionListeners(
            View.OnClickListener tocListener,
            View.OnClickListener bookmarkListener,
            View.OnClickListener settingsListener
    ) {
        tocButton.setOnClickListener(tocListener);
        bookmarkButton.setOnClickListener(bookmarkListener);
        settingsButton.setOnClickListener(settingsListener);
    }

    public void showSearchBar() {
        searchBar.setVisibility(View.VISIBLE);
        actionsBar.setVisibility(View.GONE);
        searchEditText.requestFocus();
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager)
                getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(searchEditText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        }
    }

    public void hideSearchBar() {
        searchBar.setVisibility(View.GONE);
        actionsBar.setVisibility(View.VISIBLE);
        searchEditText.setText("");
        searchEditText.clearFocus();
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager)
                getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(getWindowToken(), 0);
        }
    }

    public boolean isSearchBarVisible() {
        return searchBar.getVisibility() == View.VISIBLE;
    }

    public void setSearchMatchCount(int current, int total) {
        searchCountView.setText(current + "/" + total);
        searchCountView.setContentDescription(total > 0 ? "第" + current + "处，共" + total + "处匹配" : "无匹配");
        searchPrevButton.setEnabled(total > 0);
        searchNextButton.setEnabled(total > 0);
    }

    public android.widget.EditText getSearchEditText() {
        return searchEditText;
    }

    public ReaderBlockAdapter getAdapter() {
        return adapter;
    }

    public ReaderAnchorSampler getAnchorSampler() {
        return anchorSampler;
    }

    public RecyclerView getRecyclerView() {
        return recyclerView;
    }
}
