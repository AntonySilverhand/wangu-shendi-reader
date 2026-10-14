/**
 * 书源搜索页（#/search?q=）与书籍详情面板。
 * 只在提交时搜索（源站自带约 5 秒限频，不做边输边搜）；结果不落盘。
 */
import { clear, el } from '../dom.ts';
import { renderCover } from '../cover.ts';
import { apiGet, ApiError } from '../store/remote.ts';
import { openSheet } from '../ui/sheet.ts';
import type { LibraryStore } from '../store/library.ts';
import type { SearchResponse } from '../../shared/api.ts';
import type { BookInfo, BookSummary, TocRangeResult } from '../../shared/source.ts';

export interface DiscoverDeps {
  library: LibraryStore;
  onBack: () => void;
  /** 开始/继续阅读：加入书架并打开 */
  onRead: (book: BookSummary | BookInfo) => void;
  onAdd: (book: BookSummary | BookInfo) => void;
  onRemove: (bookId: string) => void;
  /** 搜索词变化时同步地址栏（便于返回/刷新） */
  onQueryChange: (q: string) => void;
  hasProgress: (bookId: string) => boolean;
}

export class DiscoverView {
  readonly root: HTMLElement;
  private input: HTMLInputElement;
  private submitBtn: HTMLButtonElement;
  private statusEl: HTMLElement;
  private listEl: HTMLElement;
  private controller: AbortController | null = null;
  private lastQuery = '';
  private results: BookSummary[] = [];

  constructor(private deps: DiscoverDeps) {
    this.input = el('input', {
      type: 'search',
      id: 'discover-input',
      placeholder: '书名或作者，宁少字勿错字',
      maxLength: 30,
      enterKeyHint: 'search',
      autocomplete: 'off',
      'aria-label': '搜索书源',
    }) as HTMLInputElement;
    this.submitBtn = el('button', { class: 'btn primary', type: 'submit', id: 'discover-submit' }, '搜索') as HTMLButtonElement;
    const form = el(
      'form',
      { class: 'discover-form', role: 'search' },
      this.input,
      this.submitBtn,
    );
    form.addEventListener('submit', (event) => {
      event.preventDefault();
      void this.search(this.input.value);
    });
    this.statusEl = el('p', { class: 'discover-status', role: 'status', 'aria-live': 'polite' });
    this.listEl = el('div', { class: 'discover-list', id: 'discover-results' });
    this.root = el(
      'main',
      { class: 'discover-root', id: 'discover-root', hidden: true },
      el(
        'div',
        { class: 'home-head' },
        el('div', null, el('h1', { text: '搜索书源' }), el('p', { text: '在万书阁中按书名或作者查找' })),
        el('button', { class: 'btn ghost', type: 'button', id: 'discover-back', onclick: () => deps.onBack() }, '返回书架'),
      ),
      form,
      this.statusEl,
      this.listEl,
    );
  }

  show(query: string | null): void {
    this.root.hidden = false;
    if (query !== null && query !== this.lastQuery) {
      this.input.value = query;
      void this.search(query, { fromRoute: true });
    } else {
      this.renderResults();
      if (!this.lastQuery) requestAnimationFrame(() => this.input.focus({ preventScroll: true }));
    }
  }

  hide(): void {
    this.root.hidden = true;
  }

  async search(raw: string, opts: { fromRoute?: boolean } = {}): Promise<void> {
    const q = raw.replace(/\s+/g, ' ').trim();
    if (!q) {
      this.setStatus('请输入书名或作者');
      return;
    }
    if (q.length > 30) {
      this.setStatus('搜索词过长（最多 30 字）');
      return;
    }
    this.input.blur();
    if (!opts.fromRoute) this.deps.onQueryChange(q);
    this.controller?.abort();
    const controller = new AbortController();
    this.controller = controller;
    this.lastQuery = q;
    this.submitBtn.disabled = true;
    clear(this.listEl);
    this.setStatus(navigator.onLine === false ? '离线状态无法搜索书源' : `正在搜索“${q}”…（源站限频，可能需要几秒）`);
    if (navigator.onLine === false) {
      this.submitBtn.disabled = false;
      return;
    }
    try {
      const data = await apiGet<SearchResponse>(`/api/search?q=${encodeURIComponent(q)}`, {
        signal: controller.signal,
        retries: 1,
        timeoutMs: 30_000,
      });
      if (controller !== this.controller) return;
      this.results = data.results;
      this.renderResults();
    } catch (err) {
      if (controller !== this.controller || controller.signal.aborted) return;
      this.results = [];
      const message =
        err instanceof ApiError && err.status === 400
          ? err.message
          : '书源暂时不可用或搜索太频繁，请稍后再试';
      this.setStatus(message);
    } finally {
      if (controller === this.controller) {
        this.submitBtn.disabled = false;
        this.controller = null;
      }
    }
  }

  /** 书架变化后刷新“已在书架”标记 */
  refresh(): void {
    if (!this.root.hidden) this.renderResults();
  }

  private setStatus(text: string): void {
    this.statusEl.textContent = text;
  }

  private renderResults(): void {
    clear(this.listEl);
    if (!this.lastQuery) {
      this.setStatus('');
      return;
    }
    if (this.results.length === 0) {
      this.setStatus(`没有找到“${this.lastQuery}”相关的书`);
      return;
    }
    this.setStatus(`“${this.lastQuery}”共 ${this.results.length} 条结果`);
    for (const book of this.results) {
      const onShelf = this.deps.library.has(book.id);
      const meta = [book.author, book.category].filter(Boolean).join(' · ');
      const card = el(
        'button',
        {
          class: 'discover-item',
          type: 'button',
          dataset: { bookId: book.id },
          onclick: () => this.openBook(book),
        },
        renderCover(book.id, book.title, { small: true }),
        el(
          'span',
          { class: 'discover-info' },
          el('strong', { text: book.title }),
          meta ? el('span', { class: 'discover-meta', text: meta }) : null,
          book.latestChapter ? el('span', { class: 'discover-meta', text: `最新：${book.latestChapter}` }) : null,
          book.intro ? el('span', { class: 'discover-intro', text: book.intro }) : null,
        ),
        onShelf ? el('span', { class: 'badge', text: '已在书架' }) : null,
      );
      this.listEl.appendChild(card);
    }
  }

  openBook(book: BookSummary | BookInfo): void {
    openBookSheet(book, this.deps);
  }
}

/** 书籍详情：先用已知信息展示，再后台取详情页补全简介（顺带预热目录第 1–2 页缓存） */
export function openBookSheet(initial: BookSummary | BookInfo, deps: Omit<DiscoverDeps, 'onBack' | 'onQueryChange'>): void {
  let book: BookSummary | BookInfo = { ...initial };
  const body = el('div', { class: 'sheet-body book-sheet' });
  const controller = new AbortController();
  const handle = openSheet({ title: book.title, body, testId: 'book-sheet', onClose: () => controller.abort() });
  let introNote: HTMLElement | null = null;

  const render = () => {
    clear(body);
    const onShelf = deps.library.has(book.id);
    const meta = [book.author, book.category, 'status' in book ? book.status : undefined]
      .filter(Boolean)
      .join(' · ');
    const intro = book.intro;
    introNote = el('p', { class: 'book-intro', text: intro || '正在获取简介…' });
    body.append(
      el(
        'div',
        { class: 'book-sheet-head' },
        renderCover(book.id, book.title),
        el(
          'div',
          { class: 'book-info' },
          el('h2', { text: book.title }),
          meta ? el('p', { class: 'author', text: meta }) : null,
          book.latestChapter ? el('p', { class: 'book-progress', text: `最新：${book.latestChapter}` }) : null,
        ),
      ),
      introNote,
      el(
        'div',
        { class: 'home-actions' },
        el(
          'button',
          {
            class: 'btn primary',
            type: 'button',
            id: 'book-read',
            onclick: () => {
              controller.abort();
              handle.close();
              deps.onRead(book);
            },
          },
          deps.hasProgress(book.id) ? '继续阅读' : '开始阅读',
        ),
        onShelf
          ? el('button', { class: 'btn ghost danger', type: 'button', onclick: () => { deps.onRemove(book.id); render(); } }, '移出书架')
          : el('button', { class: 'btn', type: 'button', id: 'book-add', onclick: () => { deps.onAdd(book); render(); } }, '加入书架'),
      ),
    );
  };
  render();

  void apiGet<TocRangeResult>(`/api/toc?book=${encodeURIComponent(book.id)}&from=1&to=2`, {
    signal: controller.signal,
    retries: 1,
  })
    .then((res) => {
      if (controller.signal.aborted || !res.book) {
        if (introNote && !book.intro) introNote.textContent = '暂无简介';
        return;
      }
      book = { ...book, ...stripUndefined(res.book) };
      deps.library.refresh(res.book);
      render();
      if (introNote && !book.intro) introNote.textContent = '暂无简介';
    })
    .catch(() => {
      if (introNote && !book.intro) introNote.textContent = '简介获取失败（不影响阅读）';
    });
}

function stripUndefined<T extends object>(obj: T): Partial<T> {
  const out: Partial<T> = {};
  for (const [k, v] of Object.entries(obj)) if (v !== undefined && v !== '') (out as Record<string, unknown>)[k] = v;
  return out;
}
