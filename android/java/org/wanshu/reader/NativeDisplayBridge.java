package org.wanshu.reader;

import android.webkit.JavascriptInterface;

/**
 * JS 与原生窗口/显示通信接口（白名单受限，顶层类，禁止内部类/lambda）。
 */
public final class NativeDisplayBridge {
  private final MainActivity activity;

  public NativeDisplayBridge(MainActivity activity) {
    this.activity = activity;
  }

  @JavascriptInterface
  public String getDisplaySnapshot() {
    return activity.getLatestSnapshotJson();
  }

  @JavascriptInterface
  public void setTheme(String themeName, String themeColorHex) {
    activity.onSetTheme(themeName, themeColorHex);
  }

  @JavascriptInterface
  public void setImmersive(boolean immersive) {
    activity.onSetImmersive(immersive);
  }

  @JavascriptInterface
  public void onPageReady() {
    activity.onPageReady();
  }
}
