import { clear, el, iconButton } from '../dom.ts';
import { openSheet, type SheetHandle } from '../ui/sheet.ts';
import { confirmDialog, showToast } from '../ui/toast.ts';
import type { Bookmark, PersonalStore } from '../store/personal.ts';
import { formatTime } from '../format.ts';

export interface BookmarksDeps {
  personal: PersonalStore;
  bookId: string;
  onJump: (bookmark: Bookmark) => void;
}

export function openBookmarksSheet(deps: BookmarksDeps): SheetHandle {
  const body = el('div', { class: 'sheet-body tight' });

  const render = () => {
    clear(body);
    const items = deps.personal.getBookmarks(deps.bookId);
    if (items.length === 0) {
      body.appendChild(
        el('div', {
          class: 'empty-hint',
          text: '还没有书签。\n阅读时点顶部的书签按钮即可记住当前位置。',
        }),
      );
      return;
    }
    for (const bm of items) {
      const item = el(
        'div',
        { class: 'bookmark-item' },
        el(
          'div',
          { class: 'bm-head' },
          el('strong', { text: bm.chapterTitle || `章节 ${bm.chapterId}` }),
          el('time', { text: formatTime(bm.createdAt) }),
        ),
        bm.excerpt ? el('div', { class: 'excerpt', text: bm.excerpt }) : null,
        el(
          'div',
          { class: 'row-actions' },
          el(
            'button',
            {
              class: 'btn small',
              type: 'button',
              onclick: () => {
                handle.close();
                deps.onJump(bm);
              },
            },
            '跳转',
          ),
          el(
            'button',
            {
              class: 'btn small ghost danger',
              type: 'button',
              onclick: () => {
                deps.personal.removeBookmark(deps.bookId, bm.id);
                render();
                showToast(deps.personal.storageHealthy ? '已删除书签' : '删除未保存：存储写入失败');
              },
            },
            '删除',
          ),
        ),
      );
      body.appendChild(item);
    }
  };

  const clearBtn = iconButton({
    label: '清空书签',
    icon: 'trash',
    onClick: async () => {
      if (deps.personal.getBookmarks(deps.bookId).length === 0) return;
      const ok = await confirmDialog('清空全部书签？');
      if (!ok) return;
      deps.personal.clearBookmarks(deps.bookId);
      render();
      if (!deps.personal.storageHealthy) showToast('清空未保存：存储写入失败');
    },
  });

  render();
  const handle = openSheet({
    title: '我的书签',
    body,
    headActions: [clearBtn],
    testId: 'bookmarks-sheet',
  });
  return handle;
}
