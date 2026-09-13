package org.wanshu.reader;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Insets;
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

  public static DisplaySnapshot computeSnapshot(Activity activity, WindowInsets insets,
      boolean isImmersive) {
    float density = activity.getResources().getDisplayMetrics().density;
    if (density <= 0f) {
      density = 1.0f;
    }

    if (insets == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      insets = activity.getWindow().getDecorView().getRootWindowInsets();
    }

    int top = 0;
    int bottom = 0;
    int left = 0;
    int right = 0;
    int ime = 0;

    if (insets != null) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
        Insets cutout = insets.getInsets(WindowInsets.Type.displayCutout());
        Insets imeInsets = insets.getInsets(WindowInsets.Type.ime());
        top = Math.max(bars.top, cutout.top);
        bottom = Math.max(bars.bottom, cutout.bottom);
        left = Math.max(bars.left, cutout.left);
        right = Math.max(bars.right, cutout.right);
        ime = imeInsets.bottom;
      } else {
        top = insets.getSystemWindowInsetTop();
        bottom = insets.getSystemWindowInsetBottom();
        left = insets.getSystemWindowInsetLeft();
        right = insets.getSystemWindowInsetRight();
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

    float topCss = Math.round((top / density) * 100.0f) / 100.0f;
    float bottomCss = Math.round((bottom / density) * 100.0f) / 100.0f;
    float leftCss = Math.round((left / density) * 100.0f) / 100.0f;
    float rightCss = Math.round((right / density) * 100.0f) / 100.0f;
    float imeCss = Math.round((ime / density) * 100.0f) / 100.0f;

    return new DisplaySnapshot(1, topCss, bottomCss, leftCss, rightCss, imeCss, density, isImmersive);
  }
}
