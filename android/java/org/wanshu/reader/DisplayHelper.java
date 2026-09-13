package org.wanshu.reader;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.DisplayCutout;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.WebView;

/**
 * 窗口 Edge-to-Edge 配置与主题/图标外观同步（顶层类，禁止内部类/lambda）。
 */
public final class DisplayHelper {
  private DisplayHelper() {}

  public static String themeColor(String theme) {
    if ("light".equals(theme)) return "#f6f5f2";
    if ("dark".equals(theme)) return "#161719";
    if ("black".equals(theme)) return "#000000";
    if ("eink".equals(theme)) return "#ededed";
    if ("paper".equals(theme)) return "#f5edda";
    return null;
  }

  public static void configureWindow(Activity activity) {
    Window window = activity.getWindow();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      window.setDecorFitsSystemWindows(false);
    } else {
      View decor = window.getDecorView();
      int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
          | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
          | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
      decor.setSystemUiVisibility(flags);
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      WindowManager.LayoutParams lp = window.getAttributes();
      lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
      window.setAttributes(lp);
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      window.setStatusBarColor(Color.TRANSPARENT);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        window.setNavigationBarColor(Color.TRANSPARENT);
      }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      window.setNavigationBarContrastEnforced(false);
      window.setStatusBarContrastEnforced(false);
    }
  }

  public static void applyTheme(Activity activity, WebView webView,
      String themeName, String themeColorHex, boolean isImmersive) {
    int color = Color.parseColor("#101113");
    try {
      if (themeColorHex != null && themeColorHex.startsWith("#")) {
        color = Color.parseColor(themeColorHex);
      }
    } catch (Exception ignored) {}

    Window window = activity.getWindow();
    window.getDecorView().setBackgroundColor(color);
    if (webView != null) {
      webView.setBackgroundColor(color);
    }

    boolean darkIcons = !"dark".equals(themeName) && !"black".equals(themeName);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      WindowInsetsController controller = window.getInsetsController();
      if (controller != null) {
        int statusAppearance = darkIcons ? WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS : 0;
        int navAppearance = darkIcons ? WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS : 0;
        controller.setSystemBarsAppearance(statusAppearance, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
        controller.setSystemBarsAppearance(navAppearance, WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
      }
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      View decor = window.getDecorView();
      int flags = decor.getSystemUiVisibility();
      if (darkIcons) {
        flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
      } else {
        flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
      }
      decor.setSystemUiVisibility(flags);
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      View decor = window.getDecorView();
      int flags = decor.getSystemUiVisibility();
      if (darkIcons) {
        flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
      } else {
        flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
      }
      decor.setSystemUiVisibility(flags);
      // API 24-25 不支持浅色导航栏图标，若浅色主题则导航栏保持深色，保证按钮可见
      if (darkIcons) {
        window.setNavigationBarColor(Color.parseColor("#161719"));
      } else {
        window.setNavigationBarColor(color);
      }
    }

    if (isImmersive) {
      applyImmersive(activity, true);
    }
  }

  public static void applyImmersive(Activity activity, boolean immersive) {
    Window window = activity.getWindow();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      WindowInsetsController controller = window.getInsetsController();
      if (controller != null) {
        if (immersive) {
          controller.hide(WindowInsets.Type.systemBars());
          controller.setSystemBarsBehavior(
              WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        } else {
          controller.show(WindowInsets.Type.systemBars());
        }
      }
    } else {
      View decor = window.getDecorView();
      int flags = decor.getSystemUiVisibility();
      int immersiveFlags = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
          | View.SYSTEM_UI_FLAG_FULLSCREEN
          | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
      if (immersive) {
        flags |= immersiveFlags;
      } else {
        flags &= ~immersiveFlags;
      }
      decor.setSystemUiVisibility(flags);
    }
  }

  public static DisplaySnapshot computeSnapshot(MainActivity activity, WindowInsets insets,
      boolean isImmersive) {
    float density = activity.getResources().getDisplayMetrics().density;
    if (density <= 0f) density = 1f;
    View decor = activity.getWindow().getDecorView();
    if (insets == null) insets = decor.getRootWindowInsets();
    Rect visible = new Rect();
    decor.getWindowVisibleDisplayFrame(visible);
    int[] decorLocation = new int[2];
    decor.getLocationOnScreen(decorLocation);
    int windowHeight = decor.getHeight();
    int windowBottom = decorLocation[1] + windowHeight;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      Rect bounds = activity.getWindowManager().getCurrentWindowMetrics().getBounds();
      windowBottom = bounds.bottom;
      windowHeight = bounds.height();
    }

    int top = 0, bottom = 0, left = 0, right = 0, ime = 0;
    boolean statusVisible = false, navigationVisible = false;
    if (insets != null) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
        Insets cutout = insets.getInsets(WindowInsets.Type.displayCutout());
        top = Math.max(bars.top, cutout.top);
        bottom = Math.max(bars.bottom, cutout.bottom);
        left = Math.max(bars.left, cutout.left);
        right = Math.max(bars.right, cutout.right);
        ime = insets.getInsets(WindowInsets.Type.ime()).bottom;
        statusVisible = insets.isVisible(WindowInsets.Type.statusBars());
        navigationVisible = insets.isVisible(WindowInsets.Type.navigationBars());
      } else {
        top = insets.getSystemWindowInsetTop();
        left = insets.getSystemWindowInsetLeft();
        right = insets.getSystemWindowInsetRight();
        int systemBottom = insets.getSystemWindowInsetBottom();
        ime = DisplayGeometry.legacyIme(systemBottom, insets.getStableInsetBottom(),
            Math.max(0, windowBottom - visible.bottom), density);
        bottom = DisplayGeometry.legacyNavigation(systemBottom, insets.getStableInsetBottom(), ime);
        statusVisible = top > 0;
        navigationVisible = bottom > 0 || left > 0 || right > 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
          DisplayCutout cutout = insets.getDisplayCutout();
          if (cutout != null) {
            top = Math.max(top, cutout.getSafeInsetTop());
            bottom = Math.max(bottom, cutout.getSafeInsetBottom());
            left = Math.max(left, cutout.getSafeInsetLeft());
            right = Math.max(right, cutout.getSafeInsetRight());
          }
        }
      }
    }
    // 现代 Android 使用窗口坐标；旧版使用可见矩形，兼容系统已经 adjustResize 的情况。
    int keyboardTop = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        ? windowBottom - ime : visible.bottom;
    int overlap = activity.resizeForKeyboard(keyboardTop, ime > 0);
    // WebView 已截止到键盘上沿，导航栏在键盘下面，不能再添加一次底部安全区。
    if (ime > 0) bottom = 0;
    // 设备验证读取实际窗口 appearance，不只检查 JS 是否发送过主题命令。
    int appearance = 0;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      WindowInsetsController c = activity.getWindow().getInsetsController();
      int flags = c == null ? 0 : c.getSystemBarsAppearance();
      if ((flags & WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS) != 0) appearance |= 1;
      if ((flags & WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS) != 0) appearance |= 2;
    } else {
      int flags = decor.getSystemUiVisibility();
      if ((flags & View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR) != 0) appearance |= 1;
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
          && (flags & View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR) != 0) appearance |= 2;
    }
    int background = decor.getBackground() instanceof ColorDrawable
        ? ((ColorDrawable) decor.getBackground()).getColor() : Color.BLACK;
    String backgroundHex = String.format(java.util.Locale.ROOT, "#%06x", background & 0xffffff);
    return new DisplaySnapshot(top / density, bottom / density, left / density, right / density,
        ime / density, density, isImmersive, statusVisible, navigationVisible, overlap,
        activity.displayHostHeight(), activity.displayWebHeight(), windowHeight, appearance, backgroundHex);
  }
}
