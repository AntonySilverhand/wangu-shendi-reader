/**
 * 封面：远程书用书源封面图（万书阁 /img/{id}.jpg、69书吧 static 图床；
 * Android 壳经受限的 /native/cover 代理），加载失败或本地导入书回退为文字封面。
 */
import { el } from './dom.ts';
import { bookCoverUrl } from '../shared/sources.ts';
import { isRemoteBookId } from './store/library.ts';

export function coverSrc(bookId: string): string | null {
  if (!isRemoteBookId(bookId)) return null;
  // 离线时直接用文字封面，避免无意义的失败请求
  if (typeof navigator !== 'undefined' && navigator.onLine === false) return null;
  if (import.meta.env.VITE_TARGET === 'android') return `/native/cover?book=${encodeURIComponent(bookId)}`;
  return bookCoverUrl(bookId);
}

/** 文字封面取书名前 4 字，竖排感由 CSS 控制 */
function coverText(title: string): string {
  return [...title.replace(/[（(【\[].*$/u, '').trim()].slice(0, 4).join('') || '书';
}

export function renderCover(bookId: string, title: string, opts: { small?: boolean } = {}): HTMLElement {
  const box = el('div', { class: `book-cover${opts.small ? ' small' : ''}`, 'aria-hidden': 'true' });
  const fallback = () => {
    box.classList.remove('has-image');
    box.textContent = coverText(title);
  };
  const src = coverSrc(bookId);
  if (!src) {
    fallback();
    return box;
  }
  box.classList.add('has-image');
  const img = el('img', {
    src,
    alt: '',
    loading: 'lazy',
    decoding: 'async',
    referrerPolicy: 'no-referrer',
  }) as HTMLImageElement;
  img.addEventListener('error', fallback, { once: true });
  box.appendChild(img);
  return box;
}
