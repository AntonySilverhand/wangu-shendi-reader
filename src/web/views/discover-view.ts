/**
 * 书源搜索页（#/search?q=&source=）与书籍详情面板。
 * 只在提交时搜索（源站自带限频，不做边输边搜）；结果不落盘。
 * 输入框也接受书籍/章节网址（万书阁、69书吧），本地解析出书号后直接打开，不经搜索。
 */
import { clear, el } from '../dom.ts';
import { renderCover } from '../cover.ts';
import { apiGet, ApiError } from '../store/remote.ts';
import { openSheet } from '../ui/sheet.ts';
import type { LibraryStore } from '../store/library.ts';
import type { SearchResponse } from '../../shared/api.ts';
import type { BookInfo, BookSummary, TocRangeResult } from '../../shared/source.ts';
import { downloadSourceBook, type DownloadProgress } from '../store/book-download.ts';
import type { LocalBookMeta } from '../store/txt.ts';
import {
  DEFAULT_SOURCE,
  isDownloadBookId,
  parseBookUrl,
  placeholderTitle,
  SOURCES,
  sourceMeta,
  sourceNameOf,
  sourceOfBook,
  type SourceId,
} from '../../shared/sources.ts';

export interface DiscoverDeps {
  library: LibraryStore;
  onBack: () => void;
  /** 开始/继续阅读：加入书架并打开 */
  onRead: (book: BookSummary | BookInfo) => void;
  onAdd: (book: BookSummary | BookInfo) => void;
  onRemove: (bookId: string) => void;
  /** 搜索词/书源变化时同步地址栏（便于返回/刷新） */
  onQueryChange: (q: string, source: SourceId) => void;
  /** 粘贴的是章节网址：直接打开该书该章 */
  onOpenUrl: (bookId: string, chapterId?: string) => void;
  /** 在书架上（在线书已加入 / 整本下载型已下载） */
  isOnShelf: (bookId: string) => boolean;
  /** 整本下载完成：登记为本机书 */
  onDownloaded: (meta: LocalBookMeta) => void;
  /** 打开已下载的书 */
  onOpenLocal: (bookId: string) => void;
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
  private lastSource: SourceId = DEFAULT_SOURCE;
  private source: SourceId = DEFAULT_SOURCE;
  private sourceSeg: HTMLElement;
  private subtitle: HTMLElement;
  private results: BookSummary[] = [];

  constructor(private deps: DiscoverDeps) {
    this.input = el('input', {
      type: 'search',
      id: 'discover-input',
      placeholder: '书名或作者，或粘贴书籍网址',
      // 允许粘贴网址；搜索词本身仍限 30 字（见 search）
      maxLength: 300,
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
    this.sourceSeg = el('div', { class: 'seg discover-sources', role: 'group', 'aria-label': '选择书源' });
    for (const s of SOURCES) {
      this.sourceSeg.appendChild(
        el(
          'button',
          {
            type: 'button',
            dataset: { source: s.id },
            'aria-pressed': String(s.id === this.source),
            title: s.host,
            onclick: () => this.selectSource(s.id, true),
          },
          s.name,
        ),
      );
    }
    this.subtitle = el('p');
    this.statusEl = el('p', { class: 'discover-status', role: 'status', 'aria-live': 'polite' });
    this.listEl = el('div', { class: 'discover-list', id: 'discover-results' });
    this.root = el(
      'main',
      { class: 'discover-root', id: 'discover-root', hidden: true },
      el(
        'div',
        { class: 'home-head' },
        el('div', null, el('h1', { text: '搜索书源' }), this.subtitle),
        el('button', { class: 'btn ghost', type: 'button', id: 'discover-back', onclick: () => deps.onBack() }, '返回书架'),
      ),
      this.sourceSeg,
      form,
      this.statusEl,
      this.listEl,
    );
    this.selectSource(DEFAULT_SOURCE, false);
  }

  /** 切换书源；用户点选且已有搜索词时立即在新书源重搜 */
  private selectSource(source: SourceId, byUser: boolean): void {
    this.source = source;
    for (const btn of this.sourceSeg.querySelectorAll('button')) {
      btn.setAttribute('aria-pressed', String(btn.dataset.source === source));
    }
    this.subtitle.textContent = `在${sourceMeta(source).name}中按书名或作者查找，也可粘贴书籍网址`;
    if (byUser && this.input.value.trim() && source !== this.lastSource) void this.search(this.input.value);
  }

  show(query: string | null, source: SourceId = DEFAULT_SOURCE): void {
    this.root.hidden = false;
    // 地址栏带搜索词时以其书源为准；无搜索词时保留用户当前选择
    if (query !== null && source !== this.source) this.selectSource(source, false);
    if (query !== null && (query !== this.lastQuery || source !== this.lastSource)) {
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
    const ref = parseBookUrl(q);
    if (ref) {
      this.input.blur();
      this.setStatus('');
      const source = sourceOfBook(ref.bookId);
      if (source && source !== this.source) this.selectSource(source, false);
      if (ref.chapterId && (!isDownloadBookId(ref.bookId) || this.deps.isOnShelf(ref.bookId))) {
        this.deps.onOpenUrl(ref.bookId, ref.chapterId);
      } else {
        const known = this.deps.library.get(ref.bookId);
        this.openBook(known ?? { id: ref.bookId, title: placeholderTitle(ref.bookId) });
      }
      return;
    }
    if (/^https?:\/\//i.test(q)) {
      this.setStatus(`暂不支持这个网址：目前支持${SOURCES.map((x) => x.name).join('、')}的书籍或章节网址`);
      return;
    }
    if (q.length > 30) {
      this.setStatus('搜索词过长（最多 30 字）');
      return;
    }
    this.input.blur();
    const source = this.source;
    if (!opts.fromRoute) this.deps.onQueryChange(q, source);
    this.controller?.abort();
    const controller = new AbortController();
    this.controller = controller;
    this.lastQuery = q;
    this.lastSource = source;
    this.submitBtn.disabled = true;
    clear(this.listEl);
    this.setStatus(
      navigator.onLine === false
        ? '离线状态无法搜索书源'
        : `正在${sourceMeta(source).name}搜索“${q}”…（源站限频，可能需要几秒）`,
    );
    if (navigator.onLine === false) {
      this.submitBtn.disabled = false;
      return;
    }
    try {
      const data = await apiGet<SearchResponse>(`/api/search?q=${encodeURIComponent(q)}&source=${source}`, {
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
      // 书源给出的中文提示（搜索词无效、人机验证、限频）原样展示，其余统一提示
      const message =
        err instanceof ApiError && /[\u4e00-\u9fff]/.test(err.message)
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
    this.setStatus(`${sourceMeta(this.lastSource).name}：“${this.lastQuery}”共 ${this.results.length} 条结果`);
    for (const book of this.results) {
      const onShelf = this.deps.isOnShelf(book.id);
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
        onShelf ? el('span', { class: 'badge', text: isDownloadBookId(book.id) ? '已下载' : '已在书架' }) : null,
      );
      this.listEl.appendChild(card);
    }
  }

  openBook(book: BookSummary | BookInfo): void {
    openBookSheet(book, this.deps);
  }
}

/** 书籍详情：先用已知信息展示，再后台取详情页补全简介（顺带预热目录第 1–2 页缓存） */
export function openBookSheet(
  initial: BookSummary | BookInfo,
  deps: Omit<DiscoverDeps, 'onBack' | 'onQueryChange' | 'onOpenUrl'>,
): void {
  let book: BookSummary | BookInfo = { ...initial };
  const body = el('div', { class: 'sheet-body book-sheet' });
  const controller = new AbortController();
  /** 整本下载型书源（爱下电子书）：没有“加入书架”，下载即入架 */
  const downloadMode = isDownloadBookId(book.id);
  let downloading: AbortController | null = null;
  let statusText = '';
  let statusEl: HTMLElement | null = null;
  const handle = openSheet({
    title: book.title,
    body,
    testId: 'book-sheet',
    onClose: () => {
      controller.abort();
      downloading?.abort();
    },
  });
  let introNote: HTMLElement | null = null;

  const setStatus = (text: string) => {
    statusText = text;
    if (statusEl) statusEl.textContent = text;
  };

  const downloadActions = (): HTMLElement[] => {
    const downloaded = deps.isOnShelf(book.id);
    const busy = downloading !== null;
    const out: HTMLElement[] = [];
    if (downloaded && !busy) {
      out.push(
        el(
          'button',
          {
            class: 'btn primary',
            type: 'button',
            id: 'book-read',
            onclick: () => {
              controller.abort();
              handle.close();
              deps.onOpenLocal(book.id);
            },
          },
          deps.hasProgress(book.id) ? '继续阅读' : '开始阅读',
        ),
      );
    }
    out.push(
      el(
        'button',
        {
          class: downloaded ? 'btn' : 'btn primary',
          type: 'button',
          id: 'book-download',
          disabled: busy,
          onclick: () => void startDownload(),
        },
        busy ? '下载中…' : downloaded ? '重新下载（更新）' : '下载整本',
      ),
    );
    if (busy) {
      out.push(el('button', { class: 'btn ghost', type: 'button', onclick: () => downloading?.abort() }, '取消'));
    } else if (downloaded) {
      out.push(el('button', { class: 'btn ghost danger', type: 'button', onclick: () => { deps.onRemove(book.id); render(); } }, '移出书架'));
    }
    return out;
  };

  const render = () => {
    clear(body);
    const onShelf = deps.isOnShelf(book.id);
    const meta = [
      book.author,
      book.category,
      'status' in book ? book.status : undefined,
      'size' in book ? book.size : undefined,
      sourceNameOf(book.id),
    ]
      .filter(Boolean)
      .join(' · ');
    const intro = book.intro;
    introNote = el('p', { class: 'book-intro', text: intro || '正在获取简介…' });
    statusEl = downloadMode
      ? el('p', {
          class: 'book-progress download-status',
          role: 'status',
          'aria-live': 'polite',
          text:
            statusText ||
            (onShelf
              ? '已下载到本机，可离线阅读'
              : `该书源需整本下载后阅读：使用站方提供的 TXT${estimateDownload(book)}，下载后可离线阅读`),
        })
      : null;
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
      ...(statusEl ? [statusEl] : []),
      el(
        'div',
        { class: 'home-actions' },
        ...(downloadMode
          ? downloadActions()
          : [
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
            ]),
      ),
    );
  };
  render();

  const tocPromise = apiGet<TocRangeResult>(`/api/toc?book=${encodeURIComponent(book.id)}&from=1&to=2`, {
    signal: controller.signal,
    retries: 1,
  });
  void tocPromise
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

  const startDownload = async (): Promise<void> => {
    if (downloading) return;
    const ctl = new AbortController();
    downloading = ctl;
    statusText = '正在获取目录…';
    render();
    try {
      // 目录用于对齐章节号与标题；取不到时按 TXT 标题切分
      const toc = await tocPromise.catch(() => null);
      const catalog = toc?.pages.find((p) => p.page === 1)?.entries ?? [];
      const result = await downloadSourceBook(book, catalog, {
        signal: ctl.signal,
        onProgress: (p) => setStatus(describeProgress(p)),
      });
      deps.onDownloaded(result.meta);
      const notes = [
        result.missing > 0 ? `目录中有 ${result.missing} 章在 TXT 里缺失` : '',
        result.aligned ? '' : '未能与目录对齐，章节按 TXT 标题切分',
      ].filter(Boolean);
      statusText = `已下载 ${result.meta.chapters} 章，可离线阅读${notes.length ? `（${notes.join('；')}）` : ''}`;
    } catch (err) {
      const aborted = ctl.signal.aborted || (err instanceof DOMException && err.name === 'AbortError');
      statusText = aborted ? '已取消下载' : `下载失败：${err instanceof Error ? err.message : String(err)}`;
    } finally {
      downloading = null;
      if (!controller.signal.aborted || deps.isOnShelf(book.id)) render();
    }
  };
}

/** 按字数估算压缩包体积（实测约每百万字 0.9MB）；省流量模式时提醒 */
function estimateDownload(book: BookSummary | BookInfo): string {
  const raw = 'size' in book ? book.size : undefined;
  const wan = raw ? Number.parseFloat(raw.replace(/[^\d.]/g, '')) : Number.NaN;
  const parts: string[] = [];
  if (Number.isFinite(wan) && /万字/.test(raw ?? '')) {
    const mb = (wan / 100) * 0.9;
    parts.push(`约 ${mb < 1 ? '1' : Math.round(mb)} MB`);
  }
  const conn = (navigator as Navigator & { connection?: { saveData?: boolean } }).connection;
  if (conn?.saveData) parts.push('当前为省流量模式');
  return parts.length ? `（${parts.join('，')}）` : '';
}

function describeProgress(p: DownloadProgress): string {
  const mb = (n: number) => (n / 1024 / 1024).toFixed(1);
  switch (p.phase) {
    case 'download':
      return `正在下载 ${mb(p.done)}${p.total ? ` / ${mb(p.total)}` : ''} MB…`;
    case 'unzip':
      return '正在解压…';
    case 'split':
      return '正在分章…';
    case 'save':
      return `正在保存 ${p.done} / ${p.total} 章…`;
  }
}

function stripUndefined<T extends object>(obj: T): Partial<T> {
  const out: Partial<T> = {};
  for (const [k, v] of Object.entries(obj)) if (v !== undefined && v !== '') (out as Record<string, unknown>)[k] = v;
  return out;
}
