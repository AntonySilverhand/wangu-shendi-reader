/**
 * 应用编排：路由、阅读流程、全局输入、状态持久化。
 */
import { clear, el, iconButton, on } from './dom.ts';
import { ReaderView } from './views/reader.ts';
import { TocView } from './views/toc-view.ts';
import { ChapterSearch } from './views/search-view.ts';
import { renderHome } from './views/home-view.ts';
import { openSettingsSheet, openDataSheet, openDownloadSheet, openAboutSheet, type SettingsDeps } from './views/settings-view.ts';
import { openBookmarksSheet } from './views/bookmarks-view.ts';
import { closeActiveSheet, hasActiveSheet } from './ui/sheet.ts';
import { hasBlockingOverlay, onOverlayChange } from './ui/overlay-state.ts';
import { showToast, confirmDialog } from './ui/toast.ts';
import { SettingsStore, applySettings } from './store/settings.ts';
import { isNativeDisplayAvailable, setNativeImmersive, onNativeInsetsChange } from './native-display.ts';
import { PersonalStore, type Bookmark, type ReadingPosition } from './store/personal.ts';
import { TocStore } from './store/toc.ts';
import { DownloadManager } from './store/download.ts';
import { loadChapterRecord, purgeOutdatedChapters, subscribeChapterUpdates } from './store/chapter.ts';
import { listChapterIds, clearBookContent, storageStats } from './store/db.ts';
import { importTxtFile, exportCachedTxt, listLocalBooks, type LocalBookMeta } from './store/txt.ts';
import { BOOK, type TocEntry } from '../shared/source.ts';
import { excerpt } from './format.ts';
import { bump } from './instrument.ts';
import { LayoutController } from './layout.ts';

const REMOTE_BOOK_ID = BOOK.id;

type View = 'home' | 'reader';

export class App {
  readonly settings = new SettingsStore();
  readonly personal = new PersonalStore();
  private toc: TocStore;
  private download: DownloadManager;
  private reader: ReaderView;
  private tocView: TocView;
  private search: ChapterSearch;

  private bookId: string = REMOTE_BOOK_ID;
  private localBooks: LocalBookMeta[] = listLocalBooks();
  private currentChapterId: string | null = null;
  private renderedChapterId: string | null = null;
  private loadingChapterId: string | null = null;
  private chapterGeneration = 0;
  private chapterController: AbortController | null = null;
  private currentEntry: TocEntry | null = null;
  private view: View = 'home';
  private cachedIds = new Set<string>();
  private prefetchTimer: ReturnType<typeof setTimeout> | null = null;
  private prefetchController: AbortController | null = null;
  private wakeLock: { release: () => Promise<void> } | null = null;
  private installPrompt: { prompt: () => Promise<void> } | null = null;
  private searchOpen = false;
  private disposed = false;

  private appEl!: HTMLElement;
  private homeContainer!: HTMLElement;
  private topbarTitleMain!: HTMLElement;
  private topbarTitleSub!: HTMLElement;
  private bookmarkBtn!: HTMLButtonElement;
  private netStatusEl!: HTMLElement;
  private railEl!: HTMLElement;
  private railProgress!: HTMLElement;
  private railLabel!: HTMLElement;
  private bottombarPrev!: HTMLButtonElement;
  private bottombarNext!: HTMLButtonElement;
  private bottombarBookmark!: HTMLButtonElement;

  constructor() {
    this.toc = new TocStore(this.bookId);
    this.download = this.createDownloadManager();
    this.layout = new LayoutController({
      onModeChange: (mode, prev) => {
        // 回到 compact：目录抽屉必须收起（否则覆盖整屏）
        if (mode === 'compact') this.tocView.close();
        this.syncImmersive();
        // 模式切换后布局稳定时恢复阅读位置（段落锚点不依赖像素）
        if (prev !== mode && this.view === 'reader') {
          requestAnimationFrame(() => {
            requestAnimationFrame(() => this.reader.refreshLayout());
          });
        }
      },
    });
    this.reader = new ReaderView({
      onOpenToc: () => this.tocView.toggle(),
      onOpenSettings: () => this.openSettings(),
      onOpenBookmarks: () => this.openBookmarks(),
      onOpenDownload: () => this.openDownload(),
      onOpenSearch: () => this.search.toggle(),
      onToggleBookmark: () => this.toggleBookmark(),
      isBookmarked: () => this.isCurrentBookmarked(),
      onNavigate: (id) => void this.openChapter(id),
      onPositionChange: (pos) => this.savePosition(pos),
      onRequestRetry: () => this.currentChapterId && void this.openChapter(this.currentChapterId, { force: true }),
    });
    this.tocView = new TocView({
      store: this.toc,
      onOpenChapter: (id) => {
        if (this.layout.current === 'compact') this.tocView.close();
        void this.openChapter(id);
      },
      onOpenTocData: () => this.openData(),
      getCurrentChapterId: () => this.currentChapterId,
      onOpenChange: () => this.syncImmersive(),
    });
    this.search = new ChapterSearch({
      onOpenChange: (open) => {
        this.reader.setSearchOpen(open);
        if (!open) this.reader.showBars();
        this.searchOpen = open;
        this.syncImmersive();
      },
    });
  }

  private createDownloadManager(): DownloadManager {
    return new DownloadManager(this.bookId, {
      getEntry: (index) => this.toc.entryAt(index),
      getEntryById: (id) => {
        const idx = this.toc.indexOfChapter(id);
        return idx >= 0 ? this.toc.entryAt(idx) : null;
      },
      isOnline: () => navigator.onLine !== false,
    });
  }

  async start(): Promise<void> {
    applySettings(this.settings.get());
    this.buildShell();
    this.layout.start();
    this.bindEvents();
    try {
      await this.toc.init();
      await this.refreshCachedIds(true);
      this.renderHomeView();
      if (this.toc.loadedPageCount === 0) {
        // 首页空闲时预取前几页目录，避免打开目录时空白
        const idle = (cb: () => void) =>
          'requestIdleCallback' in window
            ? (window as unknown as { requestIdleCallback: (cb: () => void) => void }).requestIdleCallback(cb)
            : setTimeout(cb, 1200);
        idle(() => void this.toc.loadRange(1, 4, { background: true }));
      }
      this.download.subscribeDownload(() => void this.refreshCachedIds());
      subscribeChapterUpdates((record) => {
        if (record.bookId !== this.bookId || record.chapterId !== this.currentChapterId) return;
        if (record.paragraphs.length <= this.reader.contentElement.childElementCount) return;
        const pos = this.reader.lastKnownPosition;
        void this.renderLoadedChapter(record.chapterId, record, pos);
        showToast('已静默补齐本章后续内容');
      });
      this.settings.subscribe(() => {
        this.reader.refreshLayout();
        this.updateWakeLock();
        this.syncImmersive();
      });
    } catch (err) {
      // 初始化失败（如 IndexedDB 不可用）不应阻止基本阅读
      console.error('[reader] 初始化部分失败', err);
    }
    void purgeOutdatedChapters().then((n) => {
      if (n > 0) {
        void this.refreshCachedIds();
        showToast(`已清理 ${n} 条旧版缓存，将自动重新获取`);
      }
    });
    this.applyRoute();
    if (this.layout.current !== 'compact' && window.innerWidth >= 1100) this.tocView.open();
    document.getElementById('app')?.setAttribute('aria-busy', 'false');
  }

  /* ------------------------------ 外壳 ------------------------------ */

  private buildShell(): void {
    this.appEl = document.getElementById('app')!;
    clear(this.appEl);
    this.appEl.dataset.view = 'home';
    this.appEl.dataset.bars = 'shown';

    this.topbarTitleMain = el('strong', { text: this.currentBookTitle() });
    this.topbarTitleSub = el('span', { text: '' });
    this.bookmarkBtn = iconButton({
      label: '添加书签',
      icon: 'bookmark',
      testId: 'bookmark-btn',
      onClick: () => this.toggleBookmark(),
    });

    const topbar = el(
      'header',
      { class: 'topbar', id: 'topbar' },
      iconButton({ label: '目录', icon: 'list', testId: 'toc-btn', onClick: () => this.tocView.toggle() }),
      el('div', { class: 'topbar-title' }, this.topbarTitleMain, this.topbarTitleSub),
      iconButton({ label: '本章内搜索', icon: 'search', testId: 'search-btn', onClick: () => this.search.toggle() }),
      this.bookmarkBtn,
      iconButton({ label: '设置', icon: 'settings', testId: 'settings-btn', onClick: () => this.openSettings() }),
    );

    this.bottombarPrev = el(
      'button',
      {
        class: 'icon-btn',
        type: 'button',
        'aria-label': '上一章',
        onclick: () => this.goRelative(-1),
      },
      this.bottombarIcon('left', '上一章'),
    ) as HTMLButtonElement;
    this.bottombarNext = el(
      'button',
      {
        class: 'icon-btn',
        type: 'button',
        'aria-label': '下一章',
        onclick: () => this.goRelative(1),
      },
      this.bottombarIcon('right', '下一章'),
    ) as HTMLButtonElement;
    this.bottombarBookmark = el(
      'button',
      {
        class: 'icon-btn',
        type: 'button',
        'aria-label': '书签',
        onclick: () => this.toggleBookmark(),
      },
      this.bottombarIcon('bookmark', '书签'),
    ) as HTMLButtonElement;
    const bottombar = el(
      'nav',
      { class: 'bottombar', 'aria-label': '阅读操作' },
      el(
        'button',
        { class: 'icon-btn', type: 'button', 'aria-label': '目录', onclick: () => this.tocView.toggle() },
        this.bottombarIcon('list', '目录'),
      ),
      this.bottombarPrev,
      this.bottombarBookmark,
      this.bottombarNext,
      el(
        'button',
        { class: 'icon-btn', type: 'button', 'aria-label': '设置', onclick: () => this.openSettings() },
        this.bottombarIcon('settings', '设置'),
      ),
    );

    this.netStatusEl = el('div', { class: 'net-status', id: 'net-status', role: 'status' });
    this.homeContainer = el('main', { class: 'home-root', id: 'home-root', hidden: true });

    this.appEl.appendChild(topbar);
    this.railProgress = el('div', { class: 'progress-fill', style: { width: '0%' } });
    this.railLabel = el('div', { class: 'loading-note', style: { textAlign: 'left' } });
    this.railEl = el(
      'aside',
      { class: 'side-rail', 'aria-label': '阅读进度' },
      el('strong', { text: '阅读进度', style: { fontSize: '13px' } }),
      el('div', { class: 'progress-track' }, this.railProgress),
      this.railLabel,
      el(
        'div',
        { class: 'row-actions' },
        el('button', { class: 'btn small', type: 'button', onclick: () => this.goRelative(-1) }, '上一章'),
        el('button', { class: 'btn small primary', type: 'button', onclick: () => this.goRelative(1) }, '下一章'),
      ),
      el('button', { class: 'btn small ghost', type: 'button', onclick: () => this.toggleBookmark() }, '书签'),
      el('button', { class: 'btn small ghost', type: 'button', onclick: () => this.tocView.toggle() }, '目录'),
    );
    this.appEl.appendChild(this.reader.root);
    this.appEl.appendChild(this.railEl);
    this.appEl.appendChild(this.tocView.panel);
    this.appEl.appendChild(bottombar);
    this.appEl.appendChild(this.homeContainer);
    this.appEl.appendChild(this.search.element);
    this.appEl.appendChild(this.netStatusEl);
    this.reader.mount();
  }

  private bottombarIcon(name: string, label: string): HTMLElement {
    const paths: Record<string, string> = {
      list: 'M4 6h16M4 12h16M4 18h16',
      left: 'M15 18l-6-6 6-6',
      right: 'M9 6l6 6-6 6',
      bookmark: 'M6 3h12v18l-6-4.5L6 21z',
      settings: 'M4 21v-7M4 10V3M12 21v-9M12 8V3M20 21v-5M20 12V3M1 14h6M9 8h6M17 16h6',
    };
    return el(
      'span',
      { style: { display: 'grid', justifyItems: 'center', gap: '2px' } },
      el('span', {
        'aria-hidden': 'true',
        html: `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="${paths[name] ?? ''}"/></svg>`,
        style: { display: 'grid' },
      }),
      el('span', { text: label }),
    );
  }

  /* ------------------------------ 事件 ------------------------------ */

  private bindEvents(): void {
    on(window, 'hashchange', () => this.applyRoute());
    on(window, 'online', () => this.updateNetStatus());
    on(window, 'offline', () => this.updateNetStatus());
    on(document, 'visibilitychange', () => {
      if (document.visibilityState === 'hidden') {
        this.reader.flushPosition();
        this.personal.flush();
        this.cancelPrefetch();
        void this.releaseWakeLock();
      } else {
        this.updateNetStatus();
        void this.updateWakeLock();
      }
    });
    on(window, 'pagehide', () => {
      this.reader.flushPosition();
      this.personal.flush();
    });
    on(window, 'keydown', (event) => this.handleKeydown(event));
    window.addEventListener('beforeinstallprompt', (event) => {
      event.preventDefault();
      this.installPrompt = event as unknown as { prompt: () => Promise<void> };
    });
    onNativeInsetsChange(() => {
      if (this.view === 'reader') this.reader.onInsetsChanged();
    });
    onOverlayChange(() => this.syncImmersive());
    // 焦点切换在同一任务结束后合并，避免输入框之间切换时系统栏闪动。
    const syncFocus = () => queueMicrotask(() => this.syncImmersive());
    on(document, 'focusin', syncFocus);
    on(document, 'focusout', syncFocus);
    history.scrollRestoration = 'manual';
    this.updateNetStatus();
  }

  private handleKeydown(event: KeyboardEvent): void {
    const target = event.target as HTMLElement | null;
    const editing =
      target &&
      (/^(input|textarea|select)$/i.test(target.tagName) || target.isContentEditable);
    if (event.key === 'Escape') {
      if (closeActiveSheet()) return;
      if (this.search.isOpen()) {
        this.search.close();
        return;
      }
      if (this.tocView.isOpen() && this.layout.current === 'compact') {
        this.tocView.close();
        return;
      }
      this.reader.showBars();
      return;
    }
    if (editing || event.metaKey || event.ctrlKey || event.altKey) return;
    if (hasActiveSheet()) return;
    switch (event.key) {
      case 'ArrowLeft':
        this.goRelative(-1);
        event.preventDefault();
        break;
      case 'ArrowRight':
        this.goRelative(1);
        event.preventDefault();
        break;
      case 't':
      case 'T':
        this.tocView.toggle();
        event.preventDefault();
        break;
      case 'f':
      case 'F':
        this.search.toggle();
        event.preventDefault();
        break;
      case 'b':
      case 'B':
        this.toggleBookmark();
        event.preventDefault();
        break;
      case 's':
      case 'S':
      case ',':
        this.openSettings();
        event.preventDefault();
        break;
      default:
        break;
    }
  }

  private updateNetStatus(): void {
    const online = navigator.onLine !== false;
    this.netStatusEl.classList.toggle('show', !online);
    this.netStatusEl.textContent = online ? '' : '离线：仅已下载章节可读';
  }

  private async updateWakeLock(): Promise<void> {
    const want = this.settings.get().keepScreenAwake && this.view === 'reader' && !document.hidden;
    const nav = navigator as Navigator & {
      wakeLock?: { request: (type: 'screen') => Promise<{ release: () => Promise<void> }> };
    };
    if (!want) {
      await this.releaseWakeLock();
      return;
    }
    if (this.wakeLock || !nav.wakeLock) return;
    try {
      this.wakeLock = await nav.wakeLock.request('screen');
    } catch {
      this.wakeLock = null;
    }
  }

  private async releaseWakeLock(): Promise<void> {
    if (!this.wakeLock) return;
    try {
      await this.wakeLock.release();
    } catch {
      /* 忽略 */
    }
    this.wakeLock = null;
  }

  /* ------------------------------ 路由 ------------------------------ */

  private applyRoute(): void {
    const hash = location.hash.replace(/^#/, '');
    const match = hash.match(/^\/read\/(\d{1,12})$/);
    if (match) {
      void this.openChapter(match[1]!, { push: false });
      return;
    }
    if (hash.startsWith('/toc')) {
      this.setView('reader');
      this.tocView.open();
      this.updateTopbar();
      return;
    }
    this.showHome();
  }

  private setView(view: View): void {
    this.view = view;
    this.appEl.dataset.view = view;
    this.homeContainer.hidden = view !== 'home';
    this.updateWakeLock();
    this.syncImmersive();
  }

  private showHome(): void {
    this.reader.flushPosition();
    this.chapterController?.abort();
    this.chapterGeneration++;
    this.loadingChapterId = null;
    this.cancelPrefetch();
    this.setView('home');
    this.tocView.close();
    this.renderHomeView();
  }

  private renderHomeView(): void {
    renderHome(this.homeContainer, {
      bookId: this.bookId,
      bookTitle: this.currentBookTitle(),
      bookAuthor: this.bookId === REMOTE_BOOK_ID ? BOOK.author : '本地导入',
      personal: this.personal,
      toc: this.toc,
      localBooks: this.localBooks,
      onContinue: () => this.continueReading(),
      onOpenChapter: (id) => void this.openChapter(id, { restorePosition: false }),
      onOpenToc: () => {
        this.setView('reader');
        this.tocView.open();
      },
      onStartBeginning: () => {
        void this.openFirstAvailable(() => 0, '正在从第一章开始加载…');
      },
      onOpenBookmarks: () => this.openBookmarks(),
      onOpenData: () => this.openData(),
      onOpenSettings: () => this.openSettings(),
      onSwitchBook: (id) => void this.openBook(id),
      onRemoveLocalBook: (id) => void this.removeLocalBook(id),
      onImportTxt: () => this.pickTxtFile(),
    });
  }

  private currentBookTitle(): string {
    if (this.bookId === REMOTE_BOOK_ID) return BOOK.title;
    return this.localBooks.find((b) => b.id === this.bookId)?.title ?? '本地书籍';
  }

  /* ---------------------------- 阅读流程 ---------------------------- */

  private async continueReading(): Promise<void> {
    const progress = this.personal.getProgress(this.bookId);
    if (progress) {
      void this.openChapter(progress.chapterId, { restorePosition: true });
      return;
    }
    await this.openFirstAvailable((index) => index, '正在获取章节列表…');
  }

  /** 目录未就绪时先展示加载态，再打开指定序号章节 */
  private async openFirstAvailable(
    pick: (index: number) => number,
    loadingText: string,
  ): Promise<void> {
    const existing = this.toc.entryAt(pick(0));
    if (existing) {
      void this.openChapter(existing.id, { restorePosition: false });
      return;
    }
    this.setView('reader');
    this.reader.renderLoading({ title: '加载中…', entry: null });
    this.reader.showBanner(loadingText, [
      { label: '打开目录', onClick: () => this.tocView.open() },
    ]);
    this.updateTopbar();
    try {
      await this.toc.loadRange(1, 2);
    } catch {
      /* 失败信息在下方统一处理 */
    }
    const entry = this.toc.entryAt(pick(0));
    if (entry) {
      await this.openChapter(entry.id, { restorePosition: false });
      return;
    }
    const offline = navigator.onLine === false;
    this.reader.renderError(
      offline ? '离线状态下无法获取目录。' : '暂时无法从书源获取章节列表。',
      offline ? '联网后可重试；已下载的章节仍可在目录中打开。' : '可点击重试，或稍后再试。',
    );
  }

  async openChapter(
    chapterId: string,
    opts: { restorePosition?: boolean; push?: boolean; force?: boolean; anchor?: { paragraph: number; offset: number } } = {},
  ): Promise<void> {
    bump('chapterOpens');
    const sameRendered = chapterId === this.renderedChapterId;
    const alreadyLoading = chapterId === this.loadingChapterId;
    if (opts.push !== false && location.hash !== `#/read/${chapterId}`) {
      location.hash = `#/read/${chapterId}`;
    }
    if (this.renderedChapterId && !sameRendered) this.reader.flushPosition();
    this.currentChapterId = chapterId;
    this.setView('reader');
    this.updateTopbar();
    // 已渲染或正在加载同一章：不重复请求（hashchange 会再次调用本方法）
    if (!opts.force && (sameRendered || alreadyLoading)) {
      if (sameRendered) {
        if (opts.anchor) {
          this.reader.restorePosition(opts.anchor);
          this.reader.flushPosition();
        }
        this.reader.showBars();
      }
      return;
    }
    if (document.activeElement instanceof HTMLElement) document.activeElement.blur();
    this.chapterController?.abort();
    this.cancelPrefetch();
    const controller = new AbortController();
    this.chapterController = controller;
    const generation = ++this.chapterGeneration;
    this.renderedChapterId = null;
    this.loadingChapterId = chapterId;
    const index = this.toc.indexOfChapter(chapterId);
    this.currentEntry = index >= 0 ? this.toc.entryAt(index) : null;
    if (this.layout.current === 'compact') this.tocView.close();

    const saved = this.personal.getProgress(this.bookId);
    const restore =
      opts.anchor ??
      (opts.restorePosition !== false && saved && saved.chapterId === chapterId
        ? { paragraph: saved.paragraph, offset: saved.offset }
        : null);
    this.reader.renderLoading({ entry: this.currentEntry });
    this.reader.setInitialPosition(restore);
    this.search.setContent(null);
    this.reader.hideBanner();

    const offline = navigator.onLine === false;
    try {
      const record = await loadChapterRecord(this.bookId, chapterId, { force: opts.force, signal: controller.signal });
      if (this.disposed || generation !== this.chapterGeneration || this.currentChapterId !== chapterId) {
        bump('chapterStaleAborts');
        return;
      }
      this.renderLoadedChapter(chapterId, record, restore);
    } catch (err) {
      if (this.disposed || generation !== this.chapterGeneration || this.currentChapterId !== chapterId) {
        bump('chapterStaleAborts');
        return;
      }
      const message = offline
        ? '当前处于离线状态，这一章还没有下载。'
        : err instanceof Error
          ? err.message
          : '网络或书源暂时不可用。';
      this.reader.renderError(message, offline ? '可在“下载章节”中提前缓存需要的章节。' : '可重试，或先从目录换一章。');
      this.setSearchContentNull();
    } finally {
      if (generation === this.chapterGeneration) {
        this.loadingChapterId = null;
        this.chapterController = null;
      }
    }
  }

  private renderLoadedChapter(
    chapterId: string,
    record: Awaited<ReturnType<typeof loadChapterRecord>>,
    restore: { paragraph: number; offset: number } | null,
  ): void {
    const index = this.toc.indexOfChapter(chapterId);
    const entry = index >= 0 ? this.toc.entryAt(index) : this.currentEntry;
    this.currentEntry = entry;
    this.renderedChapterId = chapterId;
    const prev = index > 0 ? this.toc.entryAt(index - 1) : null;
    const next = index >= 0 ? this.toc.entryAt(index + 1) : null;
    const prevId = prev?.id ?? record.prevId ?? null;
    const nextId = next?.id ?? record.nextId ?? null;

    this.reader.setInitialPosition(restore);
    this.reader.renderChapter({ record, entry, prevId, nextId }, { restore: restore !== null });
    this.savePosition(restore ?? { paragraph: 0, offset: 0 });
    this.search.setContent(this.reader.contentElement);
    if (record.source === 'local') {
      this.reader.showBanner('本地导入内容');
    } else if (record.missingPages && record.missingPages.length > 0) {
      this.reader.showBanner('本章后续内容未取到（源站波动）', [
        {
          label: '继续加载',
          onClick: () => void this.openChapter(chapterId, { force: true }),
        },
      ]);
    } else if (navigator.onLine === false) {
      this.reader.showBanner('离线阅读中（已缓存章节）');
    }
    this.updateTopbar();
    this.updateBottombar(prevId, nextId);
    this.personal.pushHistory(this.bookId, {
      chapterId,
      chapterTitle: entry?.displayTitle ?? record.title,
      chapterIndex: index >= 0 ? index : null,
    });
    this.schedulePrefetch(nextId);
    document.title = `${entry ? entry.displayTitle : record.title} · ${this.currentBookTitle()}`;
  }

  private setSearchContentNull(): void {
    this.search.setContent(null);
  }

  private updateTopbar(): void {
    this.topbarTitleMain.textContent = this.currentBookTitle();
    if (this.view === 'reader' && this.currentEntry) {
      this.topbarTitleSub.textContent = this.currentEntry.displayTitle;
    } else if (this.view === 'reader' && this.currentChapterId) {
      this.topbarTitleSub.textContent = `章节 ${this.currentChapterId}`;
    } else {
      this.topbarTitleSub.textContent = '干净阅读';
    }
    this.updateRail();
    const bookmarked = this.isCurrentBookmarked();
    this.bookmarkBtn.setAttribute('aria-label', bookmarked ? '取消书签' : '添加书签');
    this.bookmarkBtn.style.color = bookmarked ? 'var(--accent)' : '';
    this.bottombarBookmark.style.color = bookmarked ? 'var(--accent)' : '';
  }

  private updateRail(): void {
    const index = this.currentChapterId ? this.toc.indexOfChapter(this.currentChapterId) : -1;
    const total = Math.max(1, this.toc.mainCount);
    const pct = index >= 0 ? Math.min(100, Math.round(((index + 1) / total) * 100)) : 0;
    this.railProgress.style.width = `${pct}%`;
    this.railLabel.textContent = this.currentEntry
      ? `${this.currentEntry.displayTitle.slice(0, 18)} · 全书 ${pct}%`
      : '尚未开始阅读';
  }

  private updateBottombar(prevId: string | null, nextId: string | null): void {
    this.bottombarPrev.disabled = !prevId;
    this.bottombarNext.disabled = !nextId;
  }

  private goRelative(delta: number): void {
    const id = this.currentChapterId;
    if (!id) return;
    const index = this.toc.indexOfChapter(id);
    if (index < 0) return;
    const target = this.toc.entryAt(index + delta);
    if (target) void this.openChapter(target.id);
    else showToast(delta > 0 ? '已经是最后一章' : '已经是第一章');
  }

  /* --------------------------- 位置与书签 --------------------------- */

  private savePosition(pos: { paragraph: number; offset: number }): void {
    const chapterId = this.reader.currentChapterId;
    if (!chapterId) return;
    const index = this.toc.indexOfChapter(chapterId);
    const value: ReadingPosition = {
      chapterId,
      chapterIndex: index >= 0 ? index : null,
      paragraph: pos.paragraph,
      offset: pos.offset,
      updatedAt: Date.now(),
    };
    this.personal.setProgress(this.bookId, value, { silent: true });
  }

  private isCurrentBookmarked(): boolean {
    if (!this.currentChapterId) return false;
    const pos = this.reader.lastKnownPosition;
    const list = this.personal.getBookmarks(this.bookId);
    if (!list.length) return false;
    const paragraph = pos?.paragraph ?? 0;
    return list.some(
      (b) => b.chapterId === this.currentChapterId && Math.abs(b.paragraph - paragraph) <= 1,
    );
  }

  private toggleBookmark(): void {
    if (!this.currentChapterId || this.view !== 'reader') {
      showToast('进入阅读页后才能添加书签');
      return;
    }
    this.reader.flushPosition();
    const pos = this.reader.lastKnownPosition ?? { paragraph: 0, offset: 0 };
    const list = this.personal.getBookmarks(this.bookId);
    const existing = list.find(
      (b) => b.chapterId === this.currentChapterId && Math.abs(b.paragraph - pos.paragraph) <= 1,
    );
    if (existing) {
      this.personal.removeBookmark(this.bookId, existing.id);
      showToast(this.personal.storageHealthy ? '已取消书签' : '已取消书签，但保存失败（存储不可用）');
    } else {
      const paragraphs = this.reader.paragraphElements;
      const text = paragraphs[pos.paragraph]?.textContent ?? '';
      this.personal.addBookmark(this.bookId, {
        chapterId: this.currentChapterId,
        chapterTitle: this.currentEntry?.displayTitle ?? '',
        chapterIndex: this.toc.indexOfChapter(this.currentChapterId),
        paragraph: pos.paragraph,
        offset: pos.offset,
        excerpt: excerpt(text, 80),
      });
      showToast(
        this.personal.storageHealthy
          ? '已添加书签'
          : '书签已添加但保存失败：存储不可用，重启后可能丢失',
      );
    }
    this.updateTopbar();
  }

  /* ----------------------------- 预取 ----------------------------- */

  private schedulePrefetch(nextId: string | null): void {
    this.cancelPrefetch();
    if (!nextId || this.bookId !== REMOTE_BOOK_ID) return;
    if (!this.settings.get().prefetch) return;
    const conn = (navigator as Navigator & { connection?: { saveData?: boolean; effectiveType?: string } }).connection;
    if (conn?.saveData) return;
    if (conn?.effectiveType && /(^|-)2g$/.test(conn.effectiveType)) return;
    this.prefetchTimer = setTimeout(() => {
      this.prefetchTimer = null;
      if (document.hidden) return;
      this.prefetchController = new AbortController();
      void loadChapterRecord(this.bookId, nextId, { signal: this.prefetchController.signal, background: true })
        .then(() => this.refreshCachedIds())
        .catch(() => undefined);
    }, 1600);
  }

  private cancelPrefetch(): void {
    if (this.prefetchTimer) {
      clearTimeout(this.prefetchTimer);
      this.prefetchTimer = null;
    }
    this.prefetchController?.abort();
    this.prefetchController = null;
  }

  private cacheRefreshTimer: ReturnType<typeof setTimeout> | null = null;
  private layout: LayoutController;

  private async refreshCachedIds(immediate = false): Promise<void> {
    if (immediate) {
      if (this.cacheRefreshTimer) {
        clearTimeout(this.cacheRefreshTimer);
        this.cacheRefreshTimer = null;
      }
      await this.refreshCachedIdsNow();
      return;
    }
    if (this.cacheRefreshTimer) return;
    this.cacheRefreshTimer = setTimeout(() => {
      this.cacheRefreshTimer = null;
      void this.refreshCachedIdsNow();
    }, 1200);
  }

  private async refreshCachedIdsNow(): Promise<void> {
    try {
      const ids = await listChapterIds(this.bookId);
      this.cachedIds = ids;
      this.tocView.setCachedIds(ids);
    } catch {
      /* 忽略 */
    }
  }

  /* ----------------------------- 对话框 ----------------------------- */

  private settingsDeps(): SettingsDeps {
    return {
      settings: this.settings,
      personal: this.personal,
      toc: this.toc,
      download: this.download,
      bookId: this.bookId,
      bookTitle: this.currentBookTitle(),
      getCurrentChapterIndex: () => {
        if (!this.currentChapterId) return 0;
        const idx = this.toc.indexOfChapter(this.currentChapterId);
        return idx >= 0 ? idx : 0;
      },
      onLayoutChanged: () => this.reader.refreshLayout(),
      onContentCleared: () => void this.refreshCachedIds(true),
      onImportPersonal: (json, mode) => {
        const result = this.personal.importData(json, mode);
        if (result.settings) this.settings.replace({ ...this.settings.get(), ...result.settings });
        this.renderHomeView();
      },
      onExportPersonal: () => this.personal.exportData(this.settings.get()),
      onImportTxt: (file) => this.handleTxtImport(file),
      onExportTxt: (from, to) => this.handleTxtExport(from, to),
      onInstallPwa: this.installPrompt ? () => void this.installPrompt?.prompt() : undefined,
      onOpenAbout: () => this.openAbout(),
    };
  }

  private syncImmersive(): void {
    if (!isNativeDisplayAvailable()) return;
    const shouldImmersive =
      this.view === 'reader' &&
      this.settings.get().immersiveReading &&
      !hasBlockingOverlay() &&
      !(this.layout.current === 'compact' && this.tocView.isOpen()) &&
      !document.activeElement?.matches('input, textarea, select, [contenteditable="true"]') &&
      !this.searchOpen;
    setNativeImmersive(shouldImmersive);
  }

  private openSettings(): void {
    openSettingsSheet(this.settingsDeps());
  }

  private openData(): void {
    openDataSheet(this.settingsDeps());
  }

  private openDownload(): void {
    openDownloadSheet(this.settingsDeps());
  }

  private openAbout(): void {
    openAboutSheet(this.settingsDeps());
  }

  private openBookmarks(): void {
    openBookmarksSheet({
      personal: this.personal,
      bookId: this.bookId,
      onJump: (bookmark: Bookmark) => {
        void this.openChapter(bookmark.chapterId, {
          anchor: { paragraph: bookmark.paragraph, offset: bookmark.offset },
        });
      },
    });
  }

  /* ---------------------------- TXT 导入/导出 ---------------------------- */

  private pickTxtFile(): void {
    const input = el('input', { type: 'file', accept: '.txt,text/plain', style: { display: 'none' } }) as HTMLInputElement;
    input.addEventListener('change', () => {
      const file = input.files?.[0];
      input.remove();
      if (file) void this.handleTxtImport(file);
    });
    document.body.appendChild(input);
    input.click();
  }

  private async handleTxtImport(file: File): Promise<void> {
    showToast('正在解析 TXT…');
    const result = await importTxtFile(file);
    this.localBooks = listLocalBooks();
    showToast(`已导入《${result.title}》共 ${result.chapters} 章`);
    await this.switchBook(result.bookId);
  }

  private async handleTxtExport(_from: number, _to: number): Promise<void> {
    const { text, count } = await exportCachedTxt(this.bookId, this.toc.all);
    if (count === 0) {
      showToast('还没有可导出的缓存章节');
      return;
    }
    const blob = new Blob([text], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = el('a', { href: url, download: `${this.currentBookTitle()}-已缓存-${count}章.txt` });
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    showToast(`已导出 ${count} 章`);
  }

  /* ---------------------------- 书籍切换 ---------------------------- */

  /** 打开某本书：切换后继续上次进度，无进度则读第一章 */
  private async openBook(bookId: string): Promise<void> {
    if (bookId !== this.bookId) await this.switchBook(bookId);
    const progress = this.personal.getProgress(bookId);
    if (progress) {
      await this.openChapter(progress.chapterId, { restorePosition: true });
      return;
    }
    await this.openFirstAvailable(() => 0, '正在打开书籍…');
  }

  private async switchBook(bookId: string): Promise<void> {
    if (bookId === this.bookId) {
      this.showHome();
      return;
    }
    closeActiveSheet();
    this.reader.flushPosition();
    this.chapterController?.abort();
    this.chapterGeneration++;
    this.cancelPrefetch();
    this.bookId = bookId;
    this.currentChapterId = null;
    this.renderedChapterId = null;
    this.loadingChapterId = null;
    this.currentEntry = null;
    this.tocView.close();
    this.toc = new TocStore(bookId);
    this.toc.offline = bookId !== REMOTE_BOOK_ID;
    this.tocView = new TocView({
      store: this.toc,
      onOpenChapter: (id) => {
        if (this.layout.current === 'compact') this.tocView.close();
        void this.openChapter(id);
      },
      onOpenTocData: () => this.openData(),
      getCurrentChapterId: () => this.currentChapterId,
      onOpenChange: () => this.syncImmersive(),
    });
    const oldPanel = document.getElementById('toc-panel');
    oldPanel?.replaceWith(this.tocView.panel);
    this.download = this.createDownloadManager();
    await this.toc.init();
    await this.refreshCachedIds(true);
    this.renderHomeView();
    location.hash = '';
    this.showHome();
  }

  private async removeLocalBook(bookId: string): Promise<void> {
    const ok = await confirmDialog('移除这本本地导入的书？其缓存正文将一并删除。');
    if (!ok) return;
    await clearBookContent(bookId);
    this.localBooks = this.localBooks.filter((b) => b.id !== bookId);
    try {
      localStorage.setItem('reader.localbooks.v1', JSON.stringify(this.localBooks));
    } catch {
      /* 忽略 */
    }
    if (this.bookId === bookId) await this.switchBook(REMOTE_BOOK_ID);
    else this.renderHomeView();
    showToast('已移除');
  }

  /** 供调试/测试：统计当前状态 */
  async debugStats(): Promise<Record<string, unknown>> {
    const storage = await storageStats(this.bookId);
    return {
      bookId: this.bookId,
      view: this.view,
      chapterId: this.currentChapterId,
      tocLoaded: this.toc.mainCount,
      tocComplete: this.toc.isComplete,
      cached: this.cachedIds.size,
      storage,
      settings: this.settings.get(),
    };
  }
}
