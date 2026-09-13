/** 阅读设置：持久化到 localStorage，并立即应用到文档根节点。 */
import { syncNativeTheme } from '../native-display.ts';

export type ThemeName = 'light' | 'dark' | 'black' | 'eink' | 'paper';
export type FontName = 'system' | 'hei' | 'serif' | 'kai';

export interface Settings {
  theme: ThemeName;
  font: FontName;
  /** px */
  fontSize: number;
  /** 无单位倍数 */
  lineHeight: number;
  /** em */
  paraGap: number;
  /** px（正文左右留白） */
  margin: number;
  /** em（宽屏最大行宽） */
  maxWidth: number;
  paperTexture: boolean;
  autoHideBars: boolean;
  prefetch: boolean;
  keepScreenAwake: boolean;
  immersiveReading: boolean;
}

export const SETTINGS_KEY = 'reader.settings.v1';

export const THEME_LABELS: Record<ThemeName, string> = {
  light: '亮色',
  dark: '暗色',
  black: '纯黑',
  eink: '仿电子墨水',
  paper: '纸张',
};

export const FONT_LABELS: Record<FontName, string> = {
  system: '系统默认',
  hei: '黑体',
  serif: '宋体',
  kai: '楷体',
};

export const THEME_COLORS: Record<ThemeName, string> = {
  light: '#f6f5f2',
  dark: '#161719',
  black: '#000000',
  eink: '#ededed',
  paper: '#f5edda',
};

function systemPrefersDark(): boolean {
  try {
    return window.matchMedia('(prefers-color-scheme: dark)').matches;
  } catch {
    return false;
  }
}

export const DEFAULT_SETTINGS: Settings = {
  theme: systemPrefersDark() ? 'dark' : 'light',
  font: 'system',
  fontSize: 18,
  lineHeight: 1.8,
  paraGap: 0.7,
  margin: 20,
  maxWidth: 36,
  paperTexture: true,
  autoHideBars: true,
  prefetch: true,
  keepScreenAwake: false,
  immersiveReading: false,
};

export function clamp(n: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, n));
}

function sanitize(raw: unknown): Settings {
  const s = { ...DEFAULT_SETTINGS };
  if (!raw || typeof raw !== 'object') return s;
  const r = raw as Record<string, unknown>;
  if (typeof r.theme === 'string' && r.theme in THEME_LABELS) s.theme = r.theme as ThemeName;
  if (typeof r.font === 'string' && r.font in FONT_LABELS) s.font = r.font as FontName;
  if (typeof r.fontSize === 'number') s.fontSize = clamp(Math.round(r.fontSize), 14, 28);
  if (typeof r.lineHeight === 'number') s.lineHeight = clamp(r.lineHeight, 1.3, 2.4);
  if (typeof r.paraGap === 'number') s.paraGap = clamp(r.paraGap, 0, 1.6);
  if (typeof r.margin === 'number') s.margin = clamp(Math.round(r.margin), 8, 64);
  if (typeof r.maxWidth === 'number') s.maxWidth = clamp(Math.round(r.maxWidth), 24, 60);
  if (typeof r.paperTexture === 'boolean') s.paperTexture = r.paperTexture;
  if (typeof r.autoHideBars === 'boolean') s.autoHideBars = r.autoHideBars;
  if (typeof r.prefetch === 'boolean') s.prefetch = r.prefetch;
  if (typeof r.keepScreenAwake === 'boolean') s.keepScreenAwake = r.keepScreenAwake;
  if (typeof r.immersiveReading === 'boolean') s.immersiveReading = r.immersiveReading;
  return s;
}

export function loadSettings(): Settings {
  try {
    const raw = localStorage.getItem(SETTINGS_KEY);
    if (!raw) return { ...DEFAULT_SETTINGS };
    return sanitize(JSON.parse(raw));
  } catch {
    return { ...DEFAULT_SETTINGS };
  }
}

export function applySettings(s: Settings): void {
  const root = document.documentElement;
  root.setAttribute('data-theme', s.theme);
  root.setAttribute('data-font', s.font);
  root.setAttribute('data-paper-texture', s.paperTexture ? 'on' : 'off');
  root.style.setProperty('--reader-font-size', `${s.fontSize}px`);
  root.style.setProperty('--reader-line-height', String(s.lineHeight));
  root.style.setProperty('--reader-para-gap', `${s.paraGap}em`);
  root.style.setProperty('--reader-margin', `${s.margin}px`);
  // 行宽按字符数计算：一行约 s.maxWidth 个汉字
  root.style.setProperty('--reader-max-width', `${s.maxWidth * s.fontSize}px`);
  const meta = document.querySelector('meta[name="theme-color"]');
  if (meta) {
    meta.setAttribute('content', THEME_COLORS[s.theme]);
  }
  const scheme = s.theme === 'dark' || s.theme === 'black' ? 'dark' : 'light';
  document.querySelector('meta[name="color-scheme"]')?.setAttribute('content', scheme);
  syncNativeTheme(s.theme, THEME_COLORS[s.theme]);
}

export type SettingsListener = (s: Settings) => void;

export class SettingsStore {
  private value: Settings;
  private listeners = new Set<SettingsListener>();

  constructor() {
    this.value = loadSettings();
  }

  get(): Settings {
    return this.value;
  }

  update(patch: Partial<Settings>): Settings {
    this.value = sanitize({ ...this.value, ...patch });
    applySettings(this.value);
    try {
      localStorage.setItem(SETTINGS_KEY, JSON.stringify(this.value));
    } catch (err) {
      console.error('[settings] localStorage 写入失败：', err instanceof Error ? err.message : String(err));
    }
    for (const fn of this.listeners) fn(this.value);
    return this.value;
  }

  replace(next: Settings): void {
    this.update(next);
  }

  subscribe(fn: SettingsListener): () => void {
    this.listeners.add(fn);
    return () => this.listeners.delete(fn);
  }
}
