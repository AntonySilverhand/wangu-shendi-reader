package org.wanshu.reader;

/**
 * 在 UI 线程应用主题颜色和系统栏图标外观（顶层类，禁止内部类/lambda）。
 */
public final class SetThemeRunnable implements Runnable {
  private final MainActivity activity;
  private final String themeName;
  private final String themeColorHex;

  public SetThemeRunnable(MainActivity activity, String themeName, String themeColorHex) {
    this.activity = activity;
    this.themeName = themeName;
    this.themeColorHex = themeColorHex;
  }

  @Override
  public void run() {
    activity.applyThemeInternal(themeName, themeColorHex);
  }
}
