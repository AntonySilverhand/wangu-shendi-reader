/**
 * 目录视图：抽屉（手机）/ 侧栏（宽屏）。
 * 4300+ 章采用固定行高虚拟列表，只渲染可视区域，滚动与内存开销可控。
 *
 * 搜索由 TocSearchController 驱动（debounce + generation + 数字定位 + 渐进补全），
 * 视图只负责渲染控制器给出的状态；renderSearch 绝不触发新的加载任务，
 * 因此不存在 renderSearch → loadAll → renderSearch 的回路。
 */
import { clear, el, iconButton, throttleRaf } from '../dom.ts';
import { chapterOrdinal, shortTitle } from '../format.ts';
import type { TocStore } from '../store/toc.ts';
import {
  TocSearchController,
  TOC_SEARCH_LIMIT,
  type TocSearchPhase,
  type TocSearchResult,
} from '../store/toc-search.ts';
import { bump, setSearchState } from '../instrument.ts';

const ROW_HEIGHT = 52;
const OVERSCAN = 10;

export interface TocViewOptions {
  store: TocStore;
  onOpenChapter: (chapterId: string) => void;
  onOpenTocData?: () => void;
  onOpenChange?: () => void;
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
  private searchPhase: TocSearchPhase = { kind: 'idle' };
  private searchResults: TocSearchResult[] = [];
  private search: TocSearchController;
  private rowPool: HTMLButtonElement[] = [];
  private renderedRange = { start: -1, end: -1 };
  private lastCount = 0;
  private openState = false;
  private scrollRaf: (() => void) | null = null;

  constructor(private opts: TocViewOptions) {
    this.search = new TocSearchController(
      {
        searchLoaded: (q, limit) => opts.store.search(q, limit),
        countLoadable: () => opts.store.countLoadable(),
        nextLoadablePage: () => opts.store.nextLoadablePage(),
        isComplete: () => opts.store.isComplete,
        failedPageCount: () => opts.store.failedPages.size,
        loadedPageCount: () => opts.store.loadedPageCount,
        totalPages: () => opts.store.totalPages,
        loadRange: (from, to, signal) => opts.store.loadRange(from, to, { signal }),
        estimatePageForNumber: (n) => opts.store.estimatePageForNumber(n),
        retryFailedPages: (signal) => opts.store.retryFailedPages(signal),
      },
      {
        update: (state, results) => {
          const wasIdle =
            this.searchPhase.kind === 'idle' || this.searchPhase.kind === 'debouncing';
          if (state.kind === 'searching' && wasIdle) bump('loadAllForSearchCount');
          this.searchPhase = state;
          this.searchResults = results;
          setSearchState(
            state.kind === 'searching'
              ? 'running'
              : state.kind === 'done'
                ? results.length > 0
                  ? 'found'
                  : 'exhausted'
                : state.kind === 'cancelled'
                  ? 'cancelled'
                  : 'idle',
          );
          if (this.query) this.renderSearchView();
          this.updateFooter();
        },
      },
    );

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
      bump('searchGeneration');
      this.search.setQuery(this.query);
      // 立即重绘（旧结果 + 新状态），网络任务由控制器 debounce 后统一发起
      this.renderList(true);
      this.updateFooter();
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

  private renderScheduled = false;

  private onStoreChange(): void {
    if (this.renderScheduled) return;
    this.renderScheduled = true;
    requestAnimationFrame(() => {
      this.renderScheduled = false;
      this.updateFooter();
      if (this.query && this.openState) {
        // 目录数据变化：只重算结果，绝不因此发起新加载（任务在跑时由任务自己刷新）
        this.search.refreshFromLoadedData(this.query);
      } else if (this.openState) {
        this.renderList(true);
      }
    });
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
    this.opts.onOpenChange?.();
    this.updateFooter();
    this.renderList(true);
    requestAnimationFrame(() => this.scrollToCurrent());
    // 首次打开时补齐前几页
    if (this.opts.store.loadedPageCount === 0) void this.opts.store.loadRange(1, 4);
    // 重新打开：若查询仍在，重启搜索（新 generation，旧任务已作废）
    if (this.query) {
      this.search.setQuery(this.query);
      this.renderSearchView();
    }
  }

  close(): void {
    if (this.panel.contains(document.activeElement)) (document.activeElement as HTMLElement)?.blur();
    this.openState = false;
    this.panel.classList.remove('open');
    document.getElementById('app')?.setAttribute('data-toc', 'closed');
    // 关闭目录 = 取消搜索（含 debounce 期）：旧任务不得继续控制 UI / 占用网络
    this.search.cancel();
    this.opts.onOpenChange?.();
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
      const next = store.nextLoadablePage();
      if (next !== null && !store.loadingPages.has(next)) {
        void store.loadRange(next, next + 3, { background: true });
      }
    }
  };

  private async loadAll(): Promise<void> {
    this.updateFooter('正在加载目录…');
    const result = await this.opts.store.loadRemaining((p) => {
      this.updateFooter(`正在加载目录… ${p.loadedPages}/${p.totalPages} 页`);
    });
    this.updateFooter(
      result === 'complete' ? undefined : '部分页加载失败：已加载可获取的全部目录',
    );
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
      : store.exhausted
        ? `已加载 ${total} 章 · ${store.loadedPageCount}/${store.totalPages} 页（含失败页）`
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
                if (this.query) this.search.resumeAfterRetry(this.query);
                else void store.retryFailedPages().then(() => {
                  this.updateFooter();
                  this.renderList(true);
                });
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
      if (this.query && this.search.busy) {
        this.progressEl.textContent = '搜索中…';
      } else {
        this.progressEl.textContent = store.isComplete
          ? ''
          : store.loadingPages.size > 0
            ? '加载中…'
            : '继续滑动自动加载';
      }
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
      this.renderSearchView();
      return;
    }
    const total = store.all.length;
    if (total === 0) {
      clear(this.listEl);
      this.listEl.style.height = '80px';
      this.listEl.appendChild(
        el('div', {
          class: 'empty-hint',
          text: store.isComplete
            ? '目录为空'
            : store.exhausted
              ? '目录加载失败，可点击下方“重试”'
              : '目录加载中…',
        }),
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

  /** 渲染搜索结果/状态。只渲染，绝不发起加载。 */
  private renderSearchView(): void {
    bump('renderSearchCount');
    const store = this.opts.store;
    const results = this.searchResults;
    const phase = this.searchPhase;
    clear(this.listEl);
    this.listEl.style.height = 'auto';
    this.renderedRange = { start: -1, end: -1 };

    if (results.length > 0) {
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
          text: `找到 ${results.length}${results.length >= TOC_SEARCH_LIMIT ? '+' : ''} 条${
            store.isComplete ? '' : ` · 目录 ${store.loadedPageCount}/${store.totalPages} 页`
          }`,
        }),
      );
      return;
    }

    // 无结果：按状态给出明确信息 + 可操作按钮，绝不自动重触发加载
    const note = el('div', { class: 'empty-hint' });
    if (phase.kind === 'debouncing') {
      note.appendChild(el('span', { text: '正在输入…' }));
    } else if (phase.kind === 'searching') {
      const p = phase as Extract<TocSearchPhase, { kind: 'searching' }>;
      note.appendChild(
        el(
          'span',
          {
            text:
              p.mode === 'probe'
                ? `正在定位章节位置…（已加载 ${p.loadedPages}/${p.totalPages} 页）`
                : `正在加载目录并搜索…（${p.loadedPages}/${p.totalPages} 页）`,
          },
        ),
      );
      note.appendChild(el('br'));
      note.appendChild(
        el('span', { class: 'loading-note', text: '输入框保持可用，可随时修改查询或关闭目录' }),
      );
    } else if (phase.kind === 'done' && !phase.complete) {
      note.appendChild(el('span', { text: '没有找到匹配的章节' }));
      if (phase.failedPages > 0) {
        note.appendChild(el('br'));
        note.appendChild(
          el('span', { class: 'loading-note', text: `${phase.failedPages} 页目录加载失败，当前结果基于已加载内容` }),
        );
        note.appendChild(
          el(
            'button',
            {
              class: 'btn small primary',
              type: 'button',
              onclick: () => {
                if (this.query) this.search.resumeAfterRetry(this.query);
              },
            },
            '重试失败页并继续搜索',
          ),
        );
      }
    } else if (phase.kind === 'cancelled') {
      note.appendChild(el('span', { text: '搜索已取消，重新输入以继续' }));
    } else {
      // done + complete（或 idle 兜底）
      note.appendChild(el('span', { text: '没有找到匹配的章节' }));
    }
    this.listEl.appendChild(note);
  }

  /** 供外部调用：写入查询并立即搜索（跳过 debounce） */
  searchInToc(query: string): void {
    this.searchInput.value = query;
    this.query = query.trim();
    bump('searchGeneration');
    if (this.query) {
      this.search.searchNow(this.query);
      this.renderSearchView();
    } else {
      this.search.setQuery('');
    }
  }
}
