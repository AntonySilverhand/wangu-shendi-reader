package org.wanshu.reader;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;

/**
 * 单 Activity：全屏 WebView。
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

  private WebView webView;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
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
    webView.setBackgroundColor(Color.parseColor("#101113"));
    webView.setWebViewClient(new AssetClient(new AssetProvider(getAssets()), new SourceProxy()));
    // 仅 DEBUG=1 构建开放本机 adb DevTools；发布 APK 不开放调试接口。
    WebView.setWebContentsDebuggingEnabled(
        (getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0);
    setContentView(webView, new ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      getWindow().setStatusBarColor(Color.parseColor("#161719"));
      getWindow().setNavigationBarColor(Color.parseColor("#161719"));
    }

    String hash = null;
    if (savedInstanceState != null) {
      hash = savedInstanceState.getString(STATE_HASH);
    }
    webView.loadUrl(hash != null && hash.startsWith("#") ? APP_ORIGIN + hash : APP_ORIGIN);
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
