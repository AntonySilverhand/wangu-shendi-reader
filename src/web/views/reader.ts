/**
 * 阅读视图：正文渲染、阅读位置锚定、滚动/翻章交互。
 *
 * 位置锚点 = 段落序号 + 段落内字符偏移（不依赖滚动像素），
 * 因此改字号、改行距、旋转、折叠展开后都能回到同一处文字。
 */
import { clear, el, on, throttleRaf } from '../dom.ts';
import type { ChapterRecord } from '../store/db.ts';
import { chapterLabel } from '../format.ts';
import type { TocEntry } from '../../shared/source.ts';

export interface ReaderCallbacks {
  onOpenToc: () => void;
  onOpenSettings: () => void;
  onOpenBookmarks: () => void;
  onOpenDownload: () => void;
  onOpenSearch: () => void;
  onToggleBookmark: () => void;
  isBookmarked: () => boolean;
  onNavigate: (chapterId: string) => void;
  onPositionChange: (pos: { paragraph: number; offset: number }) => void;
  onRequestRetry: () => void;
}

export interface RenderedChapter {
  record: ChapterRecord;
  entry: TocEntry | null;
  prevId: string | null;
  nextId: string | null;
}

export class ReaderView {
  readonly root: HTMLDivElement;
  private contentEl: HTMLDivElement;
  private titleEl: HTMLHeadingElement;
  private metaEl: HTMLDivElement;
  private endEl: HTMLDivElement;
  private bannerEl: HTMLDivElement;
  private current: RenderedChapter | null = null;
  private lastPosition: { paragraph: number; offset: number } | null = null;
  private lastPersist = 0;
  private lastWidth = window.innerWidth;
  private lastHeight = window.innerHeight;
  private barsHidden = false;
  private searchOpen = false;
  private suppressRestore = false;

  constructor(private cb: ReaderCallbacks) {
    this.titleEl = el('h1', { class: 'chapter-title', id: 'chapter-title' });
    this.metaEl = el('div', { class: 'chapter-meta' });
    this.contentEl = el('div', { class: 'reader-content', id: 'chapter-content' });
    this.endEl = el('div', { class: 'chapter-end', id: 'chapter-end' });
    this.bannerEl = el('div', { class: 'banner', hidden: true });
    const article = el('article', { class: 'chapter' }, this.bannerEl, this.titleEl, this.metaEl, this.contentEl, this.endEl);
    this.root = el('div', { class: 'reader-root', id: 'reader-root' }, el('div', { class: 'reader-scroll' }, article));
  }

  get currentChapterId(): string | null {
    return this.current?.record.chapterId ?? null;
  }

  get currentEntry(): TocEntry | null {
    return this.current?.entry ?? null;
  }

  get lastKnownPosition(): { paragraph: number; offset: number } | null {
    return this.lastPosition;
  }

  setSearchOpen(open: boolean): void {
    this.searchOpen = open;
  }

  mount(): void {
    const scrollHandler = throttleRaf(() => this.handleScroll());
    on(window, 'scroll', scrollHandler, { passive: true });
    const resizeHandler = () => this.handleResize();
    on(window, 'resize', resizeHandler);
    on(window, 'orientationchange', resizeHandler);
    on(this.root, 'click', (event) => this.handleTap(event));
  }

  private handleTap(event: MouseEvent): void {
    const target = event.target as HTMLElement;
    if (target.closest('a, button, input, select, mark, .hl')) return;
    const selection = window.getSelection();
    if (selection && !selection.isCollapsed) return;
    this.toggleBars();
  }

  toggleBars(): void {
    this.barsHidden = !this.barsHidden;
    document.getElementById('app')?.setAttribute('data-bars', this.barsHidden ? 'hidden' : 'shown');
  }

  showBars(): void {
    if (!this.barsHidden) return;
    this.barsHidden = false;
    document.getElementById('app')?.setAttribute('data-bars', 'shown');
  }

  private hideBars(): void {
    if (this.barsHidden || this.searchOpen) return;
    this.barsHidden = true;
    document.getElementById('app')?.setAttribute('data-bars', 'hidden');
  }

  private handleScroll(): void {
    this.onScrollDelta();
    const pos = this.capturePosition();
    if (pos) {
      this.lastPosition = pos;
      this.notifyPosition(pos);
    }
  }

  private lastScrollY = 0;
  private lastRenderAt = 0;

  onScrollDelta(): void {
    // 切章/恢复位置会引发一次滚动，忽略短时间内的自动滚动
    if (Date.now() - this.lastRenderAt < 400) return;
    const y = window.scrollY;
    const delta = y - this.lastScrollY;
    this.lastScrollY = y;
    if (Math.abs(delta) < 6) return;
    if (delta > 0 && y > 90) this.hideBars();
    else if (delta < 0) this.showBars();
  }

  private notifyPosition(pos: { paragraph: number; offset: number }): void {
    const now = Date.now();
    if (now - this.lastPersist < 1000) return;
    this.lastPersist = now;
    if (!this.current) return;
    this.cb.onPositionChange({ paragraph: pos.paragraph, offset: pos.offset });
  }

  /** 立即保存（切章、隐藏页面等生命周期节点） */
  flushPosition(): void {
    const pos = this.lastPosition ?? this.capturePosition();
    if (!pos || !this.current) return;
    this.cb.onPositionChange({ paragraph: pos.paragraph, offset: pos.offset });
  }

  /* ----------------------- 位置捕获与恢复 ----------------------- */

  private anchorY(): number {
    const h = window.innerHeight;
    return Math.max(24, Math.min(h * 0.3, 260));
  }

  private pointAt(y: number): { node: Node; offset: number } | null {
    const x = Math.max(1, Math.min(window.innerWidth / 2, window.innerWidth - 2));
    const doc = document as Document & {
      caretRangeFromPoint?: (x: number, y: number) => Range | null;
      caretPositionFromPoint?: (x: number, y: number) => { offsetNode: Node; offset: number } | null;
    };
    try {
      if (doc.caretRangeFromPoint) {
        const range = doc.caretRangeFromPoint(x, y);
        if (range) return { node: range.startContainer, offset: range.startOffset };
      }
      if (doc.caretPositionFromPoint) {
        const pos = doc.caretPositionFromPoint(x, y);
        if (pos) return { node: pos.offsetNode, offset: pos.offset };
      }
    } catch {
      return null;
    }
    return null;
  }

  capturePosition(): { paragraph: number; offset: number } | null {
    const paragraphs = this.paragraphs();
    if (paragraphs.length === 0) return null;
    const anchorY = this.anchorY();
    let lo = 0;
    let hi = paragraphs.length - 1;
    let found = paragraphs.length - 1;
    while (lo <= hi) {
      const mid = (lo + hi) >> 1;
      const rect = paragraphs[mid]!.getBoundingClientRect();
      if (rect.bottom > anchorY) {
        found = mid;
        hi = mid - 1;
      } else {
        lo = mid + 1;
      }
    }
    const target = paragraphs[found]!;
    let offset = 0;
    const point = this.pointAt(anchorY);
    if (point && target.contains(point.node)) {
      offset = this.textOffset(target, point.node, point.offset);
    }
    return { paragraph: found, offset };
  }

  private textOffset(root: HTMLElement, node: Node, nodeOffset: number): number {
    try {
      const range = document.createRange();
      range.setStart(root, 0);
      range.setEnd(node, nodeOffset);
      return range.toString().length;
    } catch {
      return 0;
    }
  }

  private rangeAtOffset(root: HTMLElement, offset: number): Range | null {
    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
    let remaining = offset;
    let node = walker.nextNode();
    while (node) {
      const len = node.textContent?.length ?? 0;
      if (remaining <= len) {
        const range = document.createRange();
        range.setStart(node, Math.max(0, remaining));
        range.collapse(true);
        return range;
      }
      remaining -= len;
      node = walker.nextNode();
    }
    return null;
  }

  restorePosition(pos: { paragraph: number; offset: number }): void {
    const paragraphs = this.paragraphs();
    if (paragraphs.length === 0) {
      window.scrollTo(0, 0);
      return;
    }
    const index = Math.min(Math.max(0, pos.paragraph), paragraphs.length - 1);
    const target = paragraphs[index]!;
    let y = target.getBoundingClientRect().top + window.scrollY;
    if (pos.offset > 0) {
      const range = this.rangeAtOffset(target, pos.offset);
      if (range) {
        const rect = range.getBoundingClientRect();
        if (rect.width > 0 || rect.height > 0) y = rect.top + window.scrollY;
      }
    }
    window.scrollTo({ top: Math.max(0, y - this.anchorY()), behavior: 'auto' });
    this.lastPosition = { paragraph: index, offset: pos.offset };
  }

  private handleResize(): void {
    const width = window.innerWidth;
    const height = window.innerHeight;
    const widthChanged = Math.abs(width - this.lastWidth) > 2;
    const bigHeightChange = Math.abs(height - this.lastHeight) > 150;
    const editing = /^(input|textarea|select)$/i.test(document.activeElement?.tagName ?? '');
    this.lastWidth = width;
    this.lastHeight = height;
    if (!widthChanged && !bigHeightChange) return;
    if (editing) return;
    const pos = this.lastPosition;
    if (!pos) return;
    // 布局稳定后再恢复，避免中间态计算错误
    requestAnimationFrame(() => {
      requestAnimationFrame(() => {
        if (!this.suppressRestore) this.restorePosition(pos);
      });
    });
  }

  /** 设置变更（字号/行距/边距）后保持当前位置 */
  refreshLayout(): void {
    const pos = this.lastPosition;
    if (!pos) return;
    requestAnimationFrame(() => this.restorePosition(pos));
  }

  private paragraphs(): HTMLElement[] {
    return Array.from(this.contentEl.children) as HTMLElement[];
  }

  /* ---------------------------- 渲染 ---------------------------- */

  setInitialPosition(pos: { paragraph: number; offset: number } | null): void {
    this.lastPosition = pos;
  }

  renderLoading(known?: { title?: string; entry: TocEntry | null }): void {
    this.current = null;
    this.lastPosition = null;
    this.bannerEl.hidden = true;
    this.titleEl.textContent = known?.entry ? chapterLabel(known.entry) : (known?.title ?? '加载中…');
    clear(this.metaEl);
    clear(this.contentEl);
    this.endEl.hidden = true;
    const lines = el('div', { class: 'loading-block' });
    for (let i = 0; i < 8; i++) {
      lines.appendChild(
        el('div', {
          class: 'skeleton-line',
          style: { width: `${[96, 88, 94, 76, 92, 90, 70, 85][i]}%` },
        }),
      );
    }
    this.contentEl.appendChild(lines);
  }

  showBanner(text: string, actions?: { label: string; onClick: () => void }[]): void {
    clear(this.bannerEl);
    this.bannerEl.hidden = false;
    this.bannerEl.appendChild(el('span', { text }));
    this.bannerEl.appendChild(el('span', { class: 'spacer' }));
    for (const action of actions ?? []) {
      this.bannerEl.appendChild(
        el('button', { class: 'btn small', type: 'button', onclick: action.onClick }, action.label),
      );
    }
  }

  hideBanner(): void {
    this.bannerEl.hidden = true;
  }

  renderError(message: string, detail?: string): void {
    this.current = null;
    clear(this.contentEl);
    this.endEl.hidden = true;
    const box = el(
      'div',
      { class: 'error-box', id: 'chapter-error' },
      el('h2', { text: '这一章没有加载成功' }),
      el('p', { text: message }),
      detail ? el('p', { class: 'loading-note', text: detail }) : null,
      el(
        'div',
        { class: 'row-actions' },
        el(
          'button',
          { class: 'btn primary', type: 'button', id: 'retry-chapter', onclick: () => this.cb.onRequestRetry() },
          '重试',
        ),
        el('button', { class: 'btn', type: 'button', onclick: () => this.cb.onOpenToc() }, '打开目录'),
      ),
    );
    this.contentEl.appendChild(box);
  }

  renderChapter(chapter: RenderedChapter, opts: { restore?: boolean } = {}): void {
    const { record, entry } = chapter;
    this.current = chapter;
    this.bannerEl.hidden = true;
    this.titleEl.textContent = chapterLabel(entry, record.title) || record.title;
    clear(this.metaEl);
    this.metaEl.appendChild(el('span', { text: `${record.paragraphs.length} 段` }));
    this.metaEl.appendChild(el('span', { text: `${record.charCount.toLocaleString('zh-CN')} 字` }));
    if (record.source === 'local') this.metaEl.appendChild(el('span', { text: '本地导入' }));

    clear(this.contentEl);
    const fragment = document.createDocumentFragment();
    record.paragraphs.forEach((text, index) => {
      const p = document.createElement('p');
      p.dataset.p = String(index);
      p.textContent = text;
      fragment.appendChild(p);
    });
    this.contentEl.appendChild(fragment);
    this.renderEnd(chapter);
    this.lastRenderAt = Date.now();
    if (opts.restore && this.lastPosition) {
      const pos = this.lastPosition;
      requestAnimationFrame(() => {
        this.restorePosition(pos);
        this.lastScrollY = window.scrollY;
        this.showBars();
      });
    } else {
      window.scrollTo(0, 0);
      this.lastPosition = { paragraph: 0, offset: 0 };
      this.lastScrollY = 0;
      this.showBars();
    }
    this.lastScrollY = window.scrollY;
  }

  private renderEnd(chapter: RenderedChapter): void {
    clear(this.endEl);
    this.endEl.hidden = false;
    const prevDisabled = !chapter.prevId;
    const nextDisabled = !chapter.nextId;
    this.endEl.appendChild(
      el(
        'div',
        { class: 'nav-row' },
        el(
          'button',
          {
            class: 'btn',
            type: 'button',
            disabled: prevDisabled,
            id: 'nav-prev',
            onclick: () => chapter.prevId && this.cb.onNavigate(chapter.prevId),
          },
          '上一章',
        ),
        el(
          'button',
          {
            class: 'btn primary',
            type: 'button',
            disabled: nextDisabled,
            id: 'nav-next',
            onclick: () => chapter.nextId && this.cb.onNavigate(chapter.nextId),
          },
          '下一章',
        ),
      ),
    );
    this.endEl.appendChild(
      el(
        'div',
        { class: 'row-actions' },
        el('button', { class: 'btn ghost small', type: 'button', onclick: () => this.cb.onOpenToc() }, '章节目录'),
        el(
          'button',
          { class: 'btn ghost small', type: 'button', onclick: () => this.cb.onToggleBookmark() },
          this.cb.isBookmarked() ? '取消书签' : '加入书签',
        ),
        el('button', { class: 'btn ghost small', type: 'button', onclick: () => this.cb.onOpenDownload() }, '下载'),
      ),
    );
  }

  /** 更新书签按钮文案等（渲染后状态变化） */
  get paragraphElements(): HTMLElement[] {
    return this.paragraphs();
  }

  get contentElement(): HTMLElement {
    return this.contentEl;
  }

  clearHighlights(): void {
    for (const p of this.paragraphs()) {
      const marks = p.querySelectorAll('mark.hl');
      if (marks.length === 0) continue;
      marks.forEach((mark) => {
        const parent = mark.parentNode!;
        parent.replaceChild(document.createTextNode(mark.textContent ?? ''), mark);
        parent.normalize();
      });
    }
  }
}
