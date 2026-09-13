package org.wanshu.reader;

/** 纯几何计算，独立于 Android，便于 JVM 回归。单位均为原生 px。 */
public final class DisplayGeometry {
  private DisplayGeometry() {}

  public static int legacyIme(int systemBottom, int stableBottom, int obscuredBottom, float density) {
    int threshold = Math.round(100 * density);
    int candidate = Math.max(systemBottom, obscuredBottom);
    return candidate > stableBottom + threshold ? candidate : 0;
  }

  public static int legacyNavigation(int systemBottom, int stableBottom, int ime) {
    return ime > 0 ? Math.max(0, stableBottom) : Math.max(0, systemBottom);
  }

  /** 已被 adjustResize 移走的高度不再扣第二次。 */
  public static int keyboardOverlap(int hostBottom, int keyboardTop, boolean visible) {
    return visible ? Math.max(0, hostBottom - keyboardTop) : 0;
  }
}
