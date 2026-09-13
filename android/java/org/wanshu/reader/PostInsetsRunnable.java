package org.wanshu.reader;

import android.webkit.WebView;

/**
 * 在 UI 线程向 WebView 派发 safe-area insets 变更脚本（顶层类，禁止内部类/lambda）。
 */
public final class PostInsetsRunnable implements Runnable {
  private final WebView webView;
  private final String script;

  public PostInsetsRunnable(WebView webView, String script) {
    this.webView = webView;
    this.script = script;
  }

  @Override
  public void run() {
    if (webView != null) {
      webView.evaluateJavascript(script, null);
    }
  }
}
