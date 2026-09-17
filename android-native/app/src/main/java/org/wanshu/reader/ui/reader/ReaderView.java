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

        root.addView(headerBar);

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

    public void applyTheme(ThemeColors colors) {
        if (colors == null) return;
        this.currentThemeColors = colors;
        setBackgroundColor(colors.background);
        headerBar.setBackgroundColor(colors.barBackground);
        headerTitleView.setTextColor(colors.text);
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
        }
        adapter.setTypography(currentThemeColors, fontSizeSp, lineSpacingMultiplier, marginDp);
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
