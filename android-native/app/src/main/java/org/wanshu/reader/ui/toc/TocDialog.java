package org.wanshu.reader.ui.toc;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import org.wanshu.reader.core.net.SourceHttpClient;
import org.wanshu.reader.core.toc.TocSearchController;
import org.wanshu.reader.core.toc.TocSearchPhase;
import org.wanshu.reader.core.toc.TocSearchPhaseKind;
import org.wanshu.reader.core.toc.TocSearchResult;
import org.wanshu.reader.data.AppExecutors;
import org.wanshu.reader.data.content.ContentDatabase;
import org.wanshu.reader.navigation.AppNavigator;
import org.wanshu.reader.ui.theme.ThemeColors;

public class TocDialog extends Dialog {
    private final String bookId;
    private final String currentChapterId;
    private final ContentDatabase db;
    private final SourceHttpClient httpClient;
    private final AppNavigator navigator;
    private final AppExecutors executors;
    private final ThemeColors colors;

    private ContentTocSearchDeps deps;
    private TocSearchController searchController;
    private TocAdapter adapter;

    private Button mainTabButton;
    private Button extraTabButton;
    private Button loadAllButton;
    private EditText searchEditText;
    private TextView statusView;
    private ProgressBar progressBar;
    private RecyclerView recyclerView;

    public TocDialog(
            Context context,
            String bookId,
            String currentChapterId,
            ContentDatabase db,
            SourceHttpClient httpClient,
            AppNavigator navigator,
            AppExecutors executors,
            ThemeColors colors
    ) {
        super(context);
        this.bookId = bookId != null ? bookId : "";
        this.currentChapterId = currentChapterId != null ? currentChapterId : "";
        this.db = db;
        this.httpClient = httpClient;
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
        int bg = colors != null ? colors.background : Color.WHITE;
        int barBg = colors != null ? colors.barBackground : Color.parseColor("#F5F5F5");
        int textCol = colors != null ? colors.text : Color.BLACK;
        int secTextCol = colors != null ? colors.secondaryText : Color.GRAY;

        root.setBackgroundColor(bg);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // 1. Header Bar
        LinearLayout headerBar = new LinearLayout(ctx);
        headerBar.setOrientation(LinearLayout.HORIZONTAL);
        headerBar.setGravity(Gravity.CENTER_VERTICAL);
        headerBar.setPadding(24, 16, 16, 16);
        headerBar.setBackgroundColor(barBg);

        TextView titleView = new TextView(ctx);
        titleView.setText("目录");
        titleView.setTextSize(18f);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setTextColor(textCol);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        headerBar.addView(titleView, titleParams);

        Button closeButton = new Button(ctx);
        closeButton.setText("✕");
        closeButton.setOnClickListener(new TocCloseClickListener(this));
        headerBar.addView(closeButton);
        root.addView(headerBar);

        // 2. Search Bar
        LinearLayout searchRow = new LinearLayout(ctx);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setPadding(24, 12, 16, 12);
        searchRow.setBackgroundColor(barBg);

        searchEditText = new EditText(ctx);
        searchEditText.setHint("搜索章节（支持数字、拼音、汉字）...");
        searchEditText.setSingleLine(true);
        searchEditText.setTextColor(textCol);
        searchEditText.setHintTextColor(secTextCol);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        searchRow.addView(searchEditText, searchParams);

        Button clearSearchBtn = new Button(ctx);
        clearSearchBtn.setText("清除");
        clearSearchBtn.setOnClickListener(new TocClearSearchClickListener(this));
        searchRow.addView(clearSearchBtn);
        root.addView(searchRow);

        // 3. Tabs & Actions Row
        LinearLayout tabsRow = new LinearLayout(ctx);
        tabsRow.setOrientation(LinearLayout.HORIZONTAL);
        tabsRow.setGravity(Gravity.CENTER_VERTICAL);
        tabsRow.setPadding(24, 8, 16, 8);
        tabsRow.setBackgroundColor(barBg);

        mainTabButton = new Button(ctx);
        mainTabButton.setText("正文");
        mainTabButton.setOnClickListener(new TocMainTabClickListener(this));
        tabsRow.addView(mainTabButton);

        extraTabButton = new Button(ctx);
        extraTabButton.setText("番外");
        extraTabButton.setOnClickListener(new TocExtraTabClickListener(this));
        tabsRow.addView(extraTabButton);

        loadAllButton = new Button(ctx);
        loadAllButton.setText("加载全目录");
        loadAllButton.setOnClickListener(new TocLoadAllClickListener(this));
        if (bookId.startsWith("local-")) {
            loadAllButton.setVisibility(View.GONE);
        }
        tabsRow.addView(loadAllButton);
        root.addView(tabsRow);

        // 4. Status View
        statusView = new TextView(ctx);
        statusView.setTextSize(13f);
        statusView.setPadding(24, 8, 24, 8);
        statusView.setTextColor(secTextCol);
        statusView.setVisibility(View.GONE);
        root.addView(statusView);

        // 5. Progress Bar
        progressBar = new ProgressBar(ctx);
        progressBar.setVisibility(View.GONE);
        root.addView(progressBar);

        // 6. RecyclerView
        recyclerView = new RecyclerView(ctx);
        recyclerView.setLayoutManager(new LinearLayoutManager(ctx));
        recyclerView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1.0f
        ));
        adapter = new TocAdapter(this);
        adapter.setColors(colors);
        recyclerView.setAdapter(adapter);
        root.addView(recyclerView);

        setContentView(root);

        // Init search dependencies and controller
        deps = new ContentTocSearchDeps(bookId, db, httpClient);
        searchController = new TocSearchController(
                deps,
                new TocSearchEventsImpl(this, executors.getMainThreadExecutor())
        );
        searchEditText.addTextChangedListener(new TocSearchQueryWatcher(searchController, this));

        // Load data from DB
        reloadTocData();
    }

    public void reloadTocData() {
        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
        }
        executors.getContentDbExecutor().execute(new TocDataLoaderRunnable(
                bookId,
                currentChapterId,
                db,
                executors.getMainThreadExecutor(),
                this
        ));
    }

    public void onDataLoaded(List<TocItemModel> items) {
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }
        if (adapter != null) {
            adapter.setData(items);
            if (mainTabButton != null) {
                mainTabButton.setText("正文 (" + adapter.getMainCount() + ")");
            }
            if (extraTabButton != null) {
                extraTabButton.setText("番外 (" + adapter.getExtraCount() + ")");
            }

            int curPos = adapter.findCurrentPosition();
            if (curPos >= 0 && recyclerView != null) {
                recyclerView.scrollToPosition(curPos);
            }
        }
    }

    public void selectMainTab() {
        if (adapter != null) {
            adapter.setFilter(TocAdapter.FILTER_MAIN);
            statusView.setVisibility(View.GONE);
        }
    }

    public void selectExtraTab() {
        if (adapter != null) {
            adapter.setFilter(TocAdapter.FILTER_EXTRA);
            statusView.setVisibility(View.GONE);
        }
    }

    public void clearSearch() {
        if (searchEditText != null) {
            searchEditText.setText("");
        }
        exitSearchMode();
    }

    public void exitSearchMode() {
        if (adapter != null) {
            adapter.applyCurrentFilter();
        }
        if (statusView != null) {
            statusView.setVisibility(View.GONE);
        }
    }

    public void loadAllPages() {
        if (loadAllButton != null) {
            loadAllButton.setEnabled(false);
            loadAllButton.setText("加载中...");
        }
        TocDataLoaderRunnable refresh = new TocDataLoaderRunnable(
                bookId,
                currentChapterId,
                db,
                executors.getMainThreadExecutor(),
                this
        );
        executors.getContentDbExecutor().execute(new TocLoadAllRunnable(deps, refresh, executors.getContentDbExecutor()));
    }

    public void onSearchUpdated(TocSearchPhase phase, List<TocSearchResult> results) {
        if (phase == null || adapter == null) return;

        if (phase.getKind() == TocSearchPhaseKind.SEARCHING) {
            statusView.setVisibility(View.VISIBLE);
            statusView.setText("正在搜索目录 (" + phase.getLoadedPages() + "/" + phase.getTotalPages() + " 页)...");
        } else if (phase.getKind() == TocSearchPhaseKind.DONE || phase.getKind() == TocSearchPhaseKind.IDLE) {
            statusView.setVisibility(View.VISIBLE);
            if (results == null || results.isEmpty()) {
                statusView.setText("无匹配结果");
            } else if (results.size() >= TocSearchController.TOC_SEARCH_LIMIT) {
                statusView.setText("已显示前 " + results.size() + " 条，请输入更精确的搜索词");
            } else {
                statusView.setText("找到 " + results.size() + " 条结果");
            }
        }

        if (results != null && !results.isEmpty()) {
            List<TocItemModel> models = new ArrayList<TocItemModel>();
            for (int i = 0; i < results.size(); i++) {
                TocSearchResult r = results.get(i);
                if (r != null && r.getEntry() != null) {
                    models.add(new TocItemModel(
                            r.getEntry().getChapterId(),
                            r.getEntry().getTitle(),
                            r.getEntry().getOrderKey(),
                            r.getEntry().isExtra(),
                            currentChapterId.equals(r.getEntry().getChapterId()),
                            false,
                            false
                    ));
                }
            }
            adapter.setSearchResults(models);
        }
    }

    public void onItemClicked(TocItemModel item) {
        if (item != null && navigator != null) {
            navigator.openReader(bookId, item.getChapterId());
            dismiss();
        }
    }

    @Override
    public void dismiss() {
        if (searchController != null) {
            searchController.cancel();
        }
        super.dismiss();
    }
}
