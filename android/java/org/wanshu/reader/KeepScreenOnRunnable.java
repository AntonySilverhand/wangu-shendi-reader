package org.wanshu.reader;

/** 顶层 Runnable：在 UI 线程切换 FLAG_KEEP_SCREEN_ON（WebView 没有 Screen Wake Lock API）。 */
final class KeepScreenOnRunnable implements Runnable {
  private final MainActivity activity;
  private final boolean on;

  KeepScreenOnRunnable(MainActivity activity, boolean on) {
    this.activity = activity;
    this.on = on;
  }

  @Override
  public void run() {
    activity.applyKeepScreenOn(on);
  }
}
