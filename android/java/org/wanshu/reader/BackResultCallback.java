package org.wanshu.reader;

import android.webkit.ValueCallback;

/** 顶层回调：网页 __onNativeBack() 的返回值（"true" = 网页已处理返回键）。 */
// 原始接口避免 javac 生成 synthetic 泛型桥方法（当前 d8/JDK21 会在该方法上崩溃），同 DisplayReadyCallback。
@SuppressWarnings("rawtypes")
final class BackResultCallback implements ValueCallback {
  private final MainActivity activity;

  BackResultCallback(MainActivity activity) {
    this.activity = activity;
  }

  @Override
  public void onReceiveValue(Object value) {
    activity.onWebBackResult("true".equals(value));
  }
}
