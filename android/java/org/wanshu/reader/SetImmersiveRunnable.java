package org.wanshu.reader;

/**
 * 在 UI 线程切换沉浸式全屏状态（顶层类，禁止内部类/lambda）。
 */
public final class SetImmersiveRunnable implements Runnable {
  private final MainActivity activity;
  private final boolean immersive;

  public SetImmersiveRunnable(MainActivity activity, boolean immersive) {
    this.activity = activity;
    this.immersive = immersive;
  }

  @Override
  public void run() {
    activity.applyImmersiveInternal(immersive);
  }
}
