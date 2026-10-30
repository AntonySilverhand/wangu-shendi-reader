/**
 * 应用编排：路由、阅读流程、全局输入、状态持久化。
 */
import { clear, el, iconButton, on } from './dom.ts';
import { ReaderView } from './views/reader.ts';
import { TocView } from './views/toc-view.ts';
import { ChapterSearch } from './views/search-view.ts';
import { renderHome, type ShelfBook } from './views/home-view.ts';
import { DiscoverView } from './views/discover-view.ts';
import { openSettingsSheet, openDataSheet, openDownloadSheet, openAboutSheet, type SettingsDeps } from './views/settings-view.ts';
import { openBookmarksSheet } from './views/bookmarks-view.ts';
import { closeActiveSheet, hasActiveSheet } from './ui/sheet.ts';
import { hasBlockingOverlay, onOverlayChange } from './ui/overlay-state.ts';
import { showToast, confirmDialog, closeActiveDialog } from './ui/toast.ts';
import { SettingsStore, applySettings } from './store/settings.ts';
import { isNativeDisplayAvailable, setNativeImmersive, onNativeInsetsChange } from './native-display.ts';
import { PersonalStore, type Bookmark, type ReadingPosition } from './store/personal.ts';
import { TocStore } from './store/toc.ts';
import { DownloadManager } from './store/download.ts';
import { loadChapterRecord, purgeOutdatedChapters, setCleanSiteText, subscribeChapterUpdates, CHAPTER_CACHE_VERSION } from './store/chapter.ts';
import { listChapterIds, clearBookContent, deleteToc, type ChapterRecord } from './store/db.ts';
import { importTxtFile, exportCachedTxt, listLocalBooks, TXT_ACCEPT, type LocalBookMeta } from './store/txt.ts';
import { saveTextFile } from './save-file.ts';
import { setNativeKeepScreenOn } from './native-display.ts';
import { LibraryStore, isLocalBookId, isRemoteBookId } from './store/library.ts';
import { DEFAULT_BOOK, type BookInfo, type BookSummary, type TocEntry } from '../shared/source.ts';
import { DEFAULT_SOURCE, isDownloadBookId, isSourceId, placeholderTitle, sourceNameOf, type SourceId } from '../shared/sources.ts';
import { excerpt, formatTime } from './format.ts';
import { LayoutController } from './layout.ts';
import { cleanParagraphs, EMPTY_CHAPTER_NOTE } from '../shared/text-clean.ts';
import { altOnlineSources, fetchAltChapter } from './store/alt-source.ts';

const LOCAL_BOOKS_KEY = 'reader.localbooks.v1';

type View = 'home' | 'reader' | 'discover';

export class App {
  readonly settings = new SettingsStore();
  readonly personal = new PersonalStore();
  readonly library = new LibraryStore();
  private toc: TocStore;
  private discover: DiscoverView;
  private download: DownloadManager;
  private reader: ReaderView;
  private tocView: TocView;
  private search: ChapterSearch;

  private localBooks: LocalBookMeta[] = listLocalBooks();
  private bookId: string = this.initialBookId();
  private currentChapterId: string | null = null;
  private renderedChapterId: string | null = null;
  private loadingChapterId: string | null = null;
  private chapterGeneration = 0;
  private chapterController: AbortController | null = null;
  private currentEntry: TocEntry | null = null;
  private view: View = 'home';
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
    this.toc = this.createTocStore(this.bookId);
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
      onGoHome: () => this.goHome(),
    });
    this.tocView = this.createTocView();
    this.discover = new DiscoverView({
      library: this.library,
      onBack: () => {
        location.hash = '';
      },
      onRead: (book) => void this.readFromSource(book),
      onAdd: (book) => {
        this.library.upsert(book);
        showToast(`已加入书架：${book.title}`);
      },
      onRemove: (bookId) => void this.removeBook(bookId),
      onQueryChange: (q, source) => {
        const target = `#/search?q=${encodeURIComponent(q)}${source === DEFAULT_SOURCE ? '' : `&source=${source}`}`;
        if (location.hash !== target) location.hash = target;
      },
      onOpenUrl: (bookId, chapterId) => {
        location.hash = chapterId ? `#/book/${bookId}/read/${chapterId}` : `#/book/${bookId}`;
      },
      isOnShelf: (bookId) => this.bookExists(bookId),
      onDownloaded: (meta) => {
        this.localBooks = listLocalBooks();
        showToast(`已下载《${meta.title}》共 ${meta.chapters} 章`);
        // 正在读的就是这本书：重建目录（章节可能有增减）
        if (meta.id === this.bookId) void this.activateBook(meta.id);
        this.discover.refresh();
      },
      onOpenLocal: (bookId) => void this.openBook(bookId),
      hasProgress: (bookId) => this.personal.getProgress(bookId) !== null,
    });
    this.library.subscribe(() => {
      this.discover.refresh();
      if (this.view === 'home') this.renderHomeView();
      this.updateTopbar();
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

  /** 启动时打开哪本书：上次的书 → 书架第一本 → 预置书 */
  private initialBookId(): string {
    const active = this.library.activeBookId;
    if (active && this.bookExists(active)) return active;
    const first = this.shelfBooks()[0];
    return first?.id ?? DEFAULT_BOOK.id;
  }

  private bookExists(bookId: string): boolean {
    if (isRemoteBookId(bookId)) return this.library.has(bookId);
    return this.localBooks.some((b) => b.id === bookId);
  }

  /** 书架：远程书 + 本地导入书，按最近阅读（无则加入时间）倒序 */
  private shelfBooks(): ShelfBook[] {
    const sortKey = (id: string, fallback: number) => this.personal.getProgress(id)?.updatedAt ?? fallback;
    const remote: ShelfBook[] = this.library.list().map((b) => ({
      id: b.id,
      title: b.title,
      author: b.author,
      local: false,
      sortKey: sortKey(b.id, b.addedAt),
    }));
    const local: ShelfBook[] = this.localBooks.map((b) => {
      const source = sourceNameOf(b.id);
      return {
        id: b.id,
        title: b.title,
        // 书源整本下载的书：作者 · 书源；TXT 导入：作者 · 本地导入（文件头没有作者时只写本地导入）
        author: source ? `${b.author || '佚名'} · ${source}` : b.author ? `${b.author} · 本地导入` : '本地导入',
        local: true,
        sortKey: sortKey(b.id, b.importedAt),
        note: `${b.chapters} 章 · ${formatTime(b.importedAt)}${source ? '下载' : '导入'}`,
      };
    });
    return [...remote, ...local].sort((a, b) => b.sortKey - a.sortKey);
  }

  private createTocStore(bookId: string): TocStore {
    const toc = new TocStore(bookId);
    // 本地导入书籍：目录只读 IndexedDB，不访问网络
    toc.offline = !isRemoteBookId(bookId);
    toc.onBookInfo = (info: BookInfo) => this.library.refresh(info);
    return toc;
  }

  private createTocView(): TocView {
    return new TocView({
      store: this.toc,
      onOpenChapter: (id) => {
        if (this.layout.current === 'compact') this.tocView.close();
        void this.openChapter(id);
      },
      onOpenTocData: () => this.openData(),
      getCurrentChapterId: () => this.currentChapterId,
      onOpenChange: () => this.syncImmersive(),
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
    setCleanSiteText(this.settings.get().cleanSiteText);
    this.buildShell();
    this.layout.start();
    this.bindEvents();
    try {
      await this.toc.init();
      await this.refreshCachedIds(true);
      this.renderHomeView();
      if (this.toc.loadedPageCount === 0 && isRemoteBookId(this.bookId) && this.bookExists(this.bookId)) {
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
      let cleaning = this.settings.get().cleanSiteText;
      this.settings.subscribe((s) => {
        if (s.cleanSiteText !== cleaning) {
          cleaning = s.cleanSiteText;
          setCleanSiteText(cleaning);
          void this.reloadCurrentChapterView();
        }
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
    this.library.setActive(this.bookId);
    await this.applyRoute();
    if (this.layout.current !== 'compact' && window.innerWidth >= 1100 && this.view === 'reader') this.tocView.open();
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
      iconButton({ label: '书架', icon: 'home', testId: 'home-btn', onClick: () => this.goHome() }),
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
    this.appEl.appendChild(this.discover.root);
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
    on(window, 'hashchange', () => void this.applyRoute());
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
    // Android 壳：WebView 没有 Screen Wake Lock API，改用窗口 FLAG_KEEP_SCREEN_ON
    if (setNativeKeepScreenOn(want)) return;
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
    setNativeKeepScreenOn(false);
    if (!this.wakeLock) return;
    try {
      await this.wakeLock.release();
    } catch {
      /* 忽略 */
    }
    this.wakeLock = null;
  }

  /* ------------------------------ 路由 ------------------------------ */

  /**
   * 路由：
   *  #/book/{bookId}/read/{chapterId}  阅读
   *  #/book/{bookId}/toc               目录
   *  #/book/{bookId}                   切到该书并回书架
   *  #/search?q=&source=               书源搜索（source 缺省为万书阁）
   *  #/read/{chapterId}、#/toc         旧链接（预置书 / 当前书）
   */
  private async applyRoute(): Promise<void> {
    const hash = location.hash.replace(/^#/, '');
    const search = hash.match(/^\/search(?:\?q=([^&]*)(?:&source=([a-z0-9]{1,20}))?)?$/);
    if (search) {
      let q: string | null = null;
      try {
        q = search[1] !== undefined ? decodeURIComponent(search[1]) : null;
      } catch {
        q = null;
      }
      const source: SourceId = isSourceId(search[2]) ? search[2] : DEFAULT_SOURCE;
      this.showDiscover(q, source);
      return;
    }
    const bookRoute = hash.match(/^\/book\/([A-Za-z0-9-]{1,40})(\/read\/(\d{1,12})|\/toc)?\/?$/);
    const legacyRead = hash.match(/^\/read\/(\d{1,12})$/);
    if (bookRoute || legacyRead) {
      const bookId = bookRoute ? bookRoute[1]! : DEFAULT_BOOK.id;
      if (!(await this.ensureBook(bookId))) {
        showToast('找不到这本书');
        location.hash = '';
        return;
      }
      const chapterId = bookRoute ? bookRoute[3] : legacyRead![1];
      if (chapterId) {
        void this.openChapter(chapterId, { push: false });
        if (legacyRead) history.replaceState(null, '', `#/book/${bookId}/read/${chapterId}`);
      } else if (bookRoute?.[2] === '/toc') {
        this.openTocView();
      } else {
        this.showHome();
      }
      return;
    }
    if (hash.startsWith('/toc')) {
      this.openTocView();
      return;
    }
    this.showHome();
  }

  private openTocView(): void {
    this.setView('reader');
    this.tocView.open();
    this.updateTopbar();
  }

  /** 让 bookId 成为当前书（不改变视图）。远程书不在书架上时以占位信息加入，目录加载后补全。 */
  private async ensureBook(bookId: string): Promise<boolean> {
    if (isRemoteBookId(bookId)) {
      if (!this.library.has(bookId)) this.library.upsert({ id: bookId, title: placeholderTitle(bookId) });
    } else if (!isLocalBookId(bookId) || !this.localBooks.some((b) => b.id === bookId)) {
      return false;
    }
    if (bookId !== this.bookId) await this.activateBook(bookId);
    return true;
  }

  private goHome(): void {
    if (location.hash === '' || location.hash === '#') this.showHome();
    else location.hash = '';
  }

  private showDiscover(query: string | null, source: SourceId = DEFAULT_SOURCE): void {
    this.reader.flushPosition();
    this.chapterController?.abort();
    this.chapterGeneration++;
    this.loadingChapterId = null;
    this.cancelPrefetch();
    this.tocView.close();
    this.setView('discover');
    this.discover.show(query, source);
  }

  private openDiscover(): void {
    location.hash = '#/search';
  }

  private setView(view: View): void {
    this.view = view;
    this.appEl.dataset.view = view;
    this.homeContainer.hidden = view !== 'home';
    if (view === 'discover') this.discover.root.hidden = false;
    else this.discover.hide();
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
      bookId: this.bookExists(this.bookId) ? this.bookId : null,
      bookTitle: this.currentBookTitle(),
      bookAuthor: this.currentBookAuthor(),
      personal: this.personal,
      toc: this.toc,
      shelf: this.shelfBooks(),
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
      onOpenDiscover: () => this.openDiscover(),
      onSwitchBook: (id) => void this.openBook(id),
      onRemoveBook: (id) => void this.removeBook(id),
      onImportTxt: () => this.pickTxtFile(),
    });
  }

  private currentBookTitle(): string {
    if (isRemoteBookId(this.bookId)) return this.library.get(this.bookId)?.title ?? placeholderTitle(this.bookId);
    return this.localBooks.find((b) => b.id === this.bookId)?.title ?? (isLocalBookId(this.bookId) ? placeholderTitle(this.bookId) : '本地书籍');
  }

  private currentBookAuthor(): string {
    if (isRemoteBookId(this.bookId)) return this.library.get(this.bookId)?.author || '佚名';
    if (isDownloadBookId(this.bookId)) return this.localBooks.find((b) => b.id === this.bookId)?.author || '佚名';
    return this.localBooks.find((b) => b.id === this.bookId)?.author || '本地导入';
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
      offline ? '联网后可重试；已下载的章节仍可在目录中打开。' : this.toc.lastError ?? '可点击重试，或稍后再试。',
    );
  }

  /** “清理站点广告”切换后，用同一位置重新渲染当前章（存储的是原文，这里只是换一种展示） */
  private async reloadCurrentChapterView(): Promise<void> {
    const chapterId = this.currentChapterId;
    if (this.view !== 'reader' || !chapterId) return;
    try {
      const record = await loadChapterRecord(this.bookId, chapterId);
      await this.renderLoadedChapter(chapterId, record, this.reader.lastKnownPosition);
    } catch {
      /* 章节当前取不到（离线/加载中）：保持现有显示，下次打开章节时按新设置生效 */
    }
  }

  async openChapter(
    chapterId: string,
    opts: { restorePosition?: boolean; push?: boolean; force?: boolean; anchor?: { paragraph: number; offset: number } } = {},
  ): Promise<void> {
    const sameRendered = chapterId === this.renderedChapterId;
    const alreadyLoading = chapterId === this.loadingChapterId;
    const route = `#/book/${this.bookId}/read/${chapterId}`;
    if (opts.push !== false && location.hash !== route) {
      location.hash = route;
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
        return;
      }
      this.renderLoadedChapter(chapterId, record, restore);
    } catch (err) {
      if (this.disposed || generation !== this.chapterGeneration || this.currentChapterId !== chapterId) {
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
    // 章首提示只用于影响阅读的异常（内容不全）；“本地导入”“离线阅读中”之类的状态说明不再显示——
    // 带边框的提示框像个按钮，用户反馈会误以为是导入入口
    if (record.restricted) {
      this.reader.showBanner('源站仅公开本章部分内容（需源站会员），本阅读器不绕过限制');
    } else if (record.missingPages && record.missingPages.length > 0) {
      this.reader.showBanner('本章后续内容未取到（源站波动）', [
        {
          label: '继续加载',
          onClick: () => void this.openChapter(chapterId, { force: true }),
        },
      ]);
    } else if (this.isChapterEffectivelyEmpty(record)) {
      const altSources = altOnlineSources(this.bookId);
      if (altSources.length > 0) {
        const chTitle = entry?.title ?? entry?.displayTitle ?? record.title;
        const bTitle = this.currentBookTitle();
        this.reader.showBanner(
          '当前书源暂无本章内容',
          altSources.map((s) => ({
            label: `从${s.name}获取`,
            onClick: () => void this.tryAltSource(bTitle, chTitle, s.id, chapterId),
          })),
        );
      }
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
    if (!chapterId || this.reader.currentBookId !== this.bookId) return;
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

  /* ----------------------------- 换源 ----------------------------- */

  private isChapterEffectivelyEmpty(record: ChapterRecord): boolean {
    const paras = record.paragraphs;
    if (paras.length === 1 && paras[0] === EMPTY_CHAPTER_NOTE) return true;
    if (paras.length === 0) return true;
    const cleaned = cleanParagraphs(paras);
    return cleaned.length === 1 && cleaned[0] === EMPTY_CHAPTER_NOTE;
  }

  private async tryAltSource(
    bookTitle: string,
    chapterTitle: string,
    targetSource: SourceId,
    originalChapterId: string,
  ): Promise<void> {
    if (this.currentChapterId !== originalChapterId) return;
    this.reader.showBanner('正在从其他书源获取…');

    try {
      const result = await fetchAltChapter(bookTitle, chapterTitle, targetSource);
      if (this.currentChapterId !== originalChapterId) return;

      if (!result) {
        this.reader.showBanner('在其他书源中未找到匹配章节', [
          {
            label: '搜索此书',
            onClick: () => {
              location.hash = `#/search?q=${encodeURIComponent(bookTitle)}&source=${targetSource}`;
            },
          },
        ]);
        return;
      }

      const entry = this.currentEntry;
      const index = this.toc.indexOfChapter(originalChapterId);
      const prev = index > 0 ? this.toc.entryAt(index - 1) : null;
      const next = index >= 0 ? this.toc.entryAt(index + 1) : null;
      const prevId = prev?.id ?? null;
      const nextId = next?.id ?? null;

      const altRecord: ChapterRecord = {
        key: `${this.bookId}:${originalChapterId}`,
        bookId: this.bookId,
        chapterId: originalChapterId,
        title: result.title,
        paragraphs: result.paragraphs,
        charCount: result.charCount,
        source: 'remote',
        fetchedAt: Date.now(),
        bytes: 0,
        prevId,
        nextId,
        missingPages: [],
        complete: true,
        v: CHAPTER_CACHE_VERSION,
      };

      this.renderedChapterId = originalChapterId;
      this.reader.renderChapter({ record: altRecord, entry, prevId, nextId }, { restore: false });
      this.reader.showBanner(`内容来自${result.sourceName}（不影响原书数据）`);
      this.search.setContent(this.reader.contentElement);
      this.updateTopbar();
      this.updateBottombar(prevId, nextId);
    } catch (err) {
      if (this.currentChapterId !== originalChapterId) return;
      const msg = err instanceof Error ? err.message : '网络错误';
      this.reader.showBanner(`换源获取失败：${msg}`, [
        {
          label: '重试',
          onClick: () =>
            void this.tryAltSource(bookTitle, chapterTitle, targetSource, originalChapterId),
        },
      ]);
    }
  }

  /* ----------------------------- 预取 ----------------------------- */

  private schedulePrefetch(nextId: string | null): void {
    this.cancelPrefetch();
    if (!nextId || !isRemoteBookId(this.bookId)) return;
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
        try {
          this.library.importData((JSON.parse(json) as { library?: unknown }).library);
        } catch {
          /* 旧备份没有书架信息 */
        }
        this.renderHomeView();
      },
      onExportPersonal: () => this.personal.exportData(this.settings.get(), { library: this.library.exportData() }),
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
      this.settings.get().hideStatusBar &&
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
    const input = el('input', { type: 'file', accept: TXT_ACCEPT, style: { display: 'none' } }) as HTMLInputElement;
    input.addEventListener('change', () => {
      const file = input.files?.[0];
      input.remove();
      if (file) void this.handleTxtImport(file);
    });
    document.body.appendChild(input);
    input.click();
  }

  private async handleTxtImport(file: File): Promise<void> {
    showToast(`正在导入 ${file.name}…`);
    let result;
    try {
      result = await importTxtFile(file, (done, total) => {
        if (done < total && done % 1000 < 200) showToast(`正在保存 ${done}/${total} 章…`);
      });
    } catch (err) {
      showToast(`导入失败：${err instanceof Error ? err.message : String(err)}`);
      return;
    }
    this.localBooks = listLocalBooks();
    await this.switchBook(result.bookId);
    // 切换完成后再提示，避免用户在切换过程中点到旧书
    showToast(`已导入《${result.title}》共 ${result.chapters} 章`);
  }

  /**
   * 系统返回键（Android 壳经 window.__onNativeBack 调用）：
   * 浮层 → 章内搜索 → 紧凑目录 → 阅读/搜索页回书架；已在书架返回 false 交给系统（退出）。
   * 不按网页历史逐章回退。
   */
  handleBack(): boolean {
    if (closeActiveDialog()) return true;
    if (closeActiveSheet()) return true;
    if (this.search.isOpen()) {
      this.search.close();
      return true;
    }
    if (this.tocView.isOpen() && (this.layout.current === 'compact' || this.view !== 'reader')) {
      this.tocView.close();
      return true;
    }
    if (this.view !== 'home') {
      this.goHome();
      return true;
    }
    return false;
  }

  private async handleTxtExport(_from: number, _to: number): Promise<void> {
    const { text, count } = await exportCachedTxt(this.bookId, this.toc.all, this.settings.get().cleanSiteText);
    if (count === 0) {
      showToast('还没有可导出的缓存章节');
      return;
    }
    try {
      const outcome = await saveTextFile(`${this.currentBookTitle()}-已缓存-${count}章.txt`, text, 'text/plain');
      showToast(outcome === 'cancelled' ? '已取消导出' : `已导出 ${count} 章`);
    } catch (err) {
      showToast(`导出失败：${err instanceof Error ? err.message : String(err)}`);
    }
  }

  /* ---------------------------- 书籍切换 ---------------------------- */

  /** 打开某本书：切换后继续上次进度，无进度则读第一章 */
  private async openBook(bookId: string): Promise<void> {
    if (!(await this.ensureBook(bookId))) return;
    const progress = this.personal.getProgress(bookId);
    if (progress) {
      await this.openChapter(progress.chapterId, { restorePosition: true });
      return;
    }
    await this.openFirstAvailable(() => 0, '正在打开书籍…');
  }

  /** 从搜索结果/详情开始读：加入书架后打开 */
  private async readFromSource(book: BookSummary | BookInfo): Promise<void> {
    this.library.upsert(book);
    await this.openBook(book.id);
  }

  /** 切换到某本书并回书架 */
  private async switchBook(bookId: string): Promise<void> {
    const hashBefore = location.hash;
    if (bookId !== this.bookId) await this.activateBook(bookId);
    // 切换（读目录/缓存索引）期间用户已经导航到别处：不要再把他拉回书架
    if (location.hash !== hashBefore) return;
    if (location.hash === '' || location.hash === '#') this.showHome();
    else location.hash = '';
  }

  /** 切换当前书（不改变视图与地址栏） */
  private async activateBook(bookId: string): Promise<void> {
    closeActiveSheet();
    this.reader.flushPosition();
    this.chapterController?.abort();
    this.chapterGeneration++;
    this.cancelPrefetch();
    this.bookId = bookId;
    this.library.setActive(bookId);
    this.currentChapterId = null;
    this.renderedChapterId = null;
    this.loadingChapterId = null;
    this.currentEntry = null;
    this.tocView.close();
    this.toc = this.createTocStore(bookId);
    this.tocView = this.createTocView();
    const oldPanel = document.getElementById('toc-panel');
    oldPanel?.replaceWith(this.tocView.panel);
    this.download = this.createDownloadManager();
    this.download.subscribeDownload(() => void this.refreshCachedIds());
    await this.toc.init();
    await this.refreshCachedIds(true);
    this.updateTopbar();
    if (this.view === 'home') this.renderHomeView();
  }

  /** 移出书架：删除正文与目录缓存；阅读进度和书签保留（重新加入后可恢复） */
  private async removeBook(bookId: string): Promise<void> {
    const local = !isRemoteBookId(bookId);
    const title = local
      ? this.localBooks.find((b) => b.id === bookId)?.title
      : this.library.get(bookId)?.title;
    const ok = await confirmDialog(
      isDownloadBookId(bookId)
        ? `移除《${title ?? '本地书'}》？下载的正文将一并删除（可在“搜索书源”中重新下载）；阅读进度和书签保留。`
        : local
        ? `移除《${title ?? '本地书'}》？导入的正文将一并删除（无法从书源恢复）。`
        : `把《${title ?? bookId}》移出书架？已缓存的正文会删除；阅读进度和书签保留，重新加入后可恢复。`,
    );
    if (!ok) return;
    try {
      await clearBookContent(bookId);
      await deleteToc(bookId);
    } catch {
      /* 存储不可用：仍从书架移除 */
    }
    if (local) {
      this.localBooks = this.localBooks.filter((b) => b.id !== bookId);
      try {
        localStorage.setItem(LOCAL_BOOKS_KEY, JSON.stringify(this.localBooks));
      } catch {
        /* 忽略 */
      }
    } else {
      this.library.remove(bookId);
    }
    if (this.bookId === bookId) {
      const next = this.shelfBooks()[0];
      if (next) await this.activateBook(next.id);
      else this.renderHomeView();
    }
    if (this.view === 'home') this.renderHomeView();
    showToast('已移出书架');
  }
}
