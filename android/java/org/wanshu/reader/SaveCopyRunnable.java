package org.wanshu.reader;

import android.net.Uri;

/** 顶层 Runnable：后台线程把临时文件复制到用户选择的位置（大 TXT 不能在 UI 线程写）。 */
final class SaveCopyRunnable implements Runnable {
  private final MainActivity activity;
  private final int token;
  private final Uri target;

  SaveCopyRunnable(MainActivity activity, int token, Uri target) {
    this.activity = activity;
    this.token = token;
    this.target = target;
  }

  @Override
  public void run() {
    activity.copySaveTo(token, target);
  }
}
