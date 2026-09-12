/**
 * 当前章节内搜索：命中高亮、上/下一个跳转。
 * 输入框聚焦时不拦截浏览器快捷键（全局快捷键会跳过输入元素）。
 */
import { el, iconButton } from '../dom.ts';
import { rangeAtOffset, textOffsetWithin } from '../dom.ts';

interface Match {
  paragraph: number;
  start: number;
  end: number;
}

const MAX_HIGHLIGHTS = 500;

export class ChapterSearch {
  readonly element: HTMLDivElement;
  private input: HTMLInputElement;
  private countEl: HTMLSpanElement;
  private contentEl: HTMLElement | null = null;
  private matches: Match[] = [];
  private current = -1;
  private openState = false;
  private debounceTimer: ReturnType<typeof setTimeout> | null = null;
  private highlightRanges: Range[] = [];

  constructor(private opts: { onOpenChange?: (open: boolean) => void } = {}) {
    this.input = el('input', {
      type: 'search',
      placeholder: '本章内搜索',
      'aria-label': '本章内搜索',
      enterkeyhint: 'search',
      autocomplete: 'off',
      spellcheck: false,
    }) as HTMLInputElement;
    this.countEl = el('span', { class: 'search-count', text: '0/0' });
    this.element = el(
      'div',
      { class: 'search-bar', id: 'chapter-search', role: 'search' },
      this.input,
      this.countEl,
      iconButton({ label: '上一个', icon: 'up', onClick: () => this.go(-1) }),
      iconButton({ label: '下一个', icon: 'down', onClick: () => this.go(1) }),
      iconButton({ label: '关闭搜索', icon: 'close', onClick: () => this.close() }),
    );
    this.input.addEventListener('input', () => this.scheduleSearch());
    this.input.addEventListener('keydown', (event) => {
      if (event.key === 'Enter') {
        event.preventDefault();
        this.go(event.shiftKey ? -1 : 1);
      }
    });
  }

  setContent(content: HTMLElement | null): void {
    this.clearMarks();
    this.contentEl = content;
    this.matches = [];
    this.current = -1;
    this.updateCount();
    if (this.openState && this.input.value) this.scheduleSearch();
  }

  isOpen(): boolean {
    return this.openState;
  }

  open(selectText?: string): void {
    this.openState = true;
    this.element.classList.add('open');
    this.opts.onOpenChange?.(true);
    this.input.focus({ preventScroll: true });
    if (selectText) {
      this.input.value = selectText;
      this.scheduleSearch();
    } else {
      this.input.select();
      if (this.input.value) this.scheduleSearch();
    }
  }

  close(): void {
    if (this.debounceTimer) clearTimeout(this.debounceTimer);
    this.debounceTimer = null;
    this.openState = false;
    this.element.classList.remove('open');
    this.opts.onOpenChange?.(false);
    this.clearMarks();
    this.matches = [];
    this.current = -1;
    this.updateCount();
  }

  toggle(): void {
    if (this.openState) this.close();
    else this.open();
  }

  private scheduleSearch(): void {
    if (this.debounceTimer) clearTimeout(this.debounceTimer);
    this.debounceTimer = setTimeout(() => {
      this.debounceTimer = null;
      this.runSearch();
    }, 120);
  }

  private runSearch(): void {
    this.clearMarks();
    this.matches = [];
    this.current = -1;
    const query = this.input.value.trim();
    const content = this.contentEl;
    if (!query || !content) {
      this.updateCount();
      return;
    }
    const lower = query.toLowerCase();
    const paragraphs = Array.from(content.children) as HTMLElement[];
    for (let p = 0; p < paragraphs.length; p++) {
      const text = paragraphs[p]!.textContent ?? '';
      const haystack = text.toLowerCase();
      let from = 0;
      for (;;) {
        const idx = haystack.indexOf(lower, from);
        if (idx < 0) break;
        this.matches.push({ paragraph: p, start: idx, end: idx + query.length });
        from = idx + Math.max(1, query.length);
      }
    }
    if (this.matches.length > 0) {
      this.current = 0;
      this.highlightAll();
      this.scrollToCurrent();
    }
    this.updateCount();
  }

  private highlightAll(): void {
    this.applyHighlights();
  }

  private applyHighlights(): void {
    this.clearMarks();
    const content = this.contentEl;
    if (!content) return;
    const highlightCtor = (globalThis as { Highlight?: new (...ranges: Range[]) => unknown }).Highlight;
    const registry = (CSS as unknown as { highlights?: { set(k: string, v: unknown): void; delete(k: string): void } })
      .highlights;
    const canUseCssHighlight = typeof highlightCtor === 'function' && registry;
    const paragraphs = Array.from(content.children) as HTMLElement[];
    const allRanges: Range[] = [];
    let currentRange: Range | null = null;

    for (let i = 0; i < this.matches.length; i++) {
      if (i !== this.current && (!canUseCssHighlight || i >= MAX_HIGHLIGHTS)) continue;
      const m = this.matches[i]!;
      const p = paragraphs[m.paragraph];
      if (!p) continue;
      const startRange = rangeAtOffset(p, m.start);
      const endRange = rangeAtOffset(p, m.end);
      if (!startRange || !endRange) continue;
      const range = document.createRange();
      range.setStart(startRange.startContainer, startRange.startOffset);
      range.setEnd(endRange.startContainer, endRange.startOffset);
      allRanges[i] = range;
      if (i === this.current) currentRange = range;
    }
    this.highlightRanges = allRanges;

    if (canUseCssHighlight) {
      registry.set('reader-search', new highlightCtor(...allRanges.filter(Boolean)) as never);
      if (currentRange) registry.set('reader-search-current', new highlightCtor(currentRange) as never);
      return;
    }
    // 旧 WebView 只高亮当前命中；匹配列表和导航仍覆盖全部结果。
    const marks: HTMLElement[] = [];
    for (let i = this.current; i >= 0 && i === this.current; i++) {
      const m = this.matches[i]!;
      const p = paragraphs[m.paragraph];
      if (!p) continue;
      const startRange = rangeAtOffset(p, m.start);
      const endRange = rangeAtOffset(p, m.end);
      if (!startRange || !endRange) continue;
      const range = document.createRange();
      range.setStart(startRange.startContainer, startRange.startOffset);
      range.setEnd(endRange.startContainer, endRange.startOffset);
      const mark = document.createElement('mark');
      mark.className = i === this.current ? 'hl hl-current' : 'hl';
      try {
        range.surroundContents(mark);
        marks.push(mark);
      } catch {
        /* 跨节点时忽略 */
      }
    }
    void marks;
  }

  private clearMarks(): void {
    const registry = (CSS as unknown as { highlights?: { delete(k: string): void } }).highlights;
    registry?.delete('reader-search');
    registry?.delete('reader-search-current');
    this.highlightRanges = [];
    const content = this.contentEl;
    if (!content) return;
    const marks = content.querySelectorAll('mark.hl');
    marks.forEach((mark) => {
      const parent = mark.parentNode;
      if (!parent) return;
      parent.replaceChild(document.createTextNode(mark.textContent ?? ''), mark);
      parent.normalize();
    });
  }

  private go(delta: number): void {
    if (this.matches.length === 0) return;
    this.current = (this.current + delta + this.matches.length) % this.matches.length;
    this.applyHighlights();
    this.scrollToCurrent();
    this.updateCount();
  }

  private scrollToCurrent(): void {
    const content = this.contentEl;
    if (!content || this.current < 0) return;
    const contentTop = content.getBoundingClientRect().top + window.scrollY;
    let y: number | null = null;
    if (this.highlightRanges.length === 0) this.applyHighlights();
    const range = this.highlightRanges[this.current];
    if (range) {
      const rect = range.getBoundingClientRect();
      if (rect.height > 0 || rect.top > 0) y = rect.top + window.scrollY;
    }
    if (y === null) {
      const m = this.matches[this.current]!;
      const p = (content.children[m.paragraph] as HTMLElement | undefined) ?? null;
      if (!p) return;
      y = p.getBoundingClientRect().top + window.scrollY;
    }
    const target = Math.max(0, y - Math.min(window.innerHeight * 0.35, 300));
    window.scrollTo({ top: target, behavior: 'auto' });
    void contentTop;
  }

  private updateCount(): void {
    this.countEl.textContent =
      this.matches.length === 0 ? '0/0' : `${this.current + 1}/${this.matches.length}`;
  }
}

export function scrollParagraphIntoView(paragraph: HTMLElement, offset: number): void {
  const range = offset > 0 ? rangeAtOffset(paragraph, offset) : null;
  const y = range
    ? range.getBoundingClientRect().top + window.scrollY
    : paragraph.getBoundingClientRect().top + window.scrollY;
  window.scrollTo({ top: Math.max(0, y - Math.min(window.innerHeight * 0.3, 260)), behavior: 'auto' });
}

export { textOffsetWithin };
