package org.wanshu.reader;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 单 Activity：固定 https://reader.local/ origin，跨更新保持 Web Storage / IndexedDB。
 * 系统栏由网页安全区处理；IME 由 DisplayLayout 唯一负责缩小 WebView。
 * configChanges 不重载页面；系统重建时恢复路由，段落位置由个人存储恢复。
 */
public class MainActivity extends Activity {
  private static final String APP_ORIGIN = "https://reader.local/";
  private static final String STATE_HASH = "reader.hash";
  private static final String PREFS_NAME = "org.wanshu.reader.display_prefs";
  private static final String KEY_THEME_NAME = "theme_name";
  private static final String KEY_THEME_COLOR = "theme_color";
  private static final int REQUEST_FILE = 41;
  private static final int REQUEST_SAVE = 42;
  /** 导出文件上限（整本 TXT 约 32MB 字节） */
  private static final long MAX_SAVE_BYTES = 128L * 1024 * 1024;

  private WebView webView;
  private DisplayLayout displayHost;
  private WindowInsets lastInsets;
  // Bridge getter runs off the UI thread: only read immutable, safely published data.
  private volatile String snapshotJson = "{\"version\":1,\"top\":0,\"bottom\":0,\"left\":0,\"right\":0,\"ime\":0}";
  private final DisplayRefreshRunnable refreshDisplay = new DisplayRefreshRunnable(this, false);
  private String currentThemeName = "dark";
  private String currentThemeColor = "#161719";
  private boolean isImmersive;
  private boolean pageReady;
  private boolean destroyed;
  /** 网页 &lt;input type=file&gt; 等待中的回调；必须回传（含取消时的 null），否则之后再点不会弹出 */
  private ValueCallback<Uri[]> fileCallback;
  /** 正在等网页处理返回键，避免连按时重复询问 */
  private boolean backPending;
  /** 导出（保存文件）会话：JS 桥线程写入、UI 线程弹窗、后台线程复制，统一用 saveLock 保护 */
  private final Object saveLock = new Object();
  private int saveSeq;
  private int saveToken;
  private File saveFile;
  private OutputStream saveOut;
  private long saveBytes;
  private String saveName;
  private String saveMime;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
    String defaultTheme = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
        == Configuration.UI_MODE_NIGHT_YES ? "dark" : "light";
    currentThemeName = prefs.getString(KEY_THEME_NAME, defaultTheme);
    currentThemeColor = DisplayHelper.themeColor(currentThemeName);
    if (currentThemeColor == null) {
      currentThemeName = "dark";
      currentThemeColor = "#161719";
    }
    DisplayHelper.configureWindow(this);
    DisplayHelper.applyTheme(this, null, currentThemeName, currentThemeColor, false);

    webView = new WebView(this);
    webView.setVisibility(View.INVISIBLE);
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
    webView.setBackgroundColor(Color.parseColor(currentThemeColor));
    webView.setWebViewClient(new AssetClient(new AssetProvider(getAssets()), new SourceProxy()));
    webView.setWebChromeClient(new ReaderChromeClient(this));
    webView.addJavascriptInterface(new NativeDisplayBridge(this), "_nativeDisplayBridge");
    webView.addJavascriptInterface(new NativeFileBridge(this), "_nativeFileBridge");
    // 仅 DEBUG=1 APK 开放 adb DevTools。
    WebView.setWebContentsDebuggingEnabled(
        (getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0);
    displayHost = new DisplayLayout(this);
    displayHost.setOnApplyWindowInsetsListener(new EdgeInsetsListener(this));
    displayHost.addView(webView, new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    setContentView(displayHost, new ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    recomputeAndDispatchInsets(null);
    String hash = savedInstanceState == null ? null : savedInstanceState.getString(STATE_HASH);
    webView.loadUrl(hash != null && hash.startsWith("#") ? APP_ORIGIN + hash : APP_ORIGIN);
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    lastInsets = null;
    scheduleDisplayRefresh();
    if (displayHost != null) displayHost.requestApplyInsets();
  }

  public void onInsetsChanged(WindowInsets insets) {
    lastInsets = insets;
    recomputeAndDispatchInsets(insets);
  }

  public void scheduleDisplayRefresh() {
    if (destroyed || displayHost == null) return;
    displayHost.removeCallbacks(refreshDisplay);
    displayHost.post(refreshDisplay);
  }

  public int displayHostHeight() { return displayHost == null ? 0 : displayHost.getHeight(); }
  public int displayWebHeight() { return webView == null ? 0 : webView.getHeight(); }

  public int resizeForKeyboard(int keyboardTop, boolean visible) {
    if (displayHost == null || webView == null) return 0;
    int[] location = new int[2];
    displayHost.getLocationOnScreen(location);
    int overlap = Math.min(displayHost.getHeight(), DisplayGeometry.keyboardOverlap(
        location[1] + displayHost.getHeight(), keyboardTop, visible));
    FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) webView.getLayoutParams();
    if (lp.bottomMargin != overlap) {
      lp.bottomMargin = overlap;
      webView.setLayoutParams(lp);
    }
    return overlap;
  }

  public void recomputeAndDispatchInsets(WindowInsets insets) {
    if (destroyed) return;
    String next = DisplayHelper.computeSnapshot(this, insets != null ? insets : lastInsets, isImmersive).toJson();
    if (next.equals(snapshotJson)) return;
    snapshotJson = next;
    dispatchSnapshot();
  }

  private void dispatchSnapshot() {
    if (!destroyed && pageReady && webView != null) {
      webView.evaluateJavascript("window.__onNativeDisplayChange && window.__onNativeDisplayChange("
          + snapshotJson + ");", webView.getVisibility() == View.VISIBLE ? null : new DisplayReadyCallback(this));
    }
  }

  public void displayApplied() {
    if (!destroyed && webView != null) webView.setVisibility(View.VISIBLE);
  }

  public String getLatestSnapshotJson() { return snapshotJson; }

  public void onSetTheme(String themeName, String themeColorHex) {
    runOnUiThread(new SetThemeRunnable(this, themeName, themeColorHex));
  }

  public void applyThemeInternal(String themeName, String themeColorHex) {
    String canonical = DisplayHelper.themeColor(themeName);
    if (destroyed || canonical == null || !canonical.equalsIgnoreCase(themeColorHex)) return;
    currentThemeName = themeName;
    currentThemeColor = canonical;
    SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
    editor.putString(KEY_THEME_NAME, currentThemeName);
    editor.putString(KEY_THEME_COLOR, currentThemeColor);
    editor.apply();
    // 改图标颜色不能再次 hide()，否则会打断用户临时唤出的系统栏。
    DisplayHelper.applyTheme(this, webView, currentThemeName, currentThemeColor, false);
    scheduleDisplayRefresh();
  }

  public void onSetImmersive(boolean immersive) {
    runOnUiThread(new SetImmersiveRunnable(this, immersive));
  }

  public void applyImmersiveInternal(boolean immersive) {
    if (destroyed || isImmersive == immersive) return;
    isImmersive = immersive;
    DisplayHelper.applyImmersive(this, immersive);
    scheduleDisplayRefresh();
  }

  public void onPageReady() {
    runOnUiThread(new DisplayRefreshRunnable(this, true));
  }

  public void pageReadyInternal() {
    if (destroyed) return;
    pageReady = true;
    recomputeAndDispatchInsets(null);
    dispatchSnapshot(); // 握手重放最新快照，不能依赖首次 inset 事件的时序。
    if (displayHost != null) displayHost.requestApplyInsets();
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
    if (displayHost != null) displayHost.requestApplyInsets();
    scheduleDisplayRefresh();
  }

  /**
   * 打开系统文件选择器（导入 TXT / ZIP）。不按 MIME 过滤：各文件管理器给 .txt 的类型不一，
   * 网页侧按扩展名与内容识别。
   */
  public boolean openFileChooser(ValueCallback<Uri[]> callback) {
    if (fileCallback != null) fileCallback.onReceiveValue(null);
    fileCallback = callback;
    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.setType("*/*");
    try {
      startActivityForResult(Intent.createChooser(intent, "选择 TXT 或 ZIP 文件"), REQUEST_FILE);
      return true;
    } catch (ActivityNotFoundException e) {
      fileCallback = null;
      callback.onReceiveValue(null);
      return true;
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == REQUEST_SAVE) {
      int token;
      synchronized (saveLock) {
        token = saveToken;
      }
      Uri target = (resultCode == RESULT_OK && data != null) ? data.getData() : null;
      if (target == null) {
        cancelSave(token);
        notifySaved(token, "cancelled", "");
      } else {
        new Thread(new SaveCopyRunnable(this, token, target), "reader-save").start();
      }
      return;
    }
    if (requestCode == REQUEST_FILE) {
      ValueCallback<Uri[]> callback = fileCallback;
      fileCallback = null;
      if (callback != null) callback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
      return;
    }
    super.onActivityResult(requestCode, resultCode, data);
  }

  /**
   * 系统返回键：先交给网页（关浮层 → 章内搜索 → 目录 → 回书架），网页不处理（已在书架）才交给系统。
   * 不再按 WebView 历史逐章回退——每次翻章都会压一条历史，读过几十章后就回不到书架。
   */
  @Override
  public void onBackPressed() {
    if (webView == null || !pageReady) {
      super.onBackPressed();
      return;
    }
    if (backPending) return;
    backPending = true;
    webView.evaluateJavascript(
        "(function(){try{return window.__onNativeBack?window.__onNativeBack()===true:false}catch(e){return false}})()",
        new BackResultCallback(this));
  }

  void onWebBackResult(boolean handled) {
    backPending = false;
    if (!handled && !destroyed) super.onBackPressed();
  }

  /* ----------------------------- 保持常亮 ----------------------------- */

  public void onSetKeepScreenOn(boolean on) {
    runOnUiThread(new KeepScreenOnRunnable(this, on));
  }

  void applyKeepScreenOn(boolean on) {
    if (destroyed) return;
    if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  }

  /* --------------------------- 导出（保存文件） --------------------------- */

  /** 文件名去掉路径分隔符与控制字符，限长；空时给默认名 */
  private static String safeFileName(String name) {
    String n = name == null ? "" : name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
    if (n.length() > 80) n = n.substring(n.length() - 80);
    return n.isEmpty() ? "export.txt" : n;
  }

  /** 关闭并删除当前临时文件（调用方持有 saveLock） */
  private void discardSaveLocked() {
    if (saveOut != null) {
      try {
        saveOut.close();
      } catch (IOException ignored) {
      }
      saveOut = null;
    }
    if (saveFile != null) {
      if (!saveFile.delete()) saveFile.deleteOnExit();
      saveFile = null;
    }
    saveBytes = 0;
  }

  int beginSave(String fileName, String mimeType) {
    synchronized (saveLock) {
      discardSaveLocked();
      File dir = new File(getCacheDir(), "exports");
      if (!dir.isDirectory() && !dir.mkdirs()) return -1;
      saveSeq++;
      File file = new File(dir, "export-" + saveSeq + ".part");
      try {
        saveOut = new FileOutputStream(file);
      } catch (IOException e) {
        return -1;
      }
      saveFile = file;
      saveToken = saveSeq;
      saveName = safeFileName(fileName);
      saveMime = "application/json".equals(mimeType) ? "application/json" : "text/plain";
      return saveToken;
    }
  }

  boolean appendSave(int token, String chunk) {
    synchronized (saveLock) {
      if (token != saveToken || saveOut == null || chunk == null) return false;
      byte[] bytes = chunk.getBytes(StandardCharsets.UTF_8);
      if (saveBytes + bytes.length > MAX_SAVE_BYTES) {
        discardSaveLocked();
        return false;
      }
      try {
        saveOut.write(bytes);
        saveBytes += bytes.length;
        return true;
      } catch (IOException e) {
        discardSaveLocked();
        return false;
      }
    }
  }

  void finishSave(int token) {
    synchronized (saveLock) {
      if (token != saveToken || saveOut == null) {
        notifySaved(token, "error", "保存会话已失效，请重试");
        return;
      }
      try {
        saveOut.close();
        saveOut = null;
      } catch (IOException e) {
        discardSaveLocked();
        notifySaved(token, "error", "写入临时文件失败");
        return;
      }
    }
    runOnUiThread(new SavePickerRunnable(this, token));
  }

  void cancelSave(int token) {
    synchronized (saveLock) {
      if (token == saveToken) discardSaveLocked();
    }
  }

  void launchSavePicker(int token) {
    String name;
    String mime;
    synchronized (saveLock) {
      if (destroyed || token != saveToken || saveFile == null) return;
      name = saveName;
      mime = saveMime;
    }
    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.setType(mime);
    intent.putExtra(Intent.EXTRA_TITLE, name);
    try {
      startActivityForResult(intent, REQUEST_SAVE);
    } catch (ActivityNotFoundException e) {
      cancelSave(token);
      notifySaved(token, "error", "系统没有可用的文件保存器");
    }
  }

  /** 后台线程：临时文件 → 用户选择的位置 */
  void copySaveTo(int token, Uri target) {
    File file;
    synchronized (saveLock) {
      if (token != saveToken || saveFile == null) return;
      file = saveFile;
    }
    String status = "saved";
    String message = "";
    InputStream in = null;
    OutputStream out = null;
    try {
      in = new FileInputStream(file);
      out = getContentResolver().openOutputStream(target);
      if (out == null) throw new IOException("无法打开目标文件");
      byte[] buf = new byte[64 * 1024];
      int n;
      while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
      out.flush();
    } catch (Exception e) {
      status = "error";
      message = "保存失败：" + e.getMessage();
    } finally {
      try {
        if (in != null) in.close();
      } catch (IOException ignored) {
      }
      try {
        if (out != null) out.close();
      } catch (IOException e) {
        status = "error";
        message = "保存失败：" + e.getMessage();
      }
    }
    cancelSave(token);
    notifySaved(token, status, message);
  }

  void notifySaved(int token, String status, String message) {
    runOnUiThread(new SaveResultRunnable(this, token, status, message));
  }

  void deliverSaveResult(int token, String status, String message) {
    if (destroyed || webView == null) return;
    webView.evaluateJavascript("window.__onNativeSaved&&window.__onNativeSaved(" + token + ","
        + JSONObject.quote(status) + "," + JSONObject.quote(message) + ")", null);
  }

  @Override
  protected void onDestroy() {
    destroyed = true;
    if (fileCallback != null) {
      fileCallback.onReceiveValue(null);
      fileCallback = null;
    }
    synchronized (saveLock) {
      discardSaveLocked();
    }
    if (displayHost != null) displayHost.removeCallbacks(refreshDisplay);
    if (webView != null) {
      webView.removeJavascriptInterface("_nativeDisplayBridge");
      webView.removeJavascriptInterface("_nativeFileBridge");
      webView.destroy();
    }
    super.onDestroy();
  }
}
