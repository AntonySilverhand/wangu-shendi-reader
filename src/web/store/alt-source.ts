/**
 * 换源获取：当前书源的章节内容为空/占位时，尝试从另一个在线书源获取同一章节。
 *
 * 流程：用书名在目标书源搜索 → 匹配书 → 加载目录 → 按标题匹配章节 → 获取正文。
 * 结果只用于一次性展示，不写入 IndexedDB（不覆盖原书缓存与数据）。
 */

import { apiGet } from './remote.ts';
import type { ChapterResult, TocRangeResult } from '../../shared/source.ts';
import type { SearchResponse } from '../../shared/api.ts';
import { sourceOfBook, sourceMeta, SOURCES, type SourceId } from '../../shared/sources.ts';
import { cleanParagraphs, countChars, EMPTY_CHAPTER_NOTE } from '../../shared/text-clean.ts';

export interface AltChapterResult {
  paragraphs: string[];
  charCount: number;
  title: string;
  sourceName: string;
  altBookId: string;
  altBookTitle: string;
}

/** 当前书可以尝试的备选在线书源（只有 online 模式的书源能逐章在线获取） */
export function altOnlineSources(bookId: string): { id: SourceId; name: string }[] {
  const current = sourceOfBook(bookId);
  return SOURCES
    .filter((s) => s.mode === 'online' && s.id !== current)
    .map((s) => ({ id: s.id, name: s.name }));
}

function titleKey(text: string): string {
  return text.replace(/[\s　:：,，.。、_\-—()（）【】\[\]""'']/g, '');
}

/**
 * 尝试从另一个在线书源获取同一章节。
 * @returns 成功时返回清洗后的章节内容和来源信息；找不到或失败时返回 null
 */
/**
 * 依次尝试所有备选在线书源，返回第一个有内容的结果。
 */
export async function fetchAltChapterAuto(
  bookId: string,
  bookTitle: string,
  chapterTitle: string,
  signal?: AbortSignal,
): Promise<AltChapterResult | null> {
  const sources = altOnlineSources(bookId);
  for (const s of sources) {
    if (signal?.aborted) return null;
    try {
      const result = await fetchAltChapter(bookTitle, chapterTitle, s.id, signal);
      if (result) return result;
    } catch {
      /* 单个书源失败不中断，继续下一个 */
    }
  }
  return null;
}

export async function fetchAltChapter(
  bookTitle: string,
  chapterTitle: string,
  targetSource: SourceId,
  signal?: AbortSignal,
): Promise<AltChapterResult | null> {
  const search = await apiGet<SearchResponse>(
    `/api/search?q=${encodeURIComponent(bookTitle)}&source=${targetSource}`,
    { signal, retries: 0, timeoutMs: 15_000 },
  );
  if (!search.results.length) return null;

  const book =
    search.results.find((r) => titleKey(r.title) === titleKey(bookTitle)) ??
    search.results[0]!;

  const toc = await apiGet<TocRangeResult>(
    `/api/toc?book=${encodeURIComponent(book.id)}&from=1&to=6`,
    { signal, retries: 0, timeoutMs: 15_000 },
  );

  let entries = toc.pages.flatMap((p) => p.entries);
  const key = titleKey(chapterTitle);
  let match = entries.find(
    (e) => titleKey(e.title) === key || titleKey(e.displayTitle) === key,
  );

  if (!match && toc.totalPages > 6) {
    const chNum = chapterTitle.match(/第(\d+)[章节回话]/)?.[1];
    if (chNum) {
      const perPage = Math.max(1, entries.length / Math.min(6, toc.totalPages));
      const estPage = Math.max(7, Math.ceil(Number(chNum) / perPage));
      const from = Math.max(7, estPage - 2);
      const to = Math.min(toc.totalPages, from + 5);
      if (from <= toc.totalPages) {
        try {
          const extra = await apiGet<TocRangeResult>(
            `/api/toc?book=${encodeURIComponent(book.id)}&from=${from}&to=${to}`,
            { signal, retries: 0, timeoutMs: 15_000 },
          );
          entries = extra.pages.flatMap((p) => p.entries);
          match = entries.find(
            (e) => titleKey(e.title) === key || titleKey(e.displayTitle) === key,
          );
        } catch {
          /* 加载更多目录页失败：放弃匹配 */
        }
      }
    }
  }

  if (!match) return null;

  const chapter = await apiGet<ChapterResult>(
    `/api/chapter?book=${encodeURIComponent(book.id)}&id=${encodeURIComponent(match.id)}`,
    { signal, retries: 0, timeoutMs: 20_000 },
  );

  if (!chapter.paragraphs.length) return null;

  const cleaned = cleanParagraphs(chapter.paragraphs);
  if (cleaned.length === 1 && cleaned[0] === EMPTY_CHAPTER_NOTE) return null;

  return {
    paragraphs: cleaned,
    charCount: countChars(cleaned),
    title: chapter.title,
    sourceName: sourceMeta(targetSource).name,
    altBookId: book.id,
    altBookTitle: book.title,
  };
}
