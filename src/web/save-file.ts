/**
 * 把文本保存成文件（导出个人数据 / 导出 TXT）。
 *
 * 网页版：<a download href=blob:>（浏览器下载）。
 * Android 壳：WebView 不处理下载（blob: 链接会被导航拦截直接丢掉，点了没反应），改走原生
 * _nativeFileBridge：分块写入应用缓存目录的临时文件 → 系统“保存到…”对话框（SAF）由用户选位置
 * → 原生复制过去后回调 window.__onNativeSaved(token, status, message)。
 */

export type SaveOutcome = 'saved' | 'cancelled' | 'downloaded';

interface NativeFileBridge {
  beginSave(fileName: string, mimeType: string): number;
  appendSave(token: number, chunk: string): boolean;
  finishSave(token: number): void;
  cancelSave(token: number): void;
}

declare global {
  interface Window {
    _nativeFileBridge?: NativeFileBridge;
    __onNativeSaved?: (token: number, status: string, message: string) => void;
  }
}

/** 每次跨桥传输的字符数：桥是同步字符串调用，过大的单次字符串会让 WebView 内存陡增 */
const CHUNK_CHARS = 256 * 1024;

const pending = new Map<number, { resolve: (v: SaveOutcome) => void; reject: (e: Error) => void }>();

function installCallback(): void {
  if (window.__onNativeSaved) return;
  window.__onNativeSaved = (token, status, message) => {
    const waiter = pending.get(token);
    if (!waiter) return;
    pending.delete(token);
    if (status === 'saved') waiter.resolve('saved');
    else if (status === 'cancelled') waiter.resolve('cancelled');
    else waiter.reject(new Error(message || '保存失败'));
  };
}

export function canSaveNatively(): boolean {
  return typeof window !== 'undefined' && Boolean(window._nativeFileBridge);
}

export async function saveTextFile(fileName: string, text: string, mimeType: 'application/json' | 'text/plain'): Promise<SaveOutcome> {
  const bridge = window._nativeFileBridge;
  if (!bridge) {
    const blob = new Blob([text], { type: `${mimeType};charset=utf-8` });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = fileName;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    return 'downloaded';
  }

  installCallback();
  const token = bridge.beginSave(fileName, mimeType);
  if (!(token > 0)) throw new Error('无法开始保存（存储空间不足？）');
  try {
    let i = 0;
    let chunks = 0;
    while (i < text.length) {
      let end = Math.min(text.length, i + CHUNK_CHARS);
      // 不在代理对中间切开（emoji 等补充平面字符）
      const code = text.charCodeAt(end - 1);
      if (end < text.length && code >= 0xd800 && code <= 0xdbff) end--;
      if (!bridge.appendSave(token, text.slice(i, end))) throw new Error('写入临时文件失败（存储空间不足？）');
      i = end;
      // 让出主线程，避免大文件导出时界面卡死
      if (++chunks % 8 === 0) await new Promise((r) => setTimeout(r, 0));
    }
  } catch (err) {
    bridge.cancelSave(token);
    throw err;
  }
  const result = new Promise<SaveOutcome>((resolve, reject) => pending.set(token, { resolve, reject }));
  bridge.finishSave(token);
  return result;
}
