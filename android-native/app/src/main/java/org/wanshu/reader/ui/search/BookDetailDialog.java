package org.wanshu.reader.ui.search;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.wanshu.reader.core.model.BookInfo;
import org.wanshu.reader.core.model.BookSummary;
import org.wanshu.reader.core.net.SourceHttpClient;
import org.wanshu.reader.core.queue.RequestKey;
import org.wanshu.reader.core.queue.RequestPriority;
import org.wanshu.reader.core.queue.RequestScheduler;
import org.wanshu.reader.core.source.FetchBookInfoAction;
import org.wanshu.reader.data.AppExecutors;
import org.wanshu.reader.ui.Ui;
import org.wanshu.reader.ui.cover.CoverLoader;

/**
 * Details of a search result: cover, author, category, status and intro (read from the book's first TOC
 * page through the request queue), with buttons to start reading or add the book to the shelf.
 */
public class BookDetailDialog extends Dialog implements View.OnClickListener {
    private static final int BG = Color.parseColor("#FAF7F2");
    private static final int TEXT = Color.parseColor("#2C2723");
    private static final int SECONDARY = Color.parseColor("#666666");
    private static final int COVER_WIDTH_DP = 96;
    private static final int COVER_HEIGHT_DP = 128;

    private final BookSummary summary;
    private final BookSearchHost host;
    private final RequestScheduler scheduler;
    private final SourceHttpClient client;
    private final CoverLoader coverLoader;
    private final AppExecutors executors;

    private TextView titleView;
    private TextView authorView;
    private TextView metaView;
    private TextView introView;
    private TextView messageView;
    private ImageView coverView;
    private Button startButton;
    private Button addButton;
    private Button closeButton;

    private BookInfo info;
    private boolean onShelf;
    private boolean adding;
    private long generation;

    public BookDetailDialog(
            Context context,
            BookSummary summary,
            BookSearchHost host,
            RequestScheduler scheduler,
            SourceHttpClient client,
            CoverLoader coverLoader,
            AppExecutors executors
    ) {
        super(context);
        this.summary = summary;
        this.host = host;
        this.scheduler = scheduler;
        this.client = client;
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
        root.setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 16), Ui.dp(ctx, 16), Ui.dp(ctx, 12));

        LinearLayout top = new LinearLayout(ctx);
        top.setOrientation(LinearLayout.HORIZONTAL);

        coverView = new ImageView(ctx);
        coverView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        coverView.setBackgroundColor(Color.parseColor("#E4DFD6"));
        LinearLayout.LayoutParams coverParams = new LinearLayout.LayoutParams(Ui.dp(ctx, COVER_WIDTH_DP), Ui.dp(ctx, COVER_HEIGHT_DP));
        coverParams.setMargins(0, 0, Ui.dp(ctx, 14), 0);
        top.addView(coverView, coverParams);

        LinearLayout texts = new LinearLayout(ctx);
        texts.setOrientation(LinearLayout.VERTICAL);
        titleView = new TextView(ctx);
        titleView.setText(summary.getTitle());
        titleView.setTextSize(20f);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setTextColor(TEXT);
        texts.addView(titleView);
        authorView = new TextView(ctx);
        authorView.setTextSize(14f);
        authorView.setTextColor(SECONDARY);
        authorView.setPadding(0, Ui.dp(ctx, 6), 0, 0);
        texts.addView(authorView);
        metaView = new TextView(ctx);
        metaView.setTextSize(13f);
        metaView.setTextColor(SECONDARY);
        metaView.setPadding(0, Ui.dp(ctx, 4), 0, 0);
        texts.addView(metaView);
        top.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
        root.addView(top);

        ScrollView introScroll = new ScrollView(ctx);
        introView = new TextView(ctx);
        introView.setTextSize(15f);
        introView.setTextColor(TEXT);
        introView.setLineSpacing(0f, 1.3f);
        introView.setPadding(0, Ui.dp(ctx, 14), 0, Ui.dp(ctx, 8));
        introScroll.addView(introView);
        root.addView(introScroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));

        messageView = new TextView(ctx);
        messageView.setTextSize(13f);
        messageView.setTextColor(Color.parseColor("#4A6B82"));
        messageView.setPadding(0, Ui.dp(ctx, 4), 0, Ui.dp(ctx, 4));
        root.addView(messageView);

        LinearLayout buttons = new LinearLayout(ctx);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.END);
        startButton = Ui.button(ctx, "开始阅读", this);
        addButton = Ui.button(ctx, "加入书架", this);
        closeButton = Ui.button(ctx, "关闭", this);
        buttons.addView(startButton);
        buttons.addView(addButton);
        buttons.addView(closeButton);
        root.addView(buttons);

        setContentView(root);

        renderTexts();
        renderShelfButton();

        coverLoader.load(coverView, summary.getId(), Ui.dp(ctx, COVER_WIDTH_DP), Ui.dp(ctx, COVER_HEIGHT_DP));
        host.loadShelfBooks(new BookDetailShelfCheckCallback(this, summary.getId()));
        loadInfo();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Window window = getWindow();
        if (window != null) {
            int width = Math.min(getContext().getResources().getDisplayMetrics().widthPixels - Ui.dp(getContext(), 24), Ui.dp(getContext(), 560));
            int height = Math.min(Ui.dp(getContext(), 460), getContext().getResources().getDisplayMetrics().heightPixels - Ui.dp(getContext(), 48));
            window.setLayout(width, height);
        }
    }

    private void loadInfo() {
        introView.setText("正在读取简介…");
        scheduler.enqueue(
                RequestKey.bookInfo(summary.getId()),
                RequestPriority.HIGH,
                this,
                new FetchBookInfoAction(summary.getId(), client),
                new BookInfoCallback(this, generation)
        );
    }

    @Override
    public void dismiss() {
        generation++;
        scheduler.cancelAllForSubscriber(this);
        super.dismiss();
    }

    /** Called from background threads; hops to the main thread. */
    public void postToUi(Runnable task) {
        executors.getMainThreadExecutor().execute(task);
    }

    /** Main thread. */
    public void applyInfo(long infoGeneration, BookInfo loaded, Throwable error) {
        if (!isShowing() || infoGeneration != generation) {
            return;
        }
        if (error != null) {
            introView.setText(summary.getIntro() != null ? summary.getIntro() : "简介暂时无法读取（" + BookSearchDialog.describeError(error) + "）");
            return;
        }
        info = loaded;
        renderTexts();
    }

    /** Main thread. */
    public void applyShelfState(boolean nowOnShelf, String message) {
        if (!isShowing()) {
            return;
        }
        onShelf = nowOnShelf;
        adding = false;
        messageView.setText(message != null ? message : "");
        renderShelfButton();
    }

    private String bestTitle() {
        return info != null && !info.getTitle().isEmpty() ? info.getTitle() : summary.getTitle();
    }

    private String bestAuthor() {
        if (info != null && !info.getAuthor().isEmpty()) {
            return info.getAuthor();
        }
        return summary.getAuthor() != null ? summary.getAuthor() : "";
    }

    private void renderTexts() {
        titleView.setText(bestTitle());
        String author = bestAuthor();
        authorView.setText(author.isEmpty() ? "作者未知" : "作者：" + author);
        String category = info != null && info.getCategory() != null ? info.getCategory() : summary.getCategory();
        String status = info != null ? info.getStatus() : null;
        String latest = info != null && info.getLatestChapter() != null ? info.getLatestChapter() : summary.getLatestChapter();
        metaView.setText(BookSearchDialog.joinMeta(category, status, latest));
        String intro = info != null ? info.getIntro() : null;
        if (intro == null || intro.isEmpty()) {
            intro = summary.getIntro();
        }
        if (info != null && (intro == null || intro.isEmpty())) {
            introView.setText("源站没有提供简介。");
        } else if (intro != null && !intro.isEmpty()) {
            introView.setText(intro);
        }
    }

    private void renderShelfButton() {
        addButton.setEnabled(!onShelf && !adding);
        addButton.setText(onShelf ? "已在书架" : adding ? "加入中…" : "加入书架");
    }

    @Override
    public void onClick(View v) {
        if (v == closeButton) {
            dismiss();
        } else if (v == addButton) {
            if (!onShelf && !adding) {
                adding = true;
                renderShelfButton();
                host.addBook(summary.getId(), bestTitle(), bestAuthor(), new BookDetailAddCallback(this));
            }
        } else if (v == startButton) {
            host.startReading(summary.getId(), bestTitle(), bestAuthor(), info != null ? info.getFirstChapterId() : null);
            dismiss();
        }
    }
}
