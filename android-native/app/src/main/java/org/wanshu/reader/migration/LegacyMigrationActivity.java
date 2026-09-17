package org.wanshu.reader.migration;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.wanshu.reader.AppContainer;
import org.wanshu.reader.ReaderApplication;
import org.wanshu.reader.core.text.TextBlockBuilder;

public class LegacyMigrationActivity extends Activity implements LegacyMigrationListener {
    private AppContainer container;
    private LegacyMigrationEngine engine;
    private WebView webView;

    private TextView statusTextView;
    private TextView detailTextView;
    private ProgressBar progressBar;
    private Button skipButton;
    private Button retryButton;
    private Button doneButton;

    private boolean isFinished = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ReaderApplication app = (ReaderApplication) getApplication();
        container = app.getContainer();
        engine = new LegacyMigrationEngine(
                container.getPersonalDb(),
                container.getContentDb(),
                new TextBlockBuilder()
        );

        setupViews();
        startMigrationEngine();
    }

    private void setupViews() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.parseColor("#F6F5F2"));
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackgroundColor(Color.WHITE);
        int cardPad = (int) (24 * getResources().getDisplayMetrics().density);
        card.setPadding(cardPad, cardPad, cardPad, cardPad);

        TextView titleView = new TextView(this);
        titleView.setText("旧版数据迁移");
        titleView.setTextSize(20.0f);
        titleView.setTextColor(Color.parseColor("#222222"));
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        titleLp.bottomMargin = (int) (16 * getResources().getDisplayMetrics().density);
        card.addView(titleView, titleLp);

        statusTextView = new TextView(this);
        statusTextView.setText("正在准备迁移旧版数据...");
        statusTextView.setTextSize(15.0f);
        statusTextView.setTextColor(Color.parseColor("#444444"));
        statusTextView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        statusLp.bottomMargin = (int) (8 * getResources().getDisplayMetrics().density);
        card.addView(statusTextView, statusLp);

        detailTextView = new TextView(this);
        detailTextView.setText("检测到来自老版本《万古神帝》阅读器的本地存储");
        detailTextView.setTextSize(13.0f);
        detailTextView.setTextColor(Color.parseColor("#888888"));
        detailTextView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        detailLp.bottomMargin = (int) (20 * getResources().getDisplayMetrics().density);
        card.addView(detailTextView, detailLp);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(5);
        progressBar.setIndeterminate(false);
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(
                (int) (260 * getResources().getDisplayMetrics().density),
                (int) (8 * getResources().getDisplayMetrics().density)
        );
        progressLp.bottomMargin = (int) (24 * getResources().getDisplayMetrics().density);
        card.addView(progressBar, progressLp);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);

        skipButton = new Button(this);
        skipButton.setText("跳过");
        skipButton.setOnClickListener(new LegacyMigrationSkipClickListener(this));
        btnRow.addView(skipButton);

        retryButton = new Button(this);
        retryButton.setText("重试");
        retryButton.setVisibility(View.GONE);
        retryButton.setOnClickListener(new LegacyMigrationRetryClickListener(this));
        btnRow.addView(retryButton);

        doneButton = new Button(this);
        doneButton.setText("进入阅读器");
        doneButton.setVisibility(View.GONE);
        doneButton.setOnClickListener(new LegacyMigrationFinishClickListener(this));
        btnRow.addView(doneButton);

        card.addView(btnRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        root.addView(card, new LinearLayout.LayoutParams(
                (int) (320 * getResources().getDisplayMetrics().density),
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        // Hidden WebView for bridge execution
        webView = new WebView(this);
        webView.setVisibility(View.GONE);
        root.addView(webView, new LinearLayout.LayoutParams(0, 0));

        setContentView(root);
    }

    private void startMigrationEngine() {
        if (webView == null) return;

        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setAllowFileAccess(false);
        ws.setAllowContentAccess(false);
        ws.setAllowFileAccessFromFileURLs(false);
        ws.setAllowUniversalAccessFromFileURLs(false);
        ws.setDatabaseEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setBlockNetworkLoads(true);

        webView.addJavascriptInterface(new LegacyMigrationBridge(this), "NativeMigration");
        byte[] htmlBytes = loadMigrationHtmlAsset();
        webView.setWebViewClient(new LegacyMigrationClient(htmlBytes));
        webView.loadUrl("https://reader.local/native-migration.html");
    }

    private byte[] loadMigrationHtmlAsset() {
        try {
            InputStream is = getAssets().open("native-migration.html");
            byte[] bytes = is.readAllBytes();
            is.close();
            return bytes;
        } catch (Exception e) {
            return "<html><body>Asset error</body></html>".getBytes(StandardCharsets.UTF_8);
        }
    }

    public void updateStatus(String status, String detail, int progress, int max) {
        if (statusTextView != null && status != null) {
            statusTextView.setText(status);
        }
        if (detailTextView != null && detail != null) {
            detailTextView.setText(detail);
        }
        if (progressBar != null) {
            progressBar.setMax(max);
            progressBar.setProgress(progress);
        }
    }

    public void onMigrationFinished(boolean success, String message) {
        isFinished = true;
        if (webView != null) {
            try {
                webView.removeJavascriptInterface("NativeMigration");
                webView.destroy();
                webView = null;
            } catch (Exception ignored) {
            }
        }

        if (success) {
            if (skipButton != null) skipButton.setVisibility(View.GONE);
            if (retryButton != null) retryButton.setVisibility(View.GONE);
            if (doneButton != null) doneButton.setVisibility(View.VISIBLE);
        } else {
            if (skipButton != null) skipButton.setVisibility(View.VISIBLE);
            if (retryButton != null) retryButton.setVisibility(View.VISIBLE);
            if (doneButton != null) doneButton.setVisibility(View.GONE);
        }
    }

    public void onSkipClicked() {
        LegacyMigrationDetector.setMigrationCompleted(this, true);
        finish();
    }

    public void onRetryClicked() {
        if (retryButton != null) retryButton.setVisibility(View.GONE);
        if (skipButton != null) skipButton.setVisibility(View.VISIBLE);
        updateStatus("正在重新启动迁移...", "", 0, 100);
        if (webView != null) {
            webView.reload();
        } else {
            startMigrationEngine();
        }
    }

    @Override
    public void onHandshake(String protocolVersion) {
        runOnUiThread(new LegacyMigrationStatusRunnable(
                this,
                "旧版数据握手成功",
                "正在读取本地设置与书签...",
                5,
                100
        ));
    }

    @Override
    public void onLocalStorage(String settingsJson, String personalJson, String localBooksJson) {
        container.getExecutors().getContentDbExecutor().execute(new LegacyMigrationLocalStorageRunnable(
                this,
                engine,
                webView,
                settingsJson,
                personalJson,
                localBooksJson
        ));
    }

    @Override
    public void onIndexedDbBatch(
            String runId,
            String storeName,
            String batchJson,
            int batchIndex,
            boolean isLastBatch,
            String checksum
    ) {
        container.getExecutors().getContentDbExecutor().execute(new LegacyMigrationBatchRunnable(
                this,
                engine,
                webView,
                runId,
                storeName,
                batchJson,
                batchIndex,
                isLastBatch,
                checksum
        ));
    }

    @Override
    public void onComplete(String runId, String summaryJson) {
        container.getExecutors().getContentDbExecutor().execute(new LegacyMigrationCompleteRunnable(
                this,
                engine,
                this,
                runId,
                summaryJson
        ));
    }

    @Override
    public void onError(String runId, String errorStage, String errorMessage) {
        container.getExecutors().getContentDbExecutor().execute(new LegacyMigrationErrorRunnable(
                this,
                engine,
                runId,
                errorStage,
                errorMessage
        ));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webView != null) {
            try {
                webView.removeJavascriptInterface("NativeMigration");
                webView.destroy();
                webView = null;
            } catch (Exception ignored) {
            }
        }
    }
}
