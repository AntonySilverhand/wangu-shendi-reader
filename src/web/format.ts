import type { TocEntry } from '../shared/source.ts';

export function chapterLabel(entry: TocEntry | null | undefined, fallbackId?: string): string {
  if (!entry) return fallbackId ? `章节 ${fallbackId}` : '未知章节';
  return entry.displayTitle;
}

/** 目录行右侧的短标题（去掉“第N章”前缀） */
export function shortTitle(entry: TocEntry): string {
  const dt = entry.displayTitle;
  if (entry.extra) return dt;
  const m = dt.match(/^第[\d零〇一二三四五六七八九十百千万两]+章[\s　]*(.*)$/);
  if (m) return m[1] || '正文';
  return dt;
}

export function chapterOrdinal(entry: TocEntry | null | undefined): string {
  if (!entry) return '';
  if (entry.extra) return '番外';
  if (entry.number !== null) return String(entry.number);
  return '·';
}

export function formatTime(ts: number): string {
  const d = new Date(ts);
  const now = new Date();
  const sameDay =
    d.getFullYear() === now.getFullYear() &&
    d.getMonth() === now.getMonth() &&
    d.getDate() === now.getDate();
  const pad = (n: number) => String(n).padStart(2, '0');
  if (sameDay) return `${pad(d.getHours())}:${pad(d.getMinutes())}`;
  if (d.getFullYear() === now.getFullYear()) return `${d.getMonth() + 1}月${d.getDate()}日`;
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

export function formatDate(ts: number): string {
  const d = new Date(ts);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

export function excerpt(text: string, max = 60): string {
  const t = text.replace(/\s+/g, ' ').trim();
  return t.length > max ? `${t.slice(0, max)}…` : t;
}
