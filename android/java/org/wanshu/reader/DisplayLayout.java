package org.wanshu.reader;

import android.widget.FrameLayout;

/** WebView 的唯一 IME resize 所有者；不对系统栏做整窗 padding。 */
public final class DisplayLayout extends FrameLayout {
  private final MainActivity activity;
  private int lastWebHeight = -1;

  public DisplayLayout(MainActivity activity) {
    super(activity);
    this.activity = activity;
    setFitsSystemWindows(false);
  }

  @Override
  protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    super.onLayout(changed, left, top, right, bottom);
    int height = getChildCount() == 0 ? 0 : getChildAt(0).getHeight();
    if (height != lastWebHeight) {
      lastWebHeight = height;
      activity.scheduleDisplayRefresh();
    }
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    activity.scheduleDisplayRefresh();
  }
}
