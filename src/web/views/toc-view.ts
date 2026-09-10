/**
 * 目录视图：抽屉（手机）/ 侧栏（宽屏）。
 * 4300+ 章采用固定行高虚拟列表，只渲染可视区域，滚动与内存开销可控。
 */
import { clear, el, iconButton, throttleRaf } from '../dom.ts';
import { chapterOrdinal, shortTitle } from '../format.ts';
import type { TocStore } from '../store/toc.ts';

const ROW_HEIGHT = 52;
const OVERSCAN = 10;
const SEARCH_LIMIT = 200;

export interface TocViewOptions {
  store: TocStore;
  onOpenChapter: (chapterId: string) => void;
  onOpenTocData?: () => void;
  getCurrentChapterId: () => string | null;
}

export class TocView {
  readonly panel: HTMLElement;
  private listEl: HTMLDivElement;
  private bodyEl: HTMLDivElement;
  private searchInput: HTMLInputElement;
  private footerEl: HTMLDivElement;
  private progressEl: HTMLSpanElement;
  private titleEl: HTMLHeadingElement;
  private cachedIds = new Set<string>();
  private query = '';
  private autoSearchLoading = false;
  private rowPool: HTMLButtonElement[] = [];
  private renderedRange = { start: -1, end: -1 };
  private lastCount = 0;
  private openState = false;
  private scrollRaf: (() => void) | null = null;

  constructor(private opts: TocViewOptions) {
    this.titleEl = el('h2', { text: '目录' });
    this.searchInput = el('input', {
      type: 'search',
      placeholder: '搜索章节名或章节号',
      'aria-label': '搜索目录',
      inputmode: 'search',
      enterkeyhint: 'search',
    }) as HTMLInputElement;
    this.searchInput.addEventListener('input', () => {
      this.query = this.searchInput.value.trim();
      this.renderList(true);
      // 目录未加载完时，搜索会自动补齐剩余页并实时刷新结果
      if (this.query && !this.opts.store.isComplete) void this.loadAllForSearch();
    });
    const searchWrap = el(
      'div',
      { class: 'toc-search' },
      this.searchInput,
      el(
        'div',
        { class: 'row-actions', style: { justifyContent: 'space-between', alignItems: 'center' } },
        (this.progressEl = el('span', { class: 'loading-note', style: { textAlign: 'left' } })),
        el(
          'button',
          {
            class: 'btn small ghost',
            type: 'button',
            id: 'toc-load-all',
            onclick: () => this.loadAll(),
          },
          '加载全部',
        ),
      ),
    );
    this.listEl = el('div', { class: 'toc-list', role: 'list' });
    this.bodyEl = el('div', { class: 'panel-body' }, this.listEl);
    this.footerEl = el('div', { class: 'toc-footer' });
    this.panel = el(
      'aside',
      { class: 'toc-panel', id: 'toc-panel', 'aria-label': '章节目录' },
      el(
        'div',
        { class: 'panel-head' },
        this.titleEl,
        iconButton({ label: '关闭目录', icon: 'close', onClick: () => this.close() }),
      ),
      searchWrap,
      this.bodyEl,
      this.footerEl,
    );
    this.bodyEl.addEventListener('scroll', this.onScroll, { passive: true });
    this.scrollRaf = throttleRaf(() => this.renderList(false));
    this.opts.store.subscribe(() => this.onStoreChange());
  }

  private onStoreChange(): void {
    this.updateFooter();
    if (this.query) this.renderSearch();
    else if (this.openState) this.renderList(true);
  }

  setCachedIds(ids: Set<string>): void {
    this.cachedIds = ids;
    if (this.openState) this.renderList(true);
  }

  isOpen(): boolean {
    return this.openState;
  }

  open(): void {
    this.openState = true;
    this.panel.classList.add('open');
    document.getElementById('app')?.setAttribute('data-toc', 'open');
    this.updateFooter();
    this.renderList(true);
    requestAnimationFrame(() => this.scrollToCurrent());
    // 首次打开时补齐前几页
    if (this.opts.store.loadedPageCount === 0) void this.opts.store.loadRange(1, 4);
  }

  close(): void {
    this.openState = false;
    this.panel.classList.remove('open');
    document.getElementById('app')?.setAttribute('data-toc', 'closed');
  }

  toggle(): void {
    if (this.openState) this.close();
    else this.open();
  }

  private onScroll = (): void => {
    this.scrollRaf?.();
    const store = this.opts.store;
    if (this.query) return;
    const total = store.all.length;
    if (total === 0) return;
    const viewTop = this.bodyEl.scrollTop;
    const viewBottom = viewTop + this.bodyEl.clientHeight;
    const lastVisible = Math.floor(viewBottom / ROW_HEIGHT);
    if (lastVisible > total - 120) {
      const next = store.nextMissingPage();
      if (next !== null && !store.loadingPages.has(next)) {
        void store.loadRange(next, next + 3, { background: true });
      }
    }
  };

  private async loadAll(): Promise<void> {
    this.updateFooter('正在加载目录…');
    await this.opts.store.loadAll((p) => {
      this.updateFooter(`正在加载目录… ${p.loadedPages}/${p.totalPages} 页`);
    });
    this.updateFooter();
    this.renderList(true);
  }

  private updateFooter(override?: string): void {
    const store = this.opts.store;
    const total = store.all.length;
    const currentId = this.opts.getCurrentChapterId();
    const index = currentId ? store.indexOfChapter(currentId) : -1;
    clear(this.footerEl);
    if (override) {
      this.footerEl.appendChild(el('div', { class: 'row' }, override));
    }
    const status = store.isComplete
      ? `已加载全部 ${total} 章`
      : `已加载 ${total} 章 · ${store.loadedPageCount}/${store.totalPages} 页`;
    this.footerEl.appendChild(
      el(
        'div',
        { class: 'row' },
        el('span', { text: status }),
        index >= 0 ? el('span', { text: `当前第 ${index + 1} 章` }) : null,
      ),
    );
    if (store.failedPages.size > 0) {
      this.footerEl.appendChild(
        el(
          'div',
          { class: 'row' },
          el('span', { text: `${store.failedPages.size} 页加载失败` }),
          el(
            'button',
            {
              class: 'btn small ghost',
              type: 'button',
              onclick: () => {
                const from = Math.min(...store.failedPages);
                const to = Math.max(...store.failedPages);
                store.failedPages.clear();
                void store.loadRange(from, to, { background: true });
              },
            },
            '重试',
          ),
        ),
      );
    }
    if (this.opts.onOpenTocData) {
      this.footerEl.appendChild(
        el(
          'div',
          { class: 'row' },
          el(
            'button',
            {
              class: 'btn small ghost',
              type: 'button',
              onclick: () => {
                this.close();
                this.opts.onOpenTocData?.();
              },
            },
            '下载与缓存',
          ),
        ),
      );
    }
    if (this.progressEl) {
      this.progressEl.textContent = store.isComplete
        ? ''
        : store.loadingPages.size > 0
          ? '加载中…'
          : '继续滑动自动加载';
    }
  }

  private scrollToCurrent(): void {
    const id = this.opts.getCurrentChapterId();
    if (!id) return;
    const index = this.opts.store.indexOfChapter(id);
    if (index < 0) return;
    const top = index * ROW_HEIGHT;
    if (
      top < this.bodyEl.scrollTop ||
      top > this.bodyEl.scrollTop + this.bodyEl.clientHeight - ROW_HEIGHT * 2
    ) {
      this.bodyEl.scrollTop = Math.max(0, top - this.bodyEl.clientHeight / 3);
    }
    this.renderList(true);
  }

  private createRow(): HTMLButtonElement {
    return el('button', {
      class: 'toc-row',
      type: 'button',
      role: 'listitem',
      style: { position: 'absolute' },
    }) as HTMLButtonElement;
  }

  private renderList(force: boolean): void {
    const store = this.opts.store;
    if (this.query) {
      this.renderSearch();
      return;
    }
    const total = store.all.length;
    if (total === 0) {
      clear(this.listEl);
      this.listEl.style.height = '80px';
      this.listEl.appendChild(
        el('div', { class: 'empty-hint', text: store.isComplete ? '目录为空' : '目录加载中…' }),
      );
      this.renderedRange = { start: -1, end: -1 };
      return;
    }
    const viewTop = this.bodyEl.scrollTop;
    const viewHeight = this.bodyEl.clientHeight || 600;
    const start = Math.max(0, Math.floor(viewTop / ROW_HEIGHT) - OVERSCAN);
    const end = Math.min(total, Math.ceil((viewTop + viewHeight) / ROW_HEIGHT) + OVERSCAN);
    this.listEl.style.height = `${total * ROW_HEIGHT}px`;
    if (!force && start === this.renderedRange.start && end === this.renderedRange.end && total === this.lastCount) {
      return;
    }
    this.renderedRange = { start, end };
    this.lastCount = total;
    clear(this.listEl);
    const currentId = this.opts.getCurrentChapterId();
    const slots = end - start;
    while (this.rowPool.length < slots) this.rowPool.push(this.createRow());
    for (let i = start; i < end; i++) {
      const entry = store.all[i];
      if (!entry) continue;
      const row = this.rowPool[i - start]!;
      clear(row);
      row.style.top = `${i * ROW_HEIGHT}px`;
      row.dataset.index = String(i);
      row.setAttribute('role', 'listitem');
      row.setAttribute('aria-label', `${chapterOrdinal(entry)} ${shortTitle(entry)}`);
      if (entry.id === currentId) row.classList.add('current');
      else row.classList.remove('current');
      const isCached = this.cachedIds.has(entry.id);
      row.appendChild(el('span', { class: 'num', text: chapterOrdinal(entry) }));
      row.appendChild(el('span', { class: 'name', text: shortTitle(entry) }));
      row.appendChild(
        el(
          'span',
          { class: 'flags' },
          isCached ? el('span', { class: 'dot', title: '已缓存' }) : null,
        ),
      );
      row.onclick = () => this.opts.onOpenChapter(entry.id);
      this.listEl.appendChild(row);
    }
  }

  private async loadAllForSearch(): Promise<void> {
    if (this.autoSearchLoading) return;
    this.autoSearchLoading = true;
    this.updateFooter('正在加载完整目录以便搜索…');
    await this.opts.store.loadAll((p) => {
      this.updateFooter(`正在搜索全部目录… ${p.loadedPages}/${p.totalPages} 页`);
      if (this.query) this.renderSearch();
    });
    this.autoSearchLoading = false;
    this.updateFooter();
    if (this.query) this.renderSearch();
  }

  private renderSearch(): void {
    const store = this.opts.store;
    const results = store.search(this.query, SEARCH_LIMIT);
    clear(this.listEl);
    this.listEl.style.height = 'auto';
    this.renderedRange = { start: -1, end: -1 };
    if (results.length === 0) {
      const loading = !store.isComplete;
      this.listEl.appendChild(
        el('div', {
          class: 'empty-hint',
          text: loading
            ? `尚未找到匹配项，正在加载完整目录…（${store.loadedPageCount}/${store.totalPages} 页）`
            : '没有找到匹配的章节',
        }),
      );
      if (loading && !this.autoSearchLoading) void this.loadAllForSearch();
      return;
    }
    const currentId = this.opts.getCurrentChapterId();
    for (const r of results) {
      const row = el(
        'button',
        { class: 'toc-row', type: 'button', style: { position: 'relative' } },
        el('span', { class: 'num', text: chapterOrdinal(r.entry) }),
        el('span', { class: 'name', text: shortTitle(r.entry) }),
        el('span', { class: 'flags' }, this.cachedIds.has(r.entry.id) ? el('span', { class: 'dot' }) : null),
      );
      if (r.entry.id === currentId) row.classList.add('current');
      row.addEventListener('click', () => this.opts.onOpenChapter(r.entry.id));
      this.listEl.appendChild(row);
    }
    this.listEl.appendChild(
      el('div', {
        class: 'loading-note',
        text: `找到 ${results.length}${results.length >= SEARCH_LIMIT ? '+' : ''} 条${
          store.isComplete ? '' : ` · 目录 ${store.loadedPageCount}/${store.totalPages} 页`
        }`,
      }),
    );
  }

  searchInToc(query: string): void {
    this.searchInput.value = query;
    this.query = query.trim();
    this.renderList(true);
    if (this.query && !this.opts.store.isComplete) void this.loadAllForSearch();
  }
}
