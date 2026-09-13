package org.wanshu.reader;

import android.view.View;
import android.os.Build;
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
    // 单一所有者：系统栏由 CSS 处理，IME 由 host 的 WebView margin 处理。
    // 不再让不同版本的 WebView 二次传播 env() 或自动缩小 viewport。
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return WindowInsets.CONSUMED;
    WindowInsets consumed = insets.consumeSystemWindowInsets().consumeStableInsets();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) consumed = consumed.consumeDisplayCutout();
    return consumed;
  }
}
