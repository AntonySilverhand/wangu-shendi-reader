/**
 * 统一布局控制器：语义 layout mode + 断点变化的唯一事实来源。
 *
 * 模式（按可用宽度，与 CSS 的 html[data-layout] 完全对齐）：
 *  - compact < 840    ：手机/折叠外屏/竖屏平板——目录抽屉、底栏、单栏首页
 *  - medium  840–1599 ：折叠内屏/横屏手机/小桌面——目录侧栏（可收起）、无右栏
 *  - wide    ≥ 1600   ：大桌面/外接显示器——左目录 + 中间正文 + 右侧进度栏
 *
 * 原则：
 *  - CSS 负责连续尺寸的 fluid 布局（minmax/clamp），JS 只在模式变化时
 *    执行结构性更新（属性切换 + 目录收放 + 阅读位置恢复），不做逐帧重建；
 *  - 触摸/键鼠只通过 pointer/hover media query 做交互优化，不参与模式判定；
 *  - 监听 resize（rAF 合帧）+ matchMedia 双保险，不重复注册多个事件。
 */
import { bump } from './instrument.ts';

export type LayoutMode = 'compact' | 'medium' | 'wide';

export const LAYOUT_MEDIUM_MIN = 840;
export const LAYOUT_WIDE_MIN = 1600;

export function getLayoutMode(width: number): LayoutMode {
  if (width >= LAYOUT_WIDE_MIN) return 'wide';
  if (width >= LAYOUT_MEDIUM_MIN) return 'medium';
  return 'compact';
}

export interface LayoutControllerOptions {
  /** 仅当模式真正变化时回调（含初始模式之外的后续变化） */
  onModeChange: (mode: LayoutMode, prev: LayoutMode) => void;
}

export class LayoutController {
  private mode: LayoutMode;
  private rafPending = false;
  private mqlMedium: MediaQueryList | null = null;
  private mqlWide: MediaQueryList | null = null;
  private started = false;

  constructor(private opts: LayoutControllerOptions) {
    this.mode = getLayoutMode(typeof window !== 'undefined' ? window.innerWidth : 0);
  }

  get current(): LayoutMode {
    return this.mode;
  }

  /** 首帧：把语义模式写到 <html data-layout>（CSS 与之对齐） */
  apply(): void {
    document.documentElement.setAttribute('data-layout', this.mode);
  }

  start(): void {
    if (this.started || typeof window === 'undefined') return;
    this.started = true;
    this.apply();
    window.addEventListener('resize', this.onResize, { passive: true });
    if (typeof matchMedia === 'function') {
      this.mqlMedium = matchMedia(`(min-width: ${LAYOUT_MEDIUM_MIN}px)`);
      this.mqlWide = matchMedia(`(min-width: ${LAYOUT_WIDE_MIN}px)`);
      this.mqlMedium.addEventListener?.('change', this.scheduleSync);
      this.mqlWide.addEventListener?.('change', this.scheduleSync);
    }
  }

  stop(): void {
    if (!this.started) return;
    this.started = false;
    window.removeEventListener('resize', this.onResize);
    if (this.mqlMedium?.removeEventListener && this.mqlWide?.removeEventListener) {
      this.mqlMedium.removeEventListener('change', this.scheduleSync);
      this.mqlWide.removeEventListener('change', this.scheduleSync);
    }
    this.mqlMedium = null;
    this.mqlWide = null;
  }

  private onResize = (): void => {
    this.scheduleSync();
  };

  private scheduleSync = (): void => {
    if (this.rafPending) return;
    this.rafPending = true;
    requestAnimationFrame(() => {
      this.rafPending = false;
      if (this.started) this.sync();
    });
  };

  private sync(): void {
    const next = getLayoutMode(window.innerWidth);
    if (next === this.mode) return;
    const prev = this.mode;
    this.mode = next;
    bump('layoutModeChanges');
    this.apply();
    this.opts.onModeChange(next, prev);
  }
}
