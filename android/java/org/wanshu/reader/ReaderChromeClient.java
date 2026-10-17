package org.wanshu.reader;

import android.net.Uri;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;

/**
 * 顶层 WebChromeClient（禁止内部类/lambda）：
 * WebView 默认不处理 &lt;input type="file"&gt;，没有 onShowFileChooser 时点“导入 TXT”毫无反应。
 * 这里把选择交给系统文件选择器（由 MainActivity 发起并回传结果）。
 */
public final class ReaderChromeClient extends WebChromeClient {
  private final MainActivity activity;

  public ReaderChromeClient(MainActivity activity) {
    this.activity = activity;
  }

  @Override
  public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
    return activity.openFileChooser(callback);
  }
}
