package org.wanshu.reader.ui.reader;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import org.wanshu.reader.core.text.AnchorMapper;
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

    private final LinearLayout searchBar;
    private final android.widget.EditText searchEditText;
    private final TextView searchCountView;
    private final Button searchPrevButton;
    private final Button searchNextButton;
    private final Button searchCloseButton;

    private final LinearLayout downloadStatusBar;
    private final TextView downloadStatusText;

    private final RecyclerView recyclerView;
    private final LinearLayoutManager layoutManager;
    private final ReaderBlockAdapter adapter;
    private final ReaderAnchorSampler anchorSampler;
    private ReaderScrollListener scrollListener;

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
        headerBar.setPadding(16, 16, 16, 16);
        headerBar.setBackgroundColor(currentThemeColors.barBackground);

        backButton = new Button(context);
        backButton.setText("书架");
        headerBar.addView(backButton);

        headerTitleView = new TextView(context);
        headerTitleView.setTextSize(16f);
        headerTitleView.setTextColor(currentThemeColors.text);
        headerTitleView.setPadding(24, 0, 16, 0);
        headerTitleView.setSingleLine(true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f);
        headerBar.addView(headerTitleView, titleParams);

        searchButton = new Button(context);
        searchButton.setText("搜索");
        headerBar.addView(searchButton);

        bookmarkButton = new Button(context);
        bookmarkButton.setText("书签");
        headerBar.addView(bookmarkButton);

        tocButton = new Button(context);
        tocButton.setText("目录");
        headerBar.addView(tocButton);

        settingsButton = new Button(context);
        settingsButton.setText("设置");
        headerBar.addView(settingsButton);

        root.addView(headerBar);

        // 1.5 Search Bar
        searchBar = new LinearLayout(context);
        searchBar.setOrientation(LinearLayout.HORIZONTAL);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        searchBar.setPadding(16, 8, 16, 8);
        searchBar.setBackgroundColor(currentThemeColors.barBackground);
        searchBar.setVisibility(View.GONE);

        searchEditText = new android.widget.EditText(context);
        searchEditText.setHint("本章搜索...");
        searchEditText.setSingleLine(true);
        searchEditText.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        searchEditText.setTextColor(currentThemeColors.text);
        searchEditText.setHintTextColor(currentThemeColors.secondaryText);
        LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f);
        searchBar.addView(searchEditText, editParams);

        searchCountView = new TextView(context);
        searchCountView.setText("0/0");
        searchCountView.setPadding(16, 0, 16, 0);
        searchCountView.setTextColor(currentThemeColors.secondaryText);
        searchBar.addView(searchCountView);

        searchPrevButton = new Button(context);
        searchPrevButton.setText("▲");
        searchBar.addView(searchPrevButton);

        searchNextButton = new Button(context);
        searchNextButton.setText("▼");
        searchBar.addView(searchNextButton);

        searchCloseButton = new Button(context);
        searchCloseButton.setText("✕");
        searchBar.addView(searchCloseButton);

        root.addView(searchBar);

        // 1.8 Download Status Line
        downloadStatusBar = new LinearLayout(context);
        downloadStatusBar.setOrientation(LinearLayout.HORIZONTAL);
        downloadStatusBar.setGravity(Gravity.CENTER_VERTICAL);
        downloadStatusBar.setPadding(24, 8, 24, 8);
        downloadStatusBar.setBackgroundColor(currentThemeColors.barBackground);
        downloadStatusBar.setVisibility(View.GONE);

        downloadStatusText = new TextView(context);
        downloadStatusText.setTextSize(12f);
        downloadStatusText.setTextColor(currentThemeColors.secondaryText);
        downloadStatusText.setSingleLine(true);
        downloadStatusText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        downloadStatusBar.addView(downloadStatusText, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT
        ));
        root.addView(downloadStatusBar);

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
        errorLayout.setPadding(48, 48, 48, 48);
        errorLayout.setLayoutParams(new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        errorLayout.setVisibility(View.GONE);

        errorMsgView = new TextView(context);
        errorMsgView.setText("加载失败");
        errorMsgView.setTextSize(16f);
        errorMsgView.setTextColor(Color.parseColor("#B00020"));
        errorMsgView.setGravity(Gravity.CENTER);
        errorLayout.addView(errorMsgView);

        LinearLayout errorBtnRow = new LinearLayout(context);
        errorBtnRow.setOrientation(LinearLayout.HORIZONTAL);
        errorBtnRow.setPadding(0, 32, 0, 0);

        retryButton = new Button(context);
        retryButton.setText("重试");
        errorBtnRow.addView(retryButton);

        errorBackButton = new Button(context);
        errorBackButton.setText("返回书架");
        errorBtnRow.addView(errorBackButton);

        errorLayout.addView(errorBtnRow);
        bodyFrame.addView(errorLayout);

        // 4. Content RecyclerView
        recyclerView = new RecyclerView(context);
        layoutManager = new LinearLayoutManager(context);
        recyclerView.setLayoutManager(layoutManager);
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
        adapter.setListeners(prevListener, shelfListener, nextListener);
    }

    public void setRetryClickListener(View.OnClickListener listener) {
        retryButton.setOnClickListener(listener);
    }

    public void setTocClickListener(View.OnClickListener listener) {
        tocButton.setOnClickListener(listener);
    }

    public void setBookmarkClickListener(View.OnClickListener listener) {
        bookmarkButton.setOnClickListener(listener);
    }

    public void setSearchToggleClickListener(View.OnClickListener listener) {
        searchButton.setOnClickListener(listener);
    }

    public void setSettingsClickListener(View.OnClickListener listener) {
        settingsButton.setOnClickListener(listener);
    }

    public void setSearchListeners(
            View.OnClickListener prev,
            View.OnClickListener next,
            View.OnClickListener close
    ) {
        searchPrevButton.setOnClickListener(prev);
        searchNextButton.setOnClickListener(next);
        searchCloseButton.setOnClickListener(close);
    }

    public void showSearchBar() {
        searchBar.setVisibility(View.VISIBLE);
        searchEditText.requestFocus();
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager)
                getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(searchEditText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        }
    }

    public void hideSearchBar() {
        searchBar.setVisibility(View.GONE);
        searchEditText.setText("");
        searchEditText.clearFocus();
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager)
                getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(searchEditText.getWindowToken(), 0);
        }
    }

    public boolean isSearchBarVisible() {
        return searchBar.getVisibility() == View.VISIBLE;
    }

    public void setSearchCount(int current, int total) {
        if (total <= 0) {
            searchCountView.setText("0/0");
        } else {
            searchCountView.setText(current + "/" + total);
        }
    }

    public android.widget.EditText getSearchEditText() {
        return searchEditText;
    }

    public ReaderBlockAdapter getAdapter() {
        return adapter;
    }

    public void applyTheme(ThemeColors colors) {
        if (colors == null) return;
        this.currentThemeColors = colors;
        setBackgroundColor(colors.background);
        headerBar.setBackgroundColor(colors.barBackground);
        headerTitleView.setTextColor(colors.text);
        searchBar.setBackgroundColor(colors.barBackground);
        searchEditText.setTextColor(colors.text);
        searchEditText.setHintTextColor(colors.secondaryText);
        searchCountView.setTextColor(colors.secondaryText);
        downloadStatusBar.setBackgroundColor(colors.barBackground);
        downloadStatusText.setTextColor(colors.secondaryText);
        adapter.setTypography(colors, -1, -1, -1);
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
            searchEditText.setHintTextColor(colors.secondaryText);
            searchCountView.setTextColor(colors.secondaryText);
            downloadStatusBar.setBackgroundColor(colors.barBackground);
            downloadStatusText.setTextColor(colors.secondaryText);
        }
        adapter.setTypography(currentThemeColors, fontSizeSp, lineSpacingMultiplier, marginDp);
    }

    public void setDownloadStatus(String status) {
        if (status != null && !status.isEmpty()) {
            downloadStatusText.setText(status);
            downloadStatusBar.setVisibility(View.VISIBLE);
        } else {
            downloadStatusBar.setVisibility(View.GONE);
        }
    }

    public void setDownloadStatusClickListener(View.OnClickListener listener) {
        downloadStatusBar.setOnClickListener(listener);
        downloadStatusText.setOnClickListener(listener);
    }

    public void showLoading() {
        loadingLayout.setVisibility(View.VISIBLE);
        errorLayout.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);
    }

    public void showError(String message) {
        loadingLayout.setVisibility(View.GONE);
        errorLayout.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        errorMsgView.setText(message != null ? "加载失败: " + message : "加载失败");
    }

    public void showContent(
            String title,
            List<TextBlock> blocks,
            boolean hasPrev,
            boolean hasNext,
            int targetParagraphIndex
    ) {
        loadingLayout.setVisibility(View.GONE);
        errorLayout.setVisibility(View.GONE);
        recyclerView.setVisibility(View.VISIBLE);

        headerTitleView.setText(title != null ? title : "");
        adapter.setData(title, blocks, hasPrev, hasNext);

        if (targetParagraphIndex > 0 && blocks != null && !blocks.isEmpty()) {
            int blockIdx = AnchorMapper.findBlockIndexForParagraph(blocks, targetParagraphIndex);
            int targetPos = blockIdx + 1; // +1 for header
            recyclerView.post(new ReaderScrollToPositionRunnable(recyclerView, targetPos, 0));
        } else {
            recyclerView.scrollToPosition(0);
        }
    }

    public ReaderAnchorSampler getAnchorSampler() {
        return anchorSampler;
    }

    public RecyclerView getRecyclerView() {
        return recyclerView;
    }
}
