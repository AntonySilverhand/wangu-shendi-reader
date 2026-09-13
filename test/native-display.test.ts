import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest';
import {
  initNativeDisplay,
  parseDisplaySnapshot,
  applyDisplaySnapshot,
  getLatestSnapshot,
  onNativeInsetsChange,
  syncNativeTheme,
  setNativeImmersive,
  isNativeDisplayAvailable,
  type NativeDisplaySnapshot,
} from '../src/web/native-display.ts';

describe('Android Native Display Adapter & Inset Contract', () => {
  let styleProps: Map<string, string>;
  let attrs: Map<string, string>;
  let mockWindow: Record<string, unknown>;

  beforeEach(() => {
    styleProps = new Map();
    attrs = new Map();

    const mockDoc = {
      documentElement: {
        style: {
          setProperty: (k: string, v: string) => styleProps.set(k, v),
          getPropertyValue: (k: string) => styleProps.get(k) ?? '',
          removeProperty: (k: string) => styleProps.delete(k),
        },
        setAttribute: (k: string, v: string) => attrs.set(k, v),
        getAttribute: (k: string) => attrs.get(k) ?? null,
        removeAttribute: (k: string) => attrs.delete(k),
      },
    };

    mockWindow = {};

    vi.stubGlobal('document', mockDoc);
    vi.stubGlobal('window', mockWindow);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('在无原生桥接环境下优雅回退，不抛出异常', () => {
    expect(isNativeDisplayAvailable()).toBe(false);
    expect(() => syncNativeTheme('light', '#fff')).not.toThrow();
    expect(() => setNativeImmersive(true)).not.toThrow();
  });

  it('应用安全区快照并注入 CSS 变量', () => {
    const snap: NativeDisplaySnapshot = {
      version: 1,
      top: 32,
      bottom: 24,
      left: 0,
      right: 0,
      ime: 0,
      density: 2.75,
      immersive: false,
    };
    applyDisplaySnapshot(snap);

    expect(getLatestSnapshot()).toEqual(snap);
    expect(styleProps.get('--native-safe-top')).toBe('32px');
    expect(styleProps.get('--native-safe-bottom')).toBe('24px');
    expect(styleProps.get('--native-safe-left')).toBe('0px');
    expect(styleProps.get('--native-safe-right')).toBe('0px');
    expect(styleProps.get('--native-ime')).toBe('0px');
    expect(attrs.get('data-native-immersive')).toBeUndefined();
  });

  it('正确支持非对称异形屏/横屏刘海安全区', () => {
    const snap: NativeDisplaySnapshot = {
      version: 1,
      top: 0,
      bottom: 16,
      left: 48,
      right: 0,
      ime: 0,
      density: 3.0,
      immersive: false,
    };
    applyDisplaySnapshot(snap);

    expect(styleProps.get('--native-safe-left')).toBe('48px');
    expect(styleProps.get('--native-safe-top')).toBe('0px');
    expect(styleProps.get('--native-safe-bottom')).toBe('16px');
  });

  it('沉浸模式切换 data-native-immersive 属性', () => {
    applyDisplaySnapshot({
      version: 1,
      top: 0,
      bottom: 0,
      left: 0,
      right: 0,
      ime: 0,
      immersive: true,
    });
    expect(attrs.get('data-native-immersive')).toBe('true');

    applyDisplaySnapshot({
      version: 1,
      top: 24,
      bottom: 24,
      left: 0,
      right: 0,
      ime: 0,
      immersive: false,
    });
    expect(attrs.get('data-native-immersive')).toBeUndefined();
  });

  it('拒绝损坏或未知版本的快照', () => {
    const valid = { version: 1, top: 0, bottom: 24, left: 0, right: 0, ime: 0 };
    expect(parseDisplaySnapshot(JSON.stringify(valid))).toEqual(valid);
    for (const bad of [null, '{', { ...valid, version: 2 }, { ...valid, left: -1 },
      { ...valid, top: Infinity }, { ...valid, ime: '300' }, { ...valid, bottom: NaN }]) {
      expect(parseDisplaySnapshot(bad)).toBeNull();
    }
  });

  it('相同安全区或仅键盘变化不重复触发阅读重锚定', () => {
    initNativeDisplay();
    const fn = vi.fn();
    const unsub = onNativeInsetsChange(fn);
    const emit = mockWindow.__onNativeDisplayChange as (data: unknown) => void;
    const snap = { version: 1, top: 31, bottom: 21, left: 0, right: 0, ime: 0 };
    emit(snap);
    expect(fn).toHaveBeenCalledTimes(1);
    emit({ ...snap });
    emit({ ...snap, ime: 300 });
    emit({ ...snap, version: 9 });
    expect(fn).toHaveBeenCalledTimes(1);
    expect(styleProps.get('--native-ime')).toBe('300px');
    unsub();
  });

  it('原生 Bridge 注入时完成初始化握手并接收事件', () => {
    const mockBridge = {
      getDisplaySnapshot: vi.fn(() =>
        JSON.stringify({
          version: 1,
          top: 36,
          bottom: 28,
          left: 0,
          right: 0,
          ime: 0,
          density: 2.0,
          immersive: false,
        }),
      ),
      setTheme: vi.fn(),
      setImmersive: vi.fn(),
      onPageReady: vi.fn(),
    };

    mockWindow._nativeDisplayBridge = mockBridge;
    expect(isNativeDisplayAvailable()).toBe(true);

    initNativeDisplay();
    expect(mockBridge.getDisplaySnapshot).toHaveBeenCalledTimes(1);
    expect(mockBridge.onPageReady).toHaveBeenCalledTimes(1);
    expect(styleProps.get('--native-safe-top')).toBe('36px');

    // 监听快照变更
    let notified: NativeDisplaySnapshot | null = null;
    const unsub = onNativeInsetsChange((s) => {
      notified = s;
    });

    // 模拟原生推送新快照
    const onNativeDisplayChange = mockWindow.__onNativeDisplayChange as (data: unknown) => void;
    expect(typeof onNativeDisplayChange).toBe('function');
    onNativeDisplayChange({
      version: 1,
      top: 0,
      bottom: 0,
      left: 0,
      right: 0,
      ime: 0,
      immersive: true,
    });

    expect(notified).not.toBeNull();
    expect(notified!.immersive).toBe(true);
    expect(attrs.get('data-native-immersive')).toBe('true');

    unsub();

    // 验证调用原生主题与沉浸方法
    syncNativeTheme('paper', '#f5edda');
    expect(mockBridge.setTheme).toHaveBeenCalledWith('paper', '#f5edda');

    setNativeImmersive(true);
    expect(mockBridge.setImmersive).toHaveBeenCalledWith(true);
    setNativeImmersive(true);
    expect(mockBridge.setImmersive).toHaveBeenCalledTimes(1);
  });
});
