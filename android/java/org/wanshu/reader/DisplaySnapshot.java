package org.wanshu.reader;

/** 不可变显示快照。可见性取实际 WindowInsets，不把请求沉浸等同于已隐藏。 */
public final class DisplaySnapshot {
  public final float top, bottom, left, right, ime, density;
  public final boolean immersive, statusVisible, navigationVisible;
  public final int keyboardOverlap, hostHeight, webHeight, windowHeight, appearance;
  public final String backgroundColor;

  public DisplaySnapshot(float top, float bottom, float left, float right, float ime,
      float density, boolean immersive, boolean statusVisible, boolean navigationVisible,
      int keyboardOverlap, int hostHeight, int webHeight, int windowHeight, int appearance, String backgroundColor) {
    this.top = top;
    this.bottom = bottom;
    this.left = left;
    this.right = right;
    this.ime = ime;
    this.density = density;
    this.immersive = immersive;
    this.statusVisible = statusVisible;
    this.navigationVisible = navigationVisible;
    this.keyboardOverlap = keyboardOverlap;
    this.hostHeight = hostHeight;
    this.webHeight = webHeight;
    this.windowHeight = windowHeight;
    this.appearance = appearance;
    this.backgroundColor = backgroundColor;
  }

  public String toJson() {
    return "{\"version\":1,\"top\":" + top + ",\"bottom\":" + bottom
        + ",\"left\":" + left + ",\"right\":" + right + ",\"ime\":" + ime
        + ",\"density\":" + density + ",\"immersive\":" + immersive
        + ",\"statusVisible\":" + statusVisible + ",\"navigationVisible\":" + navigationVisible
        + ",\"keyboardOverlap\":" + keyboardOverlap + ",\"hostHeight\":" + hostHeight
        + ",\"webHeight\":" + webHeight + ",\"windowHeight\":" + windowHeight
        + ",\"appearance\":" + appearance + ",\"backgroundColor\":\"" + backgroundColor + "\"}";
  }
}
