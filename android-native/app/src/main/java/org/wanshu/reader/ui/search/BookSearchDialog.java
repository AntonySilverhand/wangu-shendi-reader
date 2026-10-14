package org.wanshu.reader.ui.search;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.List;
import org.wanshu.reader.core.model.BookSummary;
import org.wanshu.reader.core.net.SourceHttpClient;
import org.wanshu.reader.core.queue.RequestKey;
import org.wanshu.reader.core.queue.RequestPriority;
import org.wanshu.reader.core.queue.RequestScheduler;
import org.wanshu.reader.core.queue.SearchGate;
import org.wanshu.reader.core.source.SearchBooksAction;
import org.wanshu.reader.core.source.SourceParser;
import org.wanshu.reader.data.AppExecutors;
import org.wanshu.reader.ui.Ui;
import org.wanshu.reader.ui.cover.CoverLoader;

/**
 * Searches the source site. The query is only sent when the user submits it; each search is one scheduled
 * request, and a generation counter drops the result of any search that has been replaced meanwhile.
 */
public class BookSearchDialog extends Dialog implements View.OnClickListener, TextView.OnEditorActionListener {
    private static final int BG = Color.parseColor("#FAF7F2");
    private static final int BAR = Color.parseColor("#EFEBE4");
    private static final int TEXT = Color.parseColor("#2C2723");
    private static final int SECONDARY = Color.parseColor("#888888");

    private final BookSearchHost host;
    private final RequestScheduler scheduler;
    private final SourceHttpClient client;
    private final SearchGate gate;
    private final CoverLoader coverLoader;
    private final AppExecutors executors;

    private EditText input;
    private Button searchButton;
    private Button closeButton;
    private TextView statusView;
    private LinearLayout resultsContainer;
    private ScrollView scrollView;

    private long generation;
    private boolean searching;
    private String activeQuery = "";
    private RequestKey activeKey;
    private BookDetailDialog detailDialog;

    public BookSearchDialog(
            Context context,
            BookSearchHost host,
            RequestScheduler scheduler,
            SourceHttpClient client,
            SearchGate gate,
            CoverLoader coverLoader,
            AppExecutors executors
    ) {
        super(context);
        this.host = host;
        this.scheduler = scheduler;
        this.client = client;
        this.gate = gate;
        this.coverLoader = coverLoader;
        this.executors = executors;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        Context ctx = getContext();

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 8), Ui.dp(ctx, 8), Ui.dp(ctx, 8));
        header.setBackgroundColor(BAR);
        TextView title = new TextView(ctx);
        title.setText("搜索书源");
        title.setTextSize(18f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(TEXT);
        header.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
        closeButton = Ui.button(ctx, "关闭", this);
        header.addView(closeButton);
        root.addView(header);

        LinearLayout searchRow = new LinearLayout(ctx);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 8), Ui.dp(ctx, 8), Ui.dp(ctx, 8));
        input = new EditText(ctx);
        input.setHint("书名或作者");
        input.setSingleLine(true);
        input.setTextSize(16f);
        input.setTextColor(TEXT);
        input.setHintTextColor(SECONDARY);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setOnEditorActionListener(this);
        searchRow.addView(input, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
        searchButton = Ui.button(ctx, "搜索", this);
        searchRow.addView(searchButton);
        root.addView(searchRow);

        statusView = new TextView(ctx);
        statusView.setTextSize(13f);
        statusView.setTextColor(SECONDARY);
        statusView.setPadding(Ui.dp(ctx, 16), 0, Ui.dp(ctx, 16), Ui.dp(ctx, 8));
        statusView.setText("输入书名或作者，点「搜索」。结果来自万书阁，源站限制约每 5 秒搜索一次。");
        root.addView(statusView);

        scrollView = new ScrollView(ctx);
        resultsContainer = new LinearLayout(ctx);
        resultsContainer.setOrientation(LinearLayout.VERTICAL);
        resultsContainer.setPadding(Ui.dp(ctx, 12), 0, Ui.dp(ctx, 12), Ui.dp(ctx, 16));
        scrollView.addView(resultsContainer);
        root.addView(scrollView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));

        setContentView(root);
        Window window = getWindow();
        if (window != null) {
            window.setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Window window = getWindow();
        if (window != null) {
            int width = Math.min(getContext().getResources().getDisplayMetrics().widthPixels, Ui.dp(getContext(), 720));
            window.setLayout(width, LinearLayout.LayoutParams.MATCH_PARENT);
        }
    }

    @Override
    public void dismiss() {
        generation++;
        searching = false;
        scheduler.cancelAllForSubscriber(this);
        if (detailDialog != null) {
            detailDialog.dismiss();
            detailDialog = null;
        }
        super.dismiss();
    }

    @Override
    public void onClick(View v) {
        if (v == closeButton) {
            dismiss();
        } else if (v == searchButton) {
            submit();
        } else if (v.getTag() instanceof BookSummary) {
            openDetail((BookSummary) v.getTag());
        }
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN;
        if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE || enter) {
            submit();
            return true;
        }
        return false;
    }

    private void submit() {
        String query = SourceParser.normalizeSearchQuery(input.getText().toString());
        if (query == null) {
            statusView.setText("请输入 1～30 个字的书名或作者。");
            return;
        }
        if (searching && query.equals(activeQuery)) {
            return;
        }
        if (activeKey != null) {
            scheduler.cancelSubscriber(this, activeKey);
        }
        generation++;
        activeQuery = query;
        activeKey = RequestKey.search(query);
        searching = true;
        resultsContainer.removeAllViews();
        statusView.setText("搜索中…（源站限制约每 5 秒搜索一次，可能需要稍候）");
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        }
        scheduler.enqueue(
                activeKey,
                RequestPriority.HIGH,
                this,
                new SearchBooksAction(client, gate, query),
                new BookSearchCallback(this, generation)
        );
    }

    /** Called from the scheduler thread; hops to the main thread. */
    public void postToUi(Runnable task) {
        executors.getMainThreadExecutor().execute(task);
    }

    /** Main thread. Results of replaced or closed searches are dropped. */
    public void applyResult(long resultGeneration, List<BookSummary> results, Throwable error) {
        if (!isShowing() || resultGeneration != generation) {
            return;
        }
        searching = false;
        resultsContainer.removeAllViews();
        if (error != null) {
            statusView.setText(describeError(error));
            return;
        }
        if (results == null || results.isEmpty()) {
            statusView.setText("没有找到相关书籍，换个关键词试试。");
            return;
        }
        statusView.setText("找到 " + results.size() + " 本" + (results.size() >= SourceParser.MAX_SEARCH_RESULTS ? "（最多显示 " + SourceParser.MAX_SEARCH_RESULTS + " 本）" : ""));
        Context ctx = getContext();
        for (int i = 0; i < results.size(); i++) {
            resultsContainer.addView(buildRow(ctx, results.get(i)));
        }
        scrollView.scrollTo(0, 0);
    }

    private View buildRow(Context ctx, BookSummary book) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 12), Ui.dp(ctx, 14), Ui.dp(ctx, 12));
        row.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, Ui.dp(ctx, 4), 0, Ui.dp(ctx, 4));
        row.setLayoutParams(params);
        row.setTag(book);
        row.setOnClickListener(this);
        row.setClickable(true);

        TextView title = new TextView(ctx);
        title.setText(book.getTitle());
        title.setTextSize(17f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(TEXT);
        row.addView(title);

        String meta = joinMeta(book.getAuthor(), book.getCategory(), book.getLatestChapter());
        if (!meta.isEmpty()) {
            TextView metaView = new TextView(ctx);
            metaView.setText(meta);
            metaView.setTextSize(13f);
            metaView.setTextColor(SECONDARY);
            metaView.setSingleLine(true);
            metaView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            metaView.setPadding(0, Ui.dp(ctx, 4), 0, 0);
            row.addView(metaView);
        }
        if (book.getIntro() != null && !book.getIntro().isEmpty()) {
            TextView intro = new TextView(ctx);
            intro.setText(book.getIntro());
            intro.setTextSize(13f);
            intro.setTextColor(SECONDARY);
            intro.setMaxLines(2);
            intro.setEllipsize(android.text.TextUtils.TruncateAt.END);
            intro.setPadding(0, Ui.dp(ctx, 4), 0, 0);
            row.addView(intro);
        }
        return row;
    }

    static String joinMeta(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i] != null && !parts[i].isEmpty()) {
                if (sb.length() > 0) {
                    sb.append(" · ");
                }
                sb.append(parts[i]);
            }
        }
        return sb.toString();
    }

    static String describeError(Throwable error) {
        Throwable t = error;
        while (t != null) {
            if (t instanceof UnknownHostException || t instanceof ConnectException
                    || t instanceof SocketTimeoutException || t instanceof NoRouteToHostException) {
                return "无法连接书源，请检查网络后重试。";
            }
            t = t.getCause();
        }
        String message = error.getMessage();
        if (message != null && message.contains("unexpected search page")) {
            return "源站返回了无法识别的页面，请稍后再试。";
        }
        return "搜索失败：" + (message != null ? message : "未知错误");
    }

    private void openDetail(BookSummary book) {
        if (detailDialog != null) {
            detailDialog.dismiss();
        }
        detailDialog = new BookDetailDialog(getContext(), book, host, scheduler, client, coverLoader, executors);
        detailDialog.show();
    }
}
