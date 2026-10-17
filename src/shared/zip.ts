/**
 * 最小 ZIP 读取 + 文本解码（同构：浏览器 / Android WebView / Node 测试）。
 * 只用于书源提供的“整本 TXT 下载”压缩包：读中央目录，取第一个 .txt 条目，
 * 支持 stored(0) 与 deflate(8)（DecompressionStream('deflate-raw')）。不支持 ZIP64 与加密。
 */

/** 解压后文本上限（防压缩炸弹）；最长的整本书 TXT 也远小于此 */
export const MAX_UNZIPPED_BYTES = 160 * 1024 * 1024;

export class ZipError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'ZipError';
  }
}

export interface ZipEntry {
  name: string;
  method: number;
  compressedSize: number;
  size: number;
  localOffset: number;
}

const EOCD_SIG = 0x06054b50;
const CEN_SIG = 0x02014b50;
const LOC_SIG = 0x04034b50;

/** 读中央目录（文件名按 UTF-8 标志位解码，否则按 GB18030——国内压缩包常见） */
export function listZipEntries(buf: Uint8Array): ZipEntry[] {
  const view = new DataView(buf.buffer, buf.byteOffset, buf.byteLength);
  let eocd = -1;
  for (let i = buf.length - 22; i >= Math.max(0, buf.length - 22 - 0xffff); i--) {
    if (view.getUint32(i, true) === EOCD_SIG) {
      eocd = i;
      break;
    }
  }
  if (eocd < 0) throw new ZipError('不是有效的 ZIP 文件');
  const count = view.getUint16(eocd + 10, true);
  let offset = view.getUint32(eocd + 16, true);
  if (offset === 0xffffffff || count === 0xffff) throw new ZipError('不支持 ZIP64');
  const entries: ZipEntry[] = [];
  for (let n = 0; n < count; n++) {
    if (offset + 46 > buf.length || view.getUint32(offset, true) !== CEN_SIG) throw new ZipError('ZIP 目录损坏');
    const flags = view.getUint16(offset + 8, true);
    const method = view.getUint16(offset + 10, true);
    const compressedSize = view.getUint32(offset + 20, true);
    const size = view.getUint32(offset + 24, true);
    const nameLen = view.getUint16(offset + 28, true);
    const extraLen = view.getUint16(offset + 30, true);
    const commentLen = view.getUint16(offset + 32, true);
    const localOffset = view.getUint32(offset + 42, true);
    const nameBytes = buf.subarray(offset + 46, offset + 46 + nameLen);
    const name = new TextDecoder(flags & 0x800 ? 'utf-8' : 'gb18030').decode(nameBytes);
    if (flags & 0x1) throw new ZipError('不支持加密的 ZIP');
    entries.push({ name, method, compressedSize, size, localOffset });
    offset += 46 + nameLen + extraLen + commentLen;
  }
  return entries;
}

async function inflateRaw(data: Uint8Array, expected: number): Promise<Uint8Array> {
  // 数据来自 fetch 的 ArrayBuffer（非 SharedArrayBuffer），类型断言安全
  const stream = new Blob([data as Uint8Array<ArrayBuffer>]).stream().pipeThrough(new DecompressionStream('deflate-raw'));
  const reader = stream.getReader();
  const out = new Uint8Array(expected);
  let pos = 0;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    if (pos + value.length > out.length) {
      await reader.cancel();
      throw new ZipError('解压结果与 ZIP 记录的大小不符');
    }
    out.set(value, pos);
    pos += value.length;
  }
  if (pos !== expected) throw new ZipError('解压结果与 ZIP 记录的大小不符');
  return out;
}

/** 取出某个条目的原始字节 */
export async function readZipEntry(buf: Uint8Array, entry: ZipEntry): Promise<Uint8Array> {
  if (entry.size > MAX_UNZIPPED_BYTES) throw new ZipError('压缩包内文件过大');
  const view = new DataView(buf.buffer, buf.byteOffset, buf.byteLength);
  const at = entry.localOffset;
  if (at + 30 > buf.length || view.getUint32(at, true) !== LOC_SIG) throw new ZipError('ZIP 条目损坏');
  const start = at + 30 + view.getUint16(at + 26, true) + view.getUint16(at + 28, true);
  const end = start + entry.compressedSize;
  if (end > buf.length) throw new ZipError('ZIP 文件不完整');
  const data = buf.subarray(start, end);
  if (entry.method === 0) {
    if (entry.compressedSize !== entry.size) throw new ZipError('ZIP 条目损坏');
    return data;
  }
  if (entry.method === 8) return inflateRaw(data, entry.size);
  throw new ZipError(`不支持的压缩方式 ${entry.method}`);
}

/** 压缩包里的第一个 .txt（整本书只有一个文件） */
export async function extractFirstTxt(buf: Uint8Array): Promise<{ name: string; data: Uint8Array }> {
  const entry = listZipEntries(buf).find((e) => /\.txt$/i.test(e.name) && e.size > 0);
  if (!entry) throw new ZipError('压缩包里没有 TXT 文件');
  return { name: entry.name, data: await readZipEntry(buf, entry) };
}

/**
 * TXT 字节 → 文本：BOM 优先（UTF-8 / UTF-16），其次严格 UTF-8，失败则按 GB18030
 * （GBK 的超集；国内 TXT 下载普遍是 GBK/GB18030）。
 */
export function decodeTextBytes(bytes: Uint8Array): string {
  if (bytes[0] === 0xef && bytes[1] === 0xbb && bytes[2] === 0xbf) return new TextDecoder('utf-8').decode(bytes.subarray(3));
  if (bytes[0] === 0xff && bytes[1] === 0xfe) return new TextDecoder('utf-16le').decode(bytes.subarray(2));
  if (bytes[0] === 0xfe && bytes[1] === 0xff) return new TextDecoder('utf-16be').decode(bytes.subarray(2));
  try {
    return new TextDecoder('utf-8', { fatal: true }).decode(bytes);
  } catch {
    return new TextDecoder('gb18030').decode(bytes);
  }
}
