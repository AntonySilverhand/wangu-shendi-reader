package org.wanshu.reader;

import android.webkit.ValueCallback;

/** 首个原生快照已在页面执行后才展示 WebView，避免首帧控件落在系统栏下面。 */
// 原始接口避免 javac 生成 synthetic 泛型桥方法（当前 d8/JDK21 会在该方法上崩溃）。
@SuppressWarnings("rawtypes")
public final class DisplayReadyCallback implements ValueCallback {
  private final MainActivity activity;

  public DisplayReadyCallback(MainActivity activity) { this.activity = activity; }

  @Override
  public void onReceiveValue(Object value) { activity.displayApplied(); }
}
