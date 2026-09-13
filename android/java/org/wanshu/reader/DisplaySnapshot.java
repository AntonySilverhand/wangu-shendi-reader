package org.wanshu.reader;

/**
 * 屏幕/系统栏安全区域快照（不可变数据容器，顶层类，禁止内部类/lambda）。
 */
public final class DisplaySnapshot {
  public final int version;
  public final float top;
  public final float bottom;
  public final float left;
  public final float right;
  public final float ime;
  public final float density;
  public final boolean immersive;

  public DisplaySnapshot(int version, float top, float bottom, float left, float right,
      float ime, float density, boolean immersive) {
    this.version = version;
    this.top = top;
    this.bottom = bottom;
    this.left = left;
    this.right = right;
    this.ime = ime;
    this.density = density;
    this.immersive = immersive;
  }

  public String toJson() {
    return "{\"version\":" + version
        + ",\"top\":" + top
        + ",\"bottom\":" + bottom
        + ",\"left\":" + left
        + ",\"right\":" + right
        + ",\"ime\":" + ime
        + ",\"density\":" + density
        + ",\"immersive\":" + (immersive ? "true" : "false")
        + "}";
  }
}
