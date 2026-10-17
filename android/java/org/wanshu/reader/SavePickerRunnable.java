package org.wanshu.reader;

/** 顶层 Runnable：在 UI 线程打开系统“保存到…”对话框。 */
final class SavePickerRunnable implements Runnable {
  private final MainActivity activity;
  private final int token;

  SavePickerRunnable(MainActivity activity, int token) {
    this.activity = activity;
    this.token = token;
  }

  @Override
  public void run() {
    activity.launchSavePicker(token);
  }
}
