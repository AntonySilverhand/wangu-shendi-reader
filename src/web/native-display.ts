/**
 * Android 原生窗口安全区与 Edge-to-Edge 状态适配器。
 *
 * 在 Android WebView 中通过 _nativeDisplayBridge 同步安全区像素与系统栏主题；
 * 在浏览器/PWA 中安全回退到 CSS env(safe-area-inset-*)。
 */

export interface NativeDisplaySnapshot {
  version: number;
  top: number;
  bottom: number;
  left: number;
  right: number;
  ime: number;
  density?: number;
  immersive?: boolean;
}

export type InsetsListener = (snapshot: NativeDisplaySnapshot) => void;

interface NativeBridge {
  getDisplaySnapshot(): string;
  setTheme(themeName: string, themeColorHex: string): void;
  setImmersive(immersive: boolean): void;
  onPageReady(): void;
}

declare global {
  interface Window {
    _nativeDisplayBridge?: NativeBridge;
    __onNativeDisplayChange?: (snapshot: string | NativeDisplaySnapshot) => void;
  }
}

let latestSnapshot: NativeDisplaySnapshot | null = null;
let lastImmersive: boolean | undefined;

/** 不让损坏/未来版本的快照污染 CSS；0 是有效安全区，不回退到 env()。 */
export function parseDisplaySnapshot(data: unknown): NativeDisplaySnapshot | null {
  try {
    const s = typeof data === 'string' ? JSON.parse(data) : data;
    if (!s || typeof s !== 'object' || s.version !== 1) return null;
    for (const key of ['top', 'bottom', 'left', 'right', 'ime']) {
      if (typeof s[key] !== 'number' || !Number.isFinite(s[key]) || s[key] < 0 || s[key] > 10000) return null;
    }
    if (s.immersive !== undefined && typeof s.immersive !== 'boolean') return null;
    return { ...s };
  } catch {
    return null;
  }
}
const listeners = new Set<InsetsListener>();

export function isNativeDisplayAvailable(): boolean {
  return typeof window !== 'undefined' && Boolean(window._nativeDisplayBridge);
}

export function applyDisplaySnapshot(snap: NativeDisplaySnapshot): void {
  const valid = parseDisplaySnapshot(snap);
  if (!valid) return;
  snap = valid;
  latestSnapshot = snap;
  if (typeof document === 'undefined') return;
  const root = document.documentElement;
  root.style.setProperty('--native-safe-top', `${snap.top}px`);
  root.style.setProperty('--native-safe-bottom', `${snap.bottom}px`);
  root.style.setProperty('--native-safe-left', `${snap.left}px`);
  root.style.setProperty('--native-safe-right', `${snap.right}px`);
  root.style.setProperty('--native-ime', `${snap.ime}px`);
  if (snap.immersive) {
    root.setAttribute('data-native-immersive', 'true');
  } else {
    root.removeAttribute('data-native-immersive');
  }
}

export function getLatestSnapshot(): NativeDisplaySnapshot | null {
  return latestSnapshot;
}

export function onNativeInsetsChange(listener: InsetsListener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function syncNativeTheme(themeName: string, themeColorHex: string): void {
  if (isNativeDisplayAvailable()) {
    try {
      window._nativeDisplayBridge?.setTheme(themeName, themeColorHex);
    } catch {
      /* 忽略桥接异常 */
    }
  }
}

export function setNativeImmersive(immersive: boolean): void {
  if (isNativeDisplayAvailable()) {
    try {
      if (lastImmersive === immersive) return;
      window._nativeDisplayBridge?.setImmersive(immersive);
      lastImmersive = immersive;
    } catch {
      /* 忽略桥接异常 */
    }
  }
}

export function initNativeDisplay(): void {
  lastImmersive = undefined;
  // 注册原生回调
  window.__onNativeDisplayChange = (data: string | NativeDisplaySnapshot) => {
    try {
      const snap = parseDisplaySnapshot(data);
      if (!snap) return;
      const prev = latestSnapshot;
      const geometryChanged = !prev || prev.top !== snap.top || prev.bottom !== snap.bottom
        || prev.left !== snap.left || prev.right !== snap.right;
      applyDisplaySnapshot(snap);
      // IME/主题/请求态变化不等于安全区变化；避免无意义重锚定和滚动跳动。
      if (!geometryChanged) return;
      for (const fn of listeners) {
        try {
          fn(snap);
        } catch {
          /* 忽略单个监听器错误 */
        }
      }
    } catch {
      /* 忽略解析错误 */
    }
  };

  // 读取初始快照
  if (isNativeDisplayAvailable()) {
    try {
      const raw = window._nativeDisplayBridge?.getDisplaySnapshot();
      if (raw) {
        const snap = parseDisplaySnapshot(raw);
        if (snap) applyDisplaySnapshot(snap);
      }
      window._nativeDisplayBridge?.onPageReady();
    } catch {
      /* 忽略 */
    }
  }
}
