package org.wanshu.reader.migration;

import android.webkit.WebView;

public class LegacyMigrationAckRunnable implements Runnable {
    private final WebView webView;
    private final String javascript;

    public LegacyMigrationAckRunnable(WebView webView, String javascript) {
        this.webView = webView;
        this.javascript = javascript;
    }

    @Override
    public void run() {
        if (webView != null && javascript != null) {
            try {
                webView.evaluateJavascript(javascript, null);
            } catch (Exception ignored) {
            }
        }
    }
}
