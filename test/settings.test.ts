import { afterEach, describe, expect, it, vi } from 'vitest';
import { loadSettings, SETTINGS_KEY } from '../src/web/store/settings.ts';

describe('设置迁移：旧“沉浸式阅读”不再生效', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('旧版 immersiveReading=true（隐藏状态栏+手势条）升级后回到默认显示状态栏', () => {
    const data = new Map<string, string>([[SETTINGS_KEY, JSON.stringify({ immersiveReading: true, fontSize: 20 })]]);
    vi.stubGlobal('localStorage', { getItem: (k: string) => data.get(k) ?? null, setItem: (k: string, v: string) => data.set(k, v) });
    const s = loadSettings();
    expect(s.hideStatusBar).toBe(false);
    expect(s.fontSize).toBe(20);
    expect('immersiveReading' in s).toBe(false);
  });

  it('新的“阅读时隐藏状态栏”可保存', () => {
    const data = new Map<string, string>([[SETTINGS_KEY, JSON.stringify({ hideStatusBar: true })]]);
    vi.stubGlobal('localStorage', { getItem: (k: string) => data.get(k) ?? null, setItem: (k: string, v: string) => data.set(k, v) });
    expect(loadSettings().hideStatusBar).toBe(true);
  });
});
