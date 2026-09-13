package org.wanshu.reader;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.webkit.WebSettings;
import android.webkit.WebView;

/**
 * 单 Activity：全屏 WebView，支持原生 Edge-to-Edge 与阅读主题同步。
 *
 * 页面 origin 固定为 https://reader.local/（由 AssetClient 拦截所有请求并从
 * APK assets 提供资源），不依赖随机端口。Web Storage / IndexedDB 按 origin 隔离，
 * 固定 origin 后阅读进度、书签、历史、设置、正文缓存、本地导入书均跨启动稳定。
 *
 * 生命周期：
 *  - configChanges 已声明（Manifest），旋转/折叠不重建 Activity，WebView 不重载；
 *  - 进程被杀/系统重建时，hash（路由 = 章节）经 savedInstanceState 恢复，
 *    阅读位置由固定 origin 下的 localStorage 恢复。
 */
public class MainActivity extends Activity {
  private static final String APP_ORIGIN = "https://reader.local/";
  private static final String STATE_HASH = "reader.hash";
  private static final String PREFS_NAME = "org.wanshu.reader.display_prefs";
  private static final String KEY_THEME_NAME = "theme_name";
  private static final String KEY_THEME_COLOR = "theme_color";

  private WebView webView;
  private DisplaySnapshot latestSnapshot;
  private String currentThemeName = "dark";
  private String currentThemeColor = "#161719";
  private boolean isImmersive = false;
  private boolean pageReady = false;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
    currentThemeName = prefs.getString(KEY_THEME_NAME, "dark");
    currentThemeColor = prefs.getString(KEY_THEME_COLOR, "#161719");

    DisplayHelper.configureWindow(this);
    DisplayHelper.applyTheme(this, null, currentThemeName, currentThemeColor, isImmersive);

    webView = new WebView(this);
    WebSettings s = webView.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setDatabaseEnabled(true);
    s.setSupportZoom(false);
    s.setBuiltInZoomControls(false);
    s.setMediaPlaybackRequiresUserGesture(true);
    s.setAllowFileAccess(false);
    s.setAllowContentAccess(false);
    s.setCacheMode(WebSettings.LOAD_DEFAULT);
    s.setJavaScriptCanOpenWindowsAutomatically(false);

    int initialColor = Color.parseColor("#101113");
    try {
      if (currentThemeColor != null && currentThemeColor.startsWith("#")) {
        initialColor = Color.parseColor(currentThemeColor);
      }
    } catch (Exception ignored) {}
    webView.setBackgroundColor(initialColor);

    webView.setWebViewClient(new AssetClient(new AssetProvider(getAssets()), new SourceProxy()));
    webView.addJavascriptInterface(new NativeDisplayBridge(this), "_nativeDisplayBridge");
    webView.setOnApplyWindowInsetsListener(new EdgeInsetsListener(this));

    // 仅 DEBUG=1 构建开放本机 adb DevTools；发布 APK 不开放调试接口。
    WebView.setWebContentsDebuggingEnabled(
        (getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0);
    setContentView(webView, new ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    latestSnapshot = DisplayHelper.computeSnapshot(this, null, isImmersive);

    String hash = null;
    if (savedInstanceState != null) {
      hash = savedInstanceState.getString(STATE_HASH);
    }
    webView.loadUrl(hash != null && hash.startsWith("#") ? APP_ORIGIN + hash : APP_ORIGIN);
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    recomputeAndDispatchInsets(null);
  }

  public void onInsetsChanged(WindowInsets insets) {
    recomputeAndDispatchInsets(insets);
  }

  public void recomputeAndDispatchInsets(WindowInsets insets) {
    latestSnapshot = DisplayHelper.computeSnapshot(this, insets, isImmersive);
    if (pageReady && webView != null) {
      String script = "window.__onNativeDisplayChange && window.__onNativeDisplayChange("
          + latestSnapshot.toJson() + ");";
      webView.post(new PostInsetsRunnable(webView, script));
    }
  }

  public String getLatestSnapshotJson() {
    if (latestSnapshot == null) {
      latestSnapshot = DisplayHelper.computeSnapshot(this, null, isImmersive);
    }
    return latestSnapshot.toJson();
  }

  public void onSetTheme(String themeName, String themeColorHex) {
    runOnUiThread(new SetThemeRunnable(this, themeName, themeColorHex));
  }

  public void applyThemeInternal(String themeName, String themeColorHex) {
    this.currentThemeName = themeName != null ? themeName : "dark";
    this.currentThemeColor = themeColorHex != null ? themeColorHex : "#161719";
    SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
    editor.putString(KEY_THEME_NAME, this.currentThemeName);
    editor.putString(KEY_THEME_COLOR, this.currentThemeColor);
    editor.apply();
    DisplayHelper.applyTheme(this, webView, this.currentThemeName, this.currentThemeColor, isImmersive);
  }

  public void onSetImmersive(boolean immersive) {
    runOnUiThread(new SetImmersiveRunnable(this, immersive));
  }

  public void applyImmersiveInternal(boolean immersive) {
    this.isImmersive = immersive;
    DisplayHelper.applyImmersive(this, immersive);
    recomputeAndDispatchInsets(null);
  }

  public void onPageReady() {
    this.pageReady = true;
    recomputeAndDispatchInsets(null);
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    if (webView == null) return;
    String url = webView.getUrl();
    if (url != null) {
      int idx = url.indexOf('#');
      if (idx >= 0) outState.putString(STATE_HASH, url.substring(idx));
    }
  }

  @Override
  protected void onPause() {
    super.onPause();
    if (webView != null) webView.onPause();
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (webView != null) webView.onResume();
    DisplayHelper.applyTheme(this, webView, currentThemeName, currentThemeColor, isImmersive);
    recomputeAndDispatchInsets(null);
  }

  @Override
  public void onBackPressed() {
    if (webView != null && webView.canGoBack()) {
      webView.goBack();
    } else {
      super.onBackPressed();
    }
  }

  @Override
  protected void onDestroy() {
    if (webView != null) webView.destroy();
    super.onDestroy();
  }
}
