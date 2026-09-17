package org.wanshu.reader.ui.download;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;
import org.wanshu.reader.core.download.DownloadRange;
import org.wanshu.reader.download.DownloadCoordinator;
import org.wanshu.reader.download.DownloadProgress;
import org.wanshu.reader.ui.theme.ThemeColors;

public class DownloadsDialog extends Dialog {
    private final String bookId;
    private final String currentChapterId;
    private final DownloadCoordinator coordinator;
    private final ThemeColors colors;
    private final DownloadDialogProgressListener progressListener;

    private DownloadRange selectedRange = DownloadRange.NEXT_50;

    private ProgressBar progressBar;
    private TextView overallProgressView;
    private TextView detailProgressView;
    private TextView currentTaskView;
    private TextView statusBadgeView;

    private Button rangeBtn50;
    private Button rangeBtn100;
    private Button rangeBtn300;
    private Button rangeBtnAll;

    public DownloadsDialog(
            Context context,
            String bookId,
            String currentChapterId,
            DownloadCoordinator coordinator,
            ThemeColors colors
    ) {
        super(context);
        this.bookId = bookId != null ? bookId : "";
        this.currentChapterId = currentChapterId != null ? currentChapterId : "";
        this.coordinator = coordinator;
        this.colors = colors;
        this.progressListener = new DownloadDialogProgressListener(this);
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
        titleView.setText("离线下载管理");
        titleView.setTextSize(18f);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setTextColor(textCol);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        headerBar.addView(titleView, titleParams);

        Button closeBtn = new Button(ctx);
        closeBtn.setText("✕");
        closeBtn.setOnClickListener(new DownloadCloseClickListener(this));
        headerBar.addView(closeBtn);
        root.addView(headerBar);

        // 2. Scrollable content
        ScrollView scroll = new ScrollView(ctx);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1.0f
        ));

        LinearLayout body = new LinearLayout(ctx);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(32, 24, 32, 32);

        // Section A: 范围选择
        TextView sectionRange = new TextView(ctx);
        sectionRange.setText("选择下载范围");
        sectionRange.setTextSize(16f);
        sectionRange.setTypeface(null, Typeface.BOLD);
        sectionRange.setTextColor(textCol);
        sectionRange.setPadding(0, 8, 0, 12);
        body.addView(sectionRange);

        LinearLayout rangeRow = new LinearLayout(ctx);
        rangeRow.setOrientation(LinearLayout.HORIZONTAL);
        rangeRow.setPadding(0, 0, 0, 20);

        rangeBtn50 = createRangeButton(ctx, "后续 50 章", DownloadRange.NEXT_50);
        rangeBtn100 = createRangeButton(ctx, "后续 100 章", DownloadRange.NEXT_100);
        rangeBtn300 = createRangeButton(ctx, "后续 300 章", DownloadRange.NEXT_300);
        rangeBtnAll = createRangeButton(ctx, "全书", DownloadRange.ENTIRE_BOOK);

        rangeRow.addView(rangeBtn50, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        rangeRow.addView(rangeBtn100, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        rangeRow.addView(rangeBtn300, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        rangeRow.addView(rangeBtnAll, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        body.addView(rangeRow);

        updateRangeButtons();

        // Section B: 控制操作
        LinearLayout actionRow = new LinearLayout(ctx);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setPadding(0, 0, 0, 24);

        Button startBtn = new Button(ctx);
        startBtn.setText("开始下载");
        startBtn.setOnClickListener(new DownloadStartClickListener(this));
        actionRow.addView(startBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        Button pauseBtn = new Button(ctx);
        pauseBtn.setText("暂停");
        pauseBtn.setOnClickListener(new DownloadPauseClickListener(this));
        actionRow.addView(pauseBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        Button resumeBtn = new Button(ctx);
        resumeBtn.setText("继续");
        resumeBtn.setOnClickListener(new DownloadResumeClickListener(this));
        actionRow.addView(resumeBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        Button cancelBtn = new Button(ctx);
        cancelBtn.setText("取消");
        cancelBtn.setOnClickListener(new DownloadCancelClickListener(this));
        actionRow.addView(cancelBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        body.addView(actionRow);

        // Section C: 进度展示
        TextView sectionProgress = new TextView(ctx);
        sectionProgress.setText("下载进度状态");
        sectionProgress.setTextSize(16f);
        sectionProgress.setTypeface(null, Typeface.BOLD);
        sectionProgress.setTextColor(textCol);
        sectionProgress.setPadding(0, 8, 0, 12);
        body.addView(sectionProgress);

        progressBar = new ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        progressBar.setPadding(0, 8, 0, 16);
        body.addView(progressBar);

        overallProgressView = new TextView(ctx);
        overallProgressView.setText("正在统计进度...");
        overallProgressView.setTextSize(15f);
        overallProgressView.setTypeface(null, Typeface.BOLD);
        overallProgressView.setTextColor(textCol);
        overallProgressView.setPadding(0, 0, 0, 8);
        body.addView(overallProgressView);

        detailProgressView = new TextView(ctx);
        detailProgressView.setText("已完成: 0 章 | 部分: 0 章 | 待下载: 0 章 | 失败: 0 章");
        detailProgressView.setTextSize(13f);
        detailProgressView.setTextColor(secTextCol);
        detailProgressView.setPadding(0, 0, 0, 8);
        body.addView(detailProgressView);

        currentTaskView = new TextView(ctx);
        currentTaskView.setText("当前: 空闲");
        currentTaskView.setTextSize(13f);
        currentTaskView.setTextColor(secTextCol);
        currentTaskView.setPadding(0, 0, 0, 8);
        body.addView(currentTaskView);

        statusBadgeView = new TextView(ctx);
        statusBadgeView.setText("状态: 就绪");
        statusBadgeView.setTextSize(13f);
        statusBadgeView.setTextColor(Color.parseColor("#2E7D32"));
        statusBadgeView.setPadding(0, 0, 0, 20);
        body.addView(statusBadgeView);

        Button retryBtn = new Button(ctx);
        retryBtn.setText("重试所有失败章节");
        retryBtn.setOnClickListener(new DownloadRetryClickListener(this));
        body.addView(retryBtn);

        TextView note = new TextView(ctx);
        note.setText("\n说明：\n• 所有下载任务仅在应用处于前台时运行，离开应用或锁屏即刻暂停，确保零后台服务与能耗。\n• 已下载完整章节零重复网络请求；清理缓存仅移除远程正文，不损坏个人书签与进度。");
        note.setTextSize(12f);
        note.setLineSpacing(0, 1.4f);
        note.setTextColor(secTextCol);
        note.setPadding(0, 16, 0, 16);
        body.addView(note);

        scroll.addView(body);
        root.addView(scroll);

        setContentView(root);

        if (coordinator != null) {
            coordinator.addListener(progressListener);
            coordinator.notifyProgress();
        }
    }

    private Button createRangeButton(Context ctx, String text, DownloadRange range) {
        Button btn = new Button(ctx);
        btn.setText(text);
        btn.setTextSize(12f);
        btn.setPadding(4, 4, 4, 4);
        btn.setOnClickListener(new DownloadRangeClickListener(this, range));
        return btn;
    }

    public void onRangeSelected(DownloadRange range) {
        if (range != null) {
            this.selectedRange = range;
            updateRangeButtons();
        }
    }

    private void updateRangeButtons() {
        highlightButton(rangeBtn50, selectedRange == DownloadRange.NEXT_50);
        highlightButton(rangeBtn100, selectedRange == DownloadRange.NEXT_100);
        highlightButton(rangeBtn300, selectedRange == DownloadRange.NEXT_300);
        highlightButton(rangeBtnAll, selectedRange == DownloadRange.ENTIRE_BOOK);
    }

    private void highlightButton(Button btn, boolean selected) {
        if (btn == null) return;
        if (selected) {
            btn.setTypeface(null, Typeface.BOLD);
        } else {
            btn.setTypeface(null, Typeface.NORMAL);
        }
    }

    public void startDownload() {
        if (coordinator != null) {
            coordinator.startManualDownload(bookId, currentChapterId, selectedRange);
            Toast.makeText(getContext(), "已添加下载任务: " + selectedRange.getLabel(), Toast.LENGTH_SHORT).show();
        }
    }

    public void pauseDownload() {
        if (coordinator != null) {
            coordinator.pauseDownload(bookId);
            Toast.makeText(getContext(), "下载已暂停", Toast.LENGTH_SHORT).show();
        }
    }

    public void resumeDownload() {
        if (coordinator != null) {
            coordinator.resumeDownload(bookId);
            Toast.makeText(getContext(), "下载已继续", Toast.LENGTH_SHORT).show();
        }
    }

    public void cancelDownload() {
        if (coordinator != null) {
            coordinator.cancelDownload(bookId);
            Toast.makeText(getContext(), "下载任务已取消", Toast.LENGTH_SHORT).show();
        }
    }

    public void retryFailed() {
        if (coordinator != null) {
            coordinator.retryFailed(bookId);
            Toast.makeText(getContext(), "重试失败任务已提交", Toast.LENGTH_SHORT).show();
        }
    }

    public void onProgressUpdate(DownloadProgress progress) {
        if (progress == null) return;

        if (progressBar != null) {
            progressBar.setProgress((int) progress.getPercent());
        }

        if (overallProgressView != null) {
            String overall = String.format(
                    Locale.getDefault(),
                    "已下载 %d / %d 章 (%.1f%%)",
                    progress.getCompletedChapters(),
                    progress.getTotalChapters(),
                    progress.getPercent()
            );
            if (progress.getSessionNewCompleted() > 0) {
                overall += " · 本次新增 " + progress.getSessionNewCompleted() + " 章";
            }
            overallProgressView.setText(overall);
        }

        if (detailProgressView != null) {
            detailProgressView.setText(String.format(
                    Locale.getDefault(),
                    "已完成: %d 章 | 部分: %d 章 | 待下载: %d 章 | 失败: %d 章",
                    progress.getCompletedChapters(),
                    progress.getPartialChapters(),
                    progress.getPendingChapters(),
                    progress.getFailedChapters()
            ));
        }

        if (currentTaskView != null) {
            if (progress.isRunning() && !progress.getCurrentChapterTitle().isEmpty()) {
                currentTaskView.setText("正在下载: " + progress.getCurrentChapterTitle());
            } else {
                currentTaskView.setText("当前: 空闲");
            }
        }

        if (statusBadgeView != null) {
            if (progress.isPaused()) {
                String reason = progress.getPauseReason();
                statusBadgeView.setText("状态: 已暂停" + (reason.isEmpty() ? "" : " (" + reason + ")"));
                statusBadgeView.setTextColor(Color.parseColor("#E65100"));
            } else if (progress.isRunning()) {
                statusBadgeView.setText("状态: 正在下载中...");
                statusBadgeView.setTextColor(Color.parseColor("#1976D2"));
            } else if (progress.getPendingChapters() == 0 && progress.getFailedChapters() == 0) {
                statusBadgeView.setText("状态: 全部下载已完成");
                statusBadgeView.setTextColor(Color.parseColor("#2E7D32"));
            } else {
                statusBadgeView.setText("状态: 就绪");
                statusBadgeView.setTextColor(Color.parseColor("#424242"));
            }
        }
    }

    @Override
    protected void onStop() {
        if (coordinator != null) {
            coordinator.removeListener(progressListener);
        }
        super.onStop();
    }
}
