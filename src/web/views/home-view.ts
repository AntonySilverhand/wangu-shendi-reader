import { el } from '../dom.ts';
import { formatTime } from '../format.ts';
import { renderCover } from '../cover.ts';
import { sourceNameOf, sourceOfBook } from '../../shared/sources.ts';
import type { PersonalStore } from '../store/personal.ts';
import type { TocStore } from '../store/toc.ts';

/** 书架上的一本书（远程书或本地导入书） */
export interface ShelfBook {
  id: string;
  title: string;
  author: string;
  local: boolean;
  /** 排序用：最近阅读时间，无则加入时间 */
  sortKey: number;
  /** 本地书的章节数等补充信息 */
  note?: string;
}

export interface HomeDeps {
  bookId: string | null;
  bookTitle: string;
  bookAuthor: string;
  personal: PersonalStore;
  toc: TocStore;
  shelf: ShelfBook[];
  onContinue: () => void;
  onOpenChapter: (chapterId: string) => void;
  onOpenToc: () => void;
  onStartBeginning: () => void;
  onOpenBookmarks: () => void;
  onOpenData: () => void;
  onOpenSettings: () => void;
  onOpenDiscover: () => void;
  onSwitchBook: (bookId: string) => void;
  onRemoveBook: (bookId: string) => void;
  onImportTxt: () => void;
}

export function renderHome(container: HTMLElement, deps: HomeDeps): void {
  while (container.firstChild) container.removeChild(container.firstChild);

  const head = el(
    'div',
    { class: 'home-head' },
    el('div', null, el('h1', { text: '我的书架' }), el('p', { text: '干净、专注、为长时间阅读而做' })),
    el(
      'button',
      { class: 'btn primary', type: 'button', id: 'open-discover', onclick: deps.onOpenDiscover },
      '搜索书源',
    ),
  );

  if (deps.bookId === null) {
    // 书架为空：只给出搜索与导入入口
    container.append(
      head,
      el(
        'section',
        { class: 'book-card empty-shelf' },
        el(
          'div',
          { class: 'book-info' },
          el('h2', { text: '书架还是空的' }),
          el('p', { class: 'book-progress', text: '在书源中搜索想读的书，或导入本地 TXT。' }),
          el(
            'div',
            { class: 'home-actions' },
            el('button', { class: 'btn primary', type: 'button', onclick: deps.onOpenDiscover }, '搜索书源'),
            el('button', { class: 'btn', type: 'button', onclick: deps.onImportTxt }, '导入 TXT'),
          ),
        ),
      ),
      quickGrid(deps, 0),
    );
    return;
  }

  const bookId = deps.bookId;
  const progress = deps.personal.getProgress(bookId);
  const mainCount = deps.toc.mainCount;
  const index = progress ? deps.toc.indexOfChapter(progress.chapterId) : -1;
  const chapterLabel = progress
    ? deps.toc.entryAt(index)?.displayTitle ?? deps.personal.getHistory(bookId)[0]?.chapterTitle ?? `章节 ${progress.chapterId}`
    : '';
  const percent = index >= 0 && mainCount > 0 ? Math.round(((index + 1) / mainCount) * 100) : 0;
  const history = deps.personal.getHistory(bookId).slice(0, 5);
  const stats = deps.personal.stats(bookId);

  const card = el(
    'div',
    { class: 'book-card', dataset: { bookId } },
    renderCover(bookId, deps.bookTitle),
    el(
      'div',
      { class: 'book-info' },
      el('h2', { text: deps.bookTitle }),
      el('p', { class: 'author', text: deps.bookAuthor }),
      progress
        ? el('p', { class: 'book-progress' }, `上次读到：${chapterLabel}`, el('br'), `全书进度 ${percent}%`)
        : el('p', { class: 'book-progress', text: '还没有阅读记录，开始第一章吧。' }),
      el('div', { class: 'progress-track' }, el('div', { class: 'progress-fill', style: { width: `${percent}%` } })),
      el(
        'div',
        { class: 'home-actions' },
        el(
          'button',
          { class: 'btn primary', type: 'button', id: 'continue-reading', onclick: deps.onContinue },
          progress ? '继续阅读' : '开始阅读',
        ),
        el('button', { class: 'btn', type: 'button', onclick: deps.onOpenToc }, '章节目录'),
        el(
          'button',
          { class: 'btn ghost', type: 'button', onclick: deps.onStartBeginning },
          '从第一章开始',
        ),
      ),
    ),
  );

  const recent = el('section', { class: 'home-recent' });
  recent.appendChild(el('h2', { class: 'section-title', text: '最近阅读' }));
  if (history.length === 0) {
    recent.appendChild(el('p', { class: 'book-progress', text: '暂无记录' }));
  } else {
    const list = el('div', { class: 'kv-list' });
    for (const item of history) {
      list.appendChild(
        el(
          'button',
          { class: 'quick-card', type: 'button', onclick: () => deps.onOpenChapter(item.chapterId) },
          el('strong', { text: item.chapterTitle || `章节 ${item.chapterId}` }),
          el('span', { text: formatTime(item.at) }),
        ),
      );
    }
    recent.appendChild(list);
  }

  container.append(head, card, quickGrid(deps, stats.bookmarks), recent, shelfSection(deps));
}

function quickGrid(deps: HomeDeps, bookmarks: number): HTMLElement {
  return el(
    'div',
    { class: 'quick-grid' },
    quickCard('我的书签', `${bookmarks} 条`, deps.onOpenBookmarks),
    quickCard('下载与缓存', '离线阅读、TXT 导入导出', deps.onOpenData),
    quickCard('主题与排版', '四套主题、字号行距', deps.onOpenSettings),
    quickCard('导入 TXT', '把本地文本加入书架', deps.onImportTxt),
  );
}

function shelfSection(deps: HomeDeps): HTMLElement {
  const section = el('section', { class: 'home-shelf', 'aria-label': '书架' });
  section.appendChild(el('h2', { class: 'section-title', text: `书架 · ${deps.shelf.length} 本` }));
  const grid = el('div', { class: 'shelf-grid' });
  for (const book of deps.shelf) {
    const progress = deps.personal.getProgress(book.id);
    const last = deps.personal.getHistory(book.id)[0];
    const current = book.id === deps.bookId;
    const status = progress
      ? `读到：${last?.chapterTitle || `章节 ${progress.chapterId}`}`
      : book.note ?? '未开始';
    const item = el(
      'div',
      { class: `shelf-card${current ? ' current' : ''}`, dataset: { bookId: book.id } },
      el(
        'button',
        {
          class: 'shelf-open',
          type: 'button',
          'aria-current': current ? 'true' : undefined,
          onclick: () => deps.onSwitchBook(book.id),
        },
        renderCover(book.id, book.title, { small: true }),
        el(
          'span',
          { class: 'shelf-info' },
          el('strong', { text: book.title }),
          el('span', { text: book.local ? book.author : shelfByline(book) }),
          el('span', { class: 'shelf-status', text: status }),
        ),
      ),
      el(
        'button',
        {
          class: 'btn small ghost danger shelf-remove',
          type: 'button',
          'aria-label': `移出书架：${book.title}`,
          onclick: () => deps.onRemoveBook(book.id),
        },
        '移除',
      ),
    );
    grid.appendChild(item);
  }
  section.appendChild(grid);
  return section;
}

/** 作者；非默认书源的书附上书源名，便于区分同名书 */
function shelfByline(book: ShelfBook): string {
  const author = book.author || '佚名';
  return sourceOfBook(book.id) === '69shuba' ? `${author} · ${sourceNameOf(book.id)}` : author;
}

function quickCard(title: string, hint: string, onClick: () => void): HTMLElement {
  return el(
    'button',
    { class: 'quick-card', type: 'button', onclick: onClick },
    el('strong', { text: title }),
    el('span', { text: hint }),
  );
}
