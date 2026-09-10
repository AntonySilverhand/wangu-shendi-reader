package org.wanshu.reader;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** 单 Activity：本地资源服务器 + 全屏 WebView。 */
public class MainActivity extends Activity {
  private WebView webView;
  private LocalServer server;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    try {
      server = new LocalServer(getAssets());
      server.start();
    } catch (Exception e) {
      throw new RuntimeException("本地服务器启动失败", e);
    }

    webView = new WebView(this);
    WebSettings s = webView.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setDatabaseEnabled(true);
    s.setSupportZoom(false);
    s.setBuiltInZoomControls(false);
    s.setMediaPlaybackRequiresUserGesture(false);
    s.setCacheMode(WebSettings.LOAD_DEFAULT);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
    }
    webView.setBackgroundColor(Color.parseColor("#101113"));
    webView.setWebViewClient(new WebViewClient());
    setContentView(webView, new ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      getWindow().setStatusBarColor(Color.parseColor("#161719"));
      getWindow().setNavigationBarColor(Color.parseColor("#161719"));
    }
    webView.loadUrl("http://127.0.0.1:" + server.getPort() + "/");
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
    if (server != null) server.stop();
    if (webView != null) webView.destroy();
    super.onDestroy();
  }
}
