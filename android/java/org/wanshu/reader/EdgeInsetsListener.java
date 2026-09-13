package org.wanshu.reader;

import android.view.View;
import android.view.WindowInsets;

/**
 * 监听原生窗口安全区/系统栏/键盘高度变更（顶层类，禁止内部类/lambda）。
 */
public final class EdgeInsetsListener implements View.OnApplyWindowInsetsListener {
  private final MainActivity activity;

  public EdgeInsetsListener(MainActivity activity) {
    this.activity = activity;
  }

  @Override
  public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
    activity.onInsetsChanged(insets);
    return insets;
  }
}
