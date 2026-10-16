/**
 * 整本下载型书源（爱下电子书）：下载站方提供的整本 TXT 压缩包 → 本机解压 → 按书页目录
 * 切章 → 作为离线书保存（书号保持 ixdzs-{n}，重新下载即更新，进度/书签不丢）。
 *
 * 网页版直接从站方下载域名获取（该域名允许跨域）；Android 壳经受限的 /native/download
 * 代理（只允许由书号拼出的压缩包地址）。只在用户点击“下载整本”时发生，不自动、不预取。
 */
import { isIxdzsBookId, ixdzsDownloadUrl, IXDZS_MAX_ZIP_BYTES, splitIxdzsTxt } from '../../shared/source-ixdzs.ts';
import { decodeTextBytes, extractFirstTxt } from '../../shared/zip.ts';
import type { BookInfo, BookSummary, TocEntry } from '../../shared/source.ts';
import { saveLocalBook, type LocalBookMeta } from './txt.ts';

export type DownloadPhase = 'download' | 'unzip' | 'split' | 'save';

export interface DownloadProgress {
  phase: DownloadPhase;
  done: number;
  /** download 阶段为字节数（未知时 0），save 阶段为章节数 */
  total: number;
}

export interface DownloadResult {
  meta: LocalBookMeta;
  /** 目录里有但整本 TXT 缺失的章节数 */
  missing: number;
  /** 是否按书页目录对齐（章节号与站点一致） */
  aligned: boolean;
}

export class BookDownloadError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'BookDownloadError';
  }
}

function zipUrl(bookId: string): string {
  if (import.meta.env.VITE_TARGET === 'android') return `/native/download?book=${encodeURIComponent(bookId)}`;
  return ixdzsDownloadUrl(bookId);
}

async function fetchZip(bookId: string, signal: AbortSignal | undefined, onProgress: (p: DownloadProgress) => void): Promise<Uint8Array> {
  const res = await fetch(zipUrl(bookId), { signal, credentials: 'omit', referrerPolicy: 'no-referrer' });
  if (!res.ok) throw new BookDownloadError(`书源下载失败（HTTP ${res.status}）`);
  const total = Number(res.headers.get('content-length') ?? 0) || 0;
  if (total > IXDZS_MAX_ZIP_BYTES) throw new BookDownloadError('压缩包过大，已取消');
  if (!res.body) return new Uint8Array(await res.arrayBuffer());
  const reader = res.body.getReader();
  const chunks: Uint8Array[] = [];
  let received = 0;
  let lastReport = 0;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    received += value.length;
    if (received > IXDZS_MAX_ZIP_BYTES) {
      await reader.cancel();
      throw new BookDownloadError('压缩包过大，已取消');
    }
    chunks.push(value);
    // 进度回调节流：避免每个分块都重绘
    if (received - lastReport > 256 * 1024) {
      lastReport = received;
      onProgress({ phase: 'download', done: received, total });
    }
  }
  onProgress({ phase: 'download', done: received, total: total || received });
  const out = new Uint8Array(received);
  let pos = 0;
  for (const c of chunks) {
    out.set(c, pos);
    pos += c.length;
  }
  return out;
}

/**
 * 下载整本并保存为离线书。catalog 为书页目录（/api/toc 第 1 页），用于对齐章节号与标题；
 * 缺失时按 TXT 的顶格标题切分。
 */
export async function downloadSourceBook(
  book: BookSummary | BookInfo,
  catalog: TocEntry[],
  opts: { signal?: AbortSignal; onProgress?: (p: DownloadProgress) => void } = {},
): Promise<DownloadResult> {
  if (!isIxdzsBookId(book.id)) throw new BookDownloadError('该书源不支持整本下载');
  const report = opts.onProgress ?? (() => undefined);
  const zip = await fetchZip(book.id, opts.signal, report);
  if (opts.signal?.aborted) throw new DOMException('aborted', 'AbortError');

  report({ phase: 'unzip', done: 0, total: 0 });
  let text: string;
  try {
    text = decodeTextBytes((await extractFirstTxt(zip)).data);
  } catch (err) {
    throw new BookDownloadError(`解压失败：${err instanceof Error ? err.message : String(err)}`);
  }
  if (opts.signal?.aborted) throw new DOMException('aborted', 'AbortError');

  report({ phase: 'split', done: 0, total: 0 });
  await new Promise((r) => setTimeout(r, 0));
  const split = splitIxdzsTxt(text, catalog);
  if (split.chapters.length === 0) throw new BookDownloadError('没有从 TXT 中识别到章节');
  if (opts.signal?.aborted) throw new DOMException('aborted', 'AbortError');

  const meta = await saveLocalBook(
    {
      bookId: book.id,
      title: book.title,
      author: book.author,
      source: 'ixdzs',
      chapters: split.chapters,
    },
    (done, total) => report({ phase: 'save', done, total }),
  );
  return { meta, missing: split.missing.length, aligned: split.aligned };
}
