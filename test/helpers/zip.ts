import { deflateRawSync } from 'node:zlib';

/** 测试用最小 ZIP 写入器（单文件；method 8 = deflate，0 = stored） */
export function makeZip(name: string, data: Uint8Array, method: 0 | 8 = 8, opts: { badSize?: boolean } = {}): Uint8Array {
  const nameBytes = new TextEncoder().encode(name);
  const body = method === 8 ? new Uint8Array(deflateRawSync(data)) : data;
  const size = opts.badSize ? data.length + 1 : data.length;
  const local = new Uint8Array(30 + nameBytes.length);
  const lv = new DataView(local.buffer);
  lv.setUint32(0, 0x04034b50, true);
  lv.setUint16(4, 20, true);
  lv.setUint16(6, 0x800, true);
  lv.setUint16(8, method, true);
  lv.setUint32(18, body.length, true);
  lv.setUint32(22, size, true);
  lv.setUint16(26, nameBytes.length, true);
  local.set(nameBytes, 30);
  const central = new Uint8Array(46 + nameBytes.length);
  const cv = new DataView(central.buffer);
  cv.setUint32(0, 0x02014b50, true);
  cv.setUint16(8, 0x800, true);
  cv.setUint16(10, method, true);
  cv.setUint32(20, body.length, true);
  cv.setUint32(24, size, true);
  cv.setUint16(28, nameBytes.length, true);
  cv.setUint32(42, 0, true);
  central.set(nameBytes, 46);
  const eocd = new Uint8Array(22);
  const ev = new DataView(eocd.buffer);
  ev.setUint32(0, 0x06054b50, true);
  ev.setUint16(8, 1, true);
  ev.setUint16(10, 1, true);
  ev.setUint32(12, central.length, true);
  ev.setUint32(16, local.length + body.length, true);
  const out = new Uint8Array(local.length + body.length + central.length + eocd.length);
  out.set(local, 0);
  out.set(body, local.length);
  out.set(central, local.length + body.length);
  out.set(eocd, local.length + body.length + central.length);
  return out;
}
