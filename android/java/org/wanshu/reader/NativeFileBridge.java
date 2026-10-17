package org.wanshu.reader;

import android.webkit.JavascriptInterface;

/**
 * JS → 原生“保存文件”接口（顶层类）。WebView 不处理 &lt;a download&gt;/blob: 下载，导出个人数据与 TXT
 * 改走这里：分块写入应用缓存目录的临时文件，再由系统“保存到…”（SAF）让用户选位置。
 * 只接受 UTF-8 文本、文件名经过清理、总大小有上限；页面固定在 https://reader.local/。
 */
public final class NativeFileBridge {
  private final MainActivity activity;

  public NativeFileBridge(MainActivity activity) {
    this.activity = activity;
  }

  @JavascriptInterface
  public int beginSave(String fileName, String mimeType) {
    return activity.beginSave(fileName, mimeType);
  }

  @JavascriptInterface
  public boolean appendSave(int token, String chunk) {
    return activity.appendSave(token, chunk);
  }

  @JavascriptInterface
  public void finishSave(int token) {
    activity.finishSave(token);
  }

  @JavascriptInterface
  public void cancelSave(int token) {
    activity.cancelSave(token);
  }
}
