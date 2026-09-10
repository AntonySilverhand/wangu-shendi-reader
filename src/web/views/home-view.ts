import { el } from '../dom.ts';
import { formatTime } from '../format.ts';
import type { PersonalStore } from '../store/personal.ts';
import type { TocStore } from '../store/toc.ts';
import type { LocalBookMeta } from '../store/txt.ts';

export interface HomeDeps {
  bookId: string;
  bookTitle: string;
  bookAuthor: string;
  personal: PersonalStore;
  toc: TocStore;
  localBooks: LocalBookMeta[];
  onContinue: () => void;
  onOpenChapter: (chapterId: string) => void;
  onOpenToc: () => void;
  onStartBeginning: () => void;
  onOpenBookmarks: () => void;
  onOpenData: () => void;
  onOpenSettings: () => void;
  onSwitchBook: (bookId: string) => void;
  onRemoveLocalBook: (bookId: string) => void;
  onImportTxt: () => void;
}

export function renderHome(container: HTMLElement, deps: HomeDeps): void {
  while (container.firstChild) container.removeChild(container.firstChild);
  const progress = deps.personal.getProgress(deps.bookId);
  const mainCount = deps.toc.mainCount;
  const index = progress ? deps.toc.indexOfChapter(progress.chapterId) : -1;
  const chapterLabel = progress
    ? deps.toc.entryAt(index)?.displayTitle ?? `章节 ${progress.chapterId}`
    : '';
  const percent = index >= 0 && mainCount > 0 ? Math.round(((index + 1) / mainCount) * 100) : 0;
  const history = deps.personal.getHistory(deps.bookId).slice(0, 5);
  const stats = deps.personal.stats(deps.bookId);

  const card = el(
    'div',
    { class: 'book-card' },
    el('div', { class: 'book-cover', 'aria-hidden': 'true' }, '万古神帝'),
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

  const quick = el(
    'div',
    { class: 'quick-grid' },
    quickCard('我的书签', `${stats.bookmarks} 条`, deps.onOpenBookmarks),
    quickCard('下载与缓存', '离线阅读、TXT 导入导出', deps.onOpenData),
    quickCard('主题与排版', '四套主题、字号行距', deps.onOpenSettings),
    quickCard('导入 TXT', '书源不可用时的备份路径', deps.onImportTxt),
  );

  const recent = el('section');
  recent.appendChild(el('h2', { class: 'section-title', text: '最近阅读' }));
  if (history.length === 0) {
    recent.appendChild(el('p', { class: 'book-progress', text: '暂无记录' }));
  } else {
    const list = el('div', { class: 'kv-list' });
    for (const item of history) {
      const row = el(
        'button',
        {
          class: 'quick-card',
          type: 'button',
          onclick: () => deps.onOpenChapter(item.chapterId),
        },
        el('strong', { text: item.chapterTitle || `章节 ${item.chapterId}` }),
        el('span', { text: formatTime(item.at) }),
      );
      list.appendChild(row);
    }
    recent.appendChild(list);
  }

  const localSection = el('section');
  if (deps.localBooks.length > 0) {
    localSection.appendChild(el('h2', { class: 'section-title', text: '本地导入的书' }));
    const grid = el('div', { class: 'quick-grid' });
    for (const book of deps.localBooks) {
      const card2 = el(
        'div',
        { class: 'quick-card', style: { display: 'grid', gap: '8px' } },
        el('strong', { text: book.title }),
        el('span', { text: `${book.chapters} 章 · ${formatTime(book.importedAt)}` }),
        el(
          'div',
          { class: 'row-actions' },
          el('button', { class: 'btn small', type: 'button', onclick: () => deps.onSwitchBook(book.id) }, '打开'),
          el(
            'button',
            {
              class: 'btn small ghost danger',
              type: 'button',
              onclick: () => deps.onRemoveLocalBook(book.id),
            },
            '移除',
          ),
        ),
      );
      grid.appendChild(card2);
    }
    localSection.appendChild(grid);
  }

  container.appendChild(
    el(
      'div',
      { class: 'home-root', id: 'home-root' },
      el(
        'div',
        { class: 'home-head' },
        el('h1', { text: '我的书架' }),
        el('p', { text: '干净、专注、为长时间阅读而做' }),
      ),
      card,
      quick,
      recent,
      localSection,
    ),
  );
}

function quickCard(title: string, hint: string, onClick: () => void): HTMLElement {
  return el(
    'button',
    { class: 'quick-card', type: 'button', onclick: onClick },
    el('strong', { text: title }),
    el('span', { text: hint }),
  );
}
