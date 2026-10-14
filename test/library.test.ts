import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest';
import { LibraryStore, LIBRARY_KEY, isRemoteBookId, isLocalBookId } from '../src/web/store/library.ts';

let data: Map<string, string>;
beforeEach(() => {
  data = new Map();
  vi.stubGlobal('localStorage', {
    getItem: (k: string) => data.get(k) ?? null,
    setItem: (k: string, v: string) => {
      data.set(k, v);
    },
  });
});
afterEach(() => vi.unstubAllGlobals());

describe('书架（LibraryStore）', () => {
  it('首次运行预置《万古神帝》，书号不变（旧进度/缓存零迁移）', () => {
    const lib = new LibraryStore();
    expect(lib.list().map((b) => b.id)).toEqual(['36780']);
    expect(lib.get('36780')?.title).toBe('万古神帝');
    expect(JSON.parse(data.get(LIBRARY_KEY)!).seeded).toBe(true);
  });

  it('移除预置书后不会被再次预置', () => {
    new LibraryStore().remove('36780');
    expect(new LibraryStore().list()).toEqual([]);
  });

  it('upsert：加入、更新、不被空值覆盖；非法书号拒绝', () => {
    const lib = new LibraryStore();
    lib.upsert({ id: '104429', title: '斗破苍穹', author: '九支书竹', category: '言情小说' });
    lib.upsert({ id: '104429', title: '斗破苍穹', author: '', intro: '简介' });
    const b = new LibraryStore().get('104429')!;
    expect(b).toMatchObject({ title: '斗破苍穹', author: '九支书竹', category: '言情小说', intro: '简介' });
    expect(lib.upsert({ id: 'local-x', title: 'x' })).toBeNull();
    expect(lib.upsert({ id: '../1', title: 'x' })).toBeNull();
  });

  it('refresh 只更新已在书架上的书（占位标题被详情页信息替换）', () => {
    const lib = new LibraryStore();
    lib.refresh({ id: '230580', title: '不在书架', author: 'A' });
    expect(lib.has('230580')).toBe(false);
    lib.upsert({ id: '230580', title: '书号 230580' });
    lib.refresh({ id: '230580', title: '万古神帝之万古青鹿传', author: 'Bean', firstChapterId: '72836267' });
    expect(lib.get('230580')).toMatchObject({ title: '万古神帝之万古青鹿传', author: 'Bean', firstChapterId: '72836267' });
  });

  it('当前书持久化；移除当前书时清空', () => {
    const lib = new LibraryStore();
    lib.upsert({ id: '104429', title: '斗破苍穹' });
    lib.setActive('104429');
    expect(new LibraryStore().activeBookId).toBe('104429');
    lib.remove('104429');
    expect(new LibraryStore().activeBookId).toBeNull();
  });

  it('导入备份：合并补入、忽略非法条目；损坏的存储不抛错', () => {
    const lib = new LibraryStore();
    const n = lib.importData({ books: [{ id: '104429', title: '斗破苍穹' }, { id: 'bad', title: 'x' }, { id: '36780', title: '重复' }] });
    expect(n).toBe(1);
    expect(lib.get('36780')?.title).toBe('万古神帝');
    data.set(LIBRARY_KEY, '{not json');
    expect(() => new LibraryStore()).not.toThrow();
  });

  it('书号判定：数字=远程书，local-xxx=本地书', () => {
    expect(isRemoteBookId('36780')).toBe(true);
    expect(isRemoteBookId('local-abc')).toBe(false);
    expect(isLocalBookId('local-lx3k9a')).toBe(true);
    expect(isLocalBookId('36780')).toBe(false);
  });
});
