package org.wanshu.reader;

/** 顶层 Runnable：在 UI 线程把保存结果回调给网页（window.__onNativeSaved）。 */
final class SaveResultRunnable implements Runnable {
  private final MainActivity activity;
  private final int token;
  private final String status;
  private final String message;

  SaveResultRunnable(MainActivity activity, int token, String status, String message) {
    this.activity = activity;
    this.token = token;
    this.status = status;
    this.message = message;
  }

  @Override
  public void run() {
    activity.deliverSaveResult(token, status, message);
  }
}
