package org.wanshu.reader;

/** 生命周期/Bridge 的显示工作统一进入 UI 线程。 */
public final class DisplayRefreshRunnable implements Runnable {
  private final MainActivity activity;
  private final boolean ready;

  public DisplayRefreshRunnable(MainActivity activity, boolean ready) {
    this.activity = activity;
    this.ready = ready;
  }

  @Override
  public void run() {
    if (ready) activity.pageReadyInternal();
    else activity.recomputeAndDispatchInsets(null);
  }
}
