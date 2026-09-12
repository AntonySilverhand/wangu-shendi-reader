import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest';
import { PersonalStore, PERSONAL_KEY } from '../src/web/store/personal.ts';

let data: Map<string, string>;
beforeEach(() => {
  data = new Map();
  vi.stubGlobal('localStorage', {
    getItem: (k: string) => data.get(k) ?? null,
    setItem: (k: string, v: string) => { data.set(k, v); },
  });
});
afterEach(() => vi.unstubAllGlobals());
const bookmark = { chapterId: '123', chapterTitle: '测试', chapterIndex: 1, paragraph: 12, offset: 34, excerpt: '书签' };

describe('个人数据同步持久化', () => {
  it('进度无需 timer/flush 即可由新实例恢复', () => {
    const s = new PersonalStore();
    s.setProgress('36780', { chapterId: '123', chapterIndex: 1, paragraph: 12, offset: 34, updatedAt: 100 });
    expect(new PersonalStore().getProgress('36780')?.paragraph).toBe(12);
  });
  it('书签添加、删除、清空即时落盘', () => {
    const s = new PersonalStore();
    const first = s.addBookmark('36780', bookmark);
    expect(new PersonalStore().getBookmarks('36780')).toHaveLength(1);
    s.removeBookmark('36780', first.id);
    expect(new PersonalStore().getBookmarks('36780')).toHaveLength(0);
    s.addBookmark('36780', bookmark);
    s.clearBookmarks('36780');
    expect(new PersonalStore().getBookmarks('36780')).toHaveLength(0);
  });
  it('保存最近30章，重复打开去重并置顶，跨实例顺序不变', () => {
    const s = new PersonalStore();
    for (let n = 0; n < 40; n++) s.pushHistory('36780', { chapterId: String(n), chapterTitle: '测试', chapterIndex: n });
    s.pushHistory('36780', { chapterId: '20', chapterTitle: '测试', chapterIndex: 20 });
    const h = new PersonalStore().getHistory('36780');
    expect(h).toHaveLength(30);
    expect(h[0]?.chapterId).toBe('20');
    expect(new Set(h.map((v) => v.chapterId)).size).toBe(30);
  });
  it('写入失败可诊断，恢复写入后健康标志恢复', () => {
    const log = vi.spyOn(console, 'error').mockImplementation(() => {});
    const s = new PersonalStore();
    const write = vi.spyOn(localStorage, 'setItem').mockImplementationOnce(() => { throw new Error('quota'); });
    s.addBookmark('36780', bookmark);
    expect(s.storageHealthy).toBe(false);
    expect(s.storageError).toBe('quota');
    expect(log).toHaveBeenCalled();
    expect(data.has(PERSONAL_KEY)).toBe(false);
    s.flush();
    expect(s.storageHealthy).toBe(true);
    write.mockRestore(); log.mockRestore();
  });
});
