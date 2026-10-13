package org.wanshu.reader.ui.toc;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import org.wanshu.reader.ui.ActionFlowLayout;
import org.wanshu.reader.ui.Ui;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import org.wanshu.reader.core.toc.TocSearchController;
import org.wanshu.reader.core.toc.TocSearchResult;
import org.wanshu.reader.data.AppExecutors;
import org.wanshu.reader.data.content.ChapterCacheStatus;
import org.wanshu.reader.data.content.entity.TocEntryEntity;
import org.wanshu.reader.data.repository.ContentRepository;
import org.wanshu.reader.navigation.AppNavigator;
import org.wanshu.reader.navigation.DialogDismissHandler;
import org.wanshu.reader.ui.theme.ThemeColors;

public class TocDialog extends Dialog implements DialogDismissHandler, TocItemClickListener {
    private final String bookId;
    private final String currentChapterId;
    private final ContentRepository contentRepository;
    private final AppNavigator navigator;
    private final AppExecutors executors;
    private final ThemeColors colors;

    private TextView titleView;
    private TextView loadStatusView;
    private int loadedPages, totalPages = 44;
    private boolean loading;
    private long loadGeneration;
    private boolean positioned;
    private Button closeButton;
    private EditText searchEditText;
    private Button filterAllBtn;
    private Button filterMainBtn;
    private Button filterExtraBtn;
    private Button loadAllBtn;
    private ProgressBar progressBar;
    private TextView emptyMsgView;
    private RecyclerView recyclerView;
    private TocAdapter adapter;

    private List<TocEntryEntity> rawEntries = new ArrayList<TocEntryEntity>();
    private ChapterCacheStatus cacheStatus = null;
    private TocSearchController searchController = null;

    public TocDialog(
            Context context,
            String bookId,
            String currentChapterId,
            ContentRepository contentRepository,
            AppNavigator navigator,
            AppExecutors executors,
            ThemeColors colors
    ) {
        super(context);
        this.bookId = bookId != null ? bookId : "";
        this.currentChapterId = currentChapterId != null ? currentChapterId : "";
        this.contentRepository = contentRepository;
        this.navigator = navigator;
        this.executors = executors;
        this.colors = colors;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        Context ctx = getContext();
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        int bgColor = colors != null ? colors.background : Color.WHITE;
        int barBgColor = colors != null ? colors.barBackground : Color.parseColor("#F5F5F5");
        int textColor = colors != null ? colors.text : Color.BLACK;
        root.setBackgroundColor(bgColor);

        // 1. Header Bar
        LinearLayout headerBar = new LinearLayout(ctx);
        headerBar.setOrientation(LinearLayout.HORIZONTAL);
        headerBar.setGravity(Gravity.CENTER_VERTICAL);
        headerBar.setPadding(24, 16, 16, 16);
        headerBar.setBackgroundColor(barBgColor);

        titleView = new TextView(ctx);
        titleView.setText("目录");
        titleView.setTextSize(18f);
        titleView.setSingleLine(true);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titleView.setTextColor(textColor);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        headerBar.addView(titleView, titleParams);

        closeButton = Ui.button(ctx, "关闭", null);
        closeButton.setOnClickListener(new TocCloseClickListener(this));
        headerBar.addView(closeButton);

        root.addView(headerBar);

        // 2. Search Box
        LinearLayout searchRow = new LinearLayout(ctx);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setPadding(24, 12, 24, 12);
        searchRow.setBackgroundColor(barBgColor);

        searchEditText = new EditText(ctx);
        searchEditText.setHint("搜索章节名/章号...");
        searchEditText.setSingleLine(true);
        searchEditText.setTextSize(14f);
        searchEditText.setTextColor(textColor);
        searchEditText.setHintTextColor(Color.parseColor("#888888"));
        searchEditText.addTextChangedListener(new TocSearchTextWatcher(this));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        searchRow.addView(searchEditText, searchParams);

        root.addView(searchRow);

        // 3. Filter Tabs
        ActionFlowLayout filterRow = new ActionFlowLayout(ctx);
        filterRow.setPadding(Ui.dp(ctx, 8), 0, Ui.dp(ctx, 8), 0);

        filterAllBtn = Ui.button(ctx, "全部", null);
        filterAllBtn.setOnClickListener(new TocFilterClickListener(this, TocFilterType.ALL));
        filterRow.addView(filterAllBtn);

        filterMainBtn = Ui.button(ctx, "正文", null);
        filterMainBtn.setOnClickListener(new TocFilterClickListener(this, TocFilterType.MAIN));
        filterRow.addView(filterMainBtn);

        filterExtraBtn = Ui.button(ctx, "番外", null);
        filterExtraBtn.setOnClickListener(new TocFilterClickListener(this, TocFilterType.EXTRA));
        filterRow.addView(filterExtraBtn);

        loadAllBtn = Ui.button(ctx, "加载全目录", null);
        loadAllBtn.setOnClickListener(new TocLoadAllClickListener(this));
        if (bookId.startsWith("local-")) {
            loadAllBtn.setVisibility(View.GONE);
        }
        filterRow.addView(loadAllBtn);

        root.addView(filterRow);
        loadStatusView = new TextView(ctx);
        loadStatusView.setText("搜索已保存的目录；在线章节可先加载全目录。关闭或离开应用即暂停加载。");
        loadStatusView.setPadding(Ui.dp(ctx, 12), 0, Ui.dp(ctx, 12), 0);
        root.addView(loadStatusView);
        if (colors != null) Ui.colors(root, colors);

        // 4. Content Area
        FrameLayout contentArea = new FrameLayout(ctx);
        LinearLayout.LayoutParams areaParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f
        );
        contentArea.setLayoutParams(areaParams);

        progressBar = new ProgressBar(ctx);
        FrameLayout.LayoutParams pbParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        progressBar.setLayoutParams(pbParams);
        contentArea.addView(progressBar);

        emptyMsgView = new TextView(ctx);
        emptyMsgView.setText("暂无目录信息");
        emptyMsgView.setTextSize(15f);
        emptyMsgView.setTextColor(Color.parseColor("#888888"));
        emptyMsgView.setVisibility(View.GONE);
        FrameLayout.LayoutParams emptyParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        emptyMsgView.setLayoutParams(emptyParams);
        contentArea.addView(emptyMsgView);

        recyclerView = new RecyclerView(ctx);
        recyclerView.setLayoutManager(new LinearLayoutManager(ctx));
        recyclerView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        adapter = new TocAdapter();
        adapter.setItemClickListener(this);
        recyclerView.setAdapter(adapter);
        contentArea.addView(recyclerView);

        root.addView(contentArea);

        setContentView(root);

        if (getWindow() != null) {
            getWindow().setLayout(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
            );
        }

        loadData();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (getWindow() != null) getWindow().setLayout(Math.min(getContext().getResources().getDisplayMetrics().widthPixels, Ui.dp(getContext(), 720)), LinearLayout.LayoutParams.MATCH_PARENT);
    }

    @Override
    public void dismiss() {
        pauseLoading();
        if (searchController != null) { searchController.shutdown(); searchController = null; }
        super.dismiss();
    }

    public void pauseLoading() {
        loading = false; loadGeneration++;
        if (contentRepository.getTocLoader() != null) contentRepository.getTocLoader().cancel(this);
        if (searchController != null) searchController.cancel();
        if (loadAllBtn != null) { loadAllBtn.setEnabled(true); loadAllBtn.setText("继续加载目录"); }
    }

    public void applyLoadProgress(int loaded, int total, boolean finished, String error, long generation) {
        if (!isShowing() || !loading || generation != loadGeneration) return;
        loadedPages = loaded; totalPages = total;
        loadStatusView.setText(error == null ? "目录 " + loaded + "/" + total + " 页" : "目录加载暂停：" + error);
        loadAllBtn.setText(finished ? (error == null ? "目录已载入" : "重试未载入目录") : "加载 " + loaded + "/" + total);
        loadAllBtn.setEnabled(finished && error != null);
        loadData();
        if (finished) loading = false;
    }

    private void loadData() {
        progressBar.setVisibility(View.VISIBLE);
        emptyMsgView.setVisibility(View.GONE);
        contentRepository.getTocEntries(bookId, new TocEntriesLoadedCallback(this));
    }

    public void onTocEntriesLoaded(final List<TocEntryEntity> entries) {
        this.rawEntries = entries != null ? entries : new ArrayList<TocEntryEntity>();
        java.util.List<org.wanshu.reader.data.content.entity.TocPageEntity> pages = contentRepository.getDatabase().tocDao().getAllTocPages(bookId);
        loadedPages = contentRepository.getDatabase().tocDao().getLoadedPagesCount(bookId);
        totalPages = pages.isEmpty() ? (bookId.startsWith("local-") ? 1 : 44) : pages.get(pages.size() - 1).pageIndex;
        contentRepository.getChapterCacheStatus(bookId, new TocCacheStatusLoadedCallback(this));
    }

    public void onCacheStatusLoaded(ChapterCacheStatus status) {
        this.cacheStatus = status;
        executors.getMainThreadExecutor().execute(new TocBuildItemsRunnable(this));
    }

    public void onTocLoadError(String error) {
        executors.getMainThreadExecutor().execute(new TocLoadErrorRunnable(this, error));
    }

    public void applyLoadError(String error) {
        if (!isShowing()) return;
        progressBar.setVisibility(View.GONE);
        emptyMsgView.setText(error != null ? error : "加载目录失败");
        emptyMsgView.setVisibility(View.VISIBLE);
    }

    public void buildAndDisplayItems() {
        if (!isShowing()) return;
        progressBar.setVisibility(View.GONE);

        if (rawEntries == null || rawEntries.isEmpty()) {
            emptyMsgView.setText("暂无目录信息");
            emptyMsgView.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
            return;
        }

        emptyMsgView.setVisibility(View.GONE);
        recyclerView.setVisibility(View.VISIBLE);

        List<TocItemModel> items = new ArrayList<TocItemModel>();
        for (int i = 0; i < rawEntries.size(); i++) {
            TocEntryEntity entity = rawEntries.get(i);
            boolean isCurrent = entity.chapterId.equals(currentChapterId);
            TocCacheStatus cs = TocCacheStatus.UNCACHED;
            if (cacheStatus != null) {
                if (cacheStatus.isCompleted(entity.chapterId)) {
                    cs = TocCacheStatus.COMPLETE;
                } else if (cacheStatus.isPartial(entity.chapterId)) {
                    cs = TocCacheStatus.PARTIAL;
                }
            }

            items.add(new TocItemModel(
                    entity.chapterId,
                    entity.title,
                    entity.isExtra,
                    entity.orderKey,
                    isCurrent,
                    cs
            ));
        }

        adapter.setData(items, colors);

        filterAllBtn.setText("全部 (" + adapter.getTotalCount() + ")");
        filterMainBtn.setText("正文 (" + adapter.getMainCount() + ")");
        filterExtraBtn.setText("番外 (" + adapter.getExtraCount() + ")");

        // Init Search Controller
        if (searchController != null) searchController.shutdown();
        RoomTocSearchDeps deps = new RoomTocSearchDeps(bookId, rawEntries, totalPages, loadedPages);
        searchController = new TocSearchController(deps, new TocSearchLoadedEvents(this));
        if (searchEditText.length() > 0) searchController.searchNow(searchEditText.getText().toString());

        // Position once; arriving TOC pages must not keep pulling the user back.
        if (!positioned) {
            positioned = true;
            int currentPos = adapter.findPositionForChapter(currentChapterId);
            if (currentPos >= 0) recyclerView.scrollToPosition(currentPos);
        }
    }

    public void onFilterClicked(TocFilterType filter) {
        if (adapter != null) {
            adapter.setFilter(filter);
        }
    }

    public void onSearchQueryChanged(String query) {
        String q = query != null ? query.trim() : "";
        if (searchController != null) {
            searchController.setQuery(q);
        } else if (q.isEmpty() && adapter != null) {
            adapter.clearSearchResults();
        }
    }

    public void onSearchResultsUpdated() {
        executors.getMainThreadExecutor().execute(new TocSearchResultsRunnable(this));
    }

    public void applySearchResults() {
        if (!isShowing() || searchController == null) return;
        // Read current state on UI, not an obsolete result captured by a queued callback.
        List<TocSearchResult> results = searchController.getCurrentResults();
        if (searchEditText.length() == 0) {
            titleView.setText("目录");
            adapter.clearSearchResults();
        } else {
            titleView.setText(results.size() >= TocSearchController.TOC_SEARCH_LIMIT ? "目录 · 前200项" : "目录 · " + results.size() + "项");
            adapter.setSearchResults(results);
        }
    }

    public void onLoadAllClicked() {
        if (!"36780".equals(bookId) || loading) return;
        if (contentRepository.getTocLoader() == null) { applyLoadError("目录加载器不可用"); return; }
        loading = true;
        loadAllBtn.setEnabled(false); loadAllBtn.setText("正在加载…");
        contentRepository.getTocLoader().load(bookId, this, new TocLoadProgressCallback(this, executors, ++loadGeneration));
    }

    @Override
    public void onTocItemClicked(TocItemModel item) {
        if (item != null) {
            navigator.openReader(bookId, item.getChapterId());
            dismiss();
        }
    }

    @Override
    public boolean dismissActiveDialog() {
        if (isShowing()) {
            dismiss();
            return true;
        }
        return false;
    }
}
