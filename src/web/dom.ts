/** 极简 DOM 工具：不引入框架，保持运行时体积极小。 */

export type Child = Node | string | number | null | undefined | false;

type Props = Record<string, unknown>;

export function el<K extends keyof HTMLElementTagNameMap>(
  tag: K,
  props?: Props | null,
  ...children: Child[]
): HTMLElementTagNameMap[K] {
  const node = document.createElement(tag);
  if (props) {
    for (const [key, value] of Object.entries(props)) {
      if (value === undefined || value === null || value === false) continue;
      if (key === 'class') node.className = String(value);
      else if (key === 'html') node.innerHTML = String(value);
      else if (key === 'text') node.textContent = String(value);
      else if (key === 'dataset') {
        for (const [dk, dv] of Object.entries(value as Record<string, string>)) {
          node.dataset[dk] = dv;
        }
      } else if (key === 'style' && typeof value === 'object') {
        Object.assign(node.style, value as Record<string, string>);
      } else if (key.startsWith('on') && typeof value === 'function') {
        const event = key.slice(2).toLowerCase();
        node.addEventListener(event, value as EventListener);
      } else if (key in node) {
        (node as unknown as Record<string, unknown>)[key] = value;
      } else {
        node.setAttribute(key, String(value));
      }
    }
  }
  append(node, children);
  return node;
}

export function append(parent: Node, children: Child[]): void {
  for (const child of children) {
    if (child === null || child === undefined || child === false) continue;
    parent.appendChild(child instanceof Node ? child : document.createTextNode(String(child)));
  }
}

export function clear(node: Node): void {
  while (node.firstChild) node.removeChild(node.firstChild);
}

export function on<K extends keyof WindowEventMap>(
  target: Window,
  type: K,
  handler: (event: WindowEventMap[K]) => void,
  opts?: AddEventListenerOptions,
): () => void;
export function on<K extends keyof DocumentEventMap>(
  target: Document,
  type: K,
  handler: (event: DocumentEventMap[K]) => void,
  opts?: AddEventListenerOptions,
): () => void;
export function on<K extends keyof HTMLElementEventMap>(
  target: HTMLElement,
  type: K,
  handler: (event: HTMLElementEventMap[K]) => void,
  opts?: AddEventListenerOptions,
): () => void;
export function on(
  target: EventTarget,
  type: string,
  handler: EventListenerOrEventListenerObject,
  opts?: AddEventListenerOptions,
): () => void {
  target.addEventListener(type, handler, opts);
  return () => target.removeEventListener(type, handler, opts);
}

const ICON_PATHS: Record<string, string> = {
  list: 'M4 6h16M4 12h16M4 18h16',
  left: 'M15 18l-6-6 6-6',
  right: 'M9 6l6 6-6 6',
  up: 'M6 15l6-6 6 6',
  down: 'M6 9l6 6 6-6',
  home: 'M3 11l9-8 9 8v9a1 1 0 01-1 1h-5v-6H9v6H4a1 1 0 01-1-1z',
  settings:
    'M4 21v-7M4 10V3M12 21v-9M12 8V3M20 21v-5M20 12V3M1 14h6M9 8h6M17 16h6',
  bookmark: 'M6 3h12v18l-6-4.5L6 21z',
  bookmarkFilled: 'M6 3h12v18l-6-4.5L6 21z',
  download: 'M12 3v12m0 0l-4-4m4 4l4-4M4 21h16',
  search: 'M11 18a7 7 0 100-14 7 7 0 000 14zM20.5 20.5L16 16',
  close: 'M6 6l12 12M18 6L6 18',
  refresh: 'M20.5 12a8.5 8.5 0 11-2.5-6M20.5 3.5V9h-5.5',
  trash: 'M4 7h16M9 7V4.5h6V7M6.5 7l1 13.5h9l1-13.5',
  clock: 'M12 21a9 9 0 100-18 9 9 0 000 18zM12 7.5V12l3 2',
  book: 'M4 5.5A2.5 2.5 0 016.5 3H20v18H6.5A2.5 2.5 0 014 18.5zM20 16.5H6.5A2.5 2.5 0 004 19',
  type: 'M4 7V4h16v3M12 4v16M9 20h6',
  check: 'M4.5 12.5l4.5 4.5L19.5 6.5',
  palette:
    'M12 21a9 9 0 110-18c4.97 0 9 3.58 9 8 0 2.5-2 4-4.5 4H14a1.5 1.5 0 00-1.5 1.5c0 .6.3.9.3 1.5 0 1.6-1 3-2.8 3z',
  more: 'M12 6.5h.01M12 12h.01M12 17.5h.01',
  share: 'M12 15V4m0 0L8 8m4-4l4 4M5 14v5a1 1 0 001 1h12a1 1 0 001-1v-5',
  upload: 'M12 16V5m0 0L8 9m4-4l4 4M5 19h14',
  wifiOff: 'M2 2l20 20M8.5 16.4a5 5 0 017 0M5 12.9a10 10 0 014-2.3M12 20h.01',
  dots: 'M5 12h.01M12 12h.01M19 12h.01',
  sun: 'M12 17a5 5 0 100-10 5 5 0 000 10zM12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4',
  moon: 'M20 14.5A8.5 8.5 0 019.5 4a8.5 8.5 0 1010.5 10.5z',
};

export function icon(name: keyof typeof ICON_PATHS | string, size = 22): HTMLSpanElement {
  const path = ICON_PATHS[name] ?? ICON_PATHS.dots!;
  const span = document.createElement('span');
  span.className = 'icon';
  span.setAttribute('aria-hidden', 'true');
  span.innerHTML = `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="${path}"/></svg>`;
  return span;
}

export interface IconButtonOptions {
  label: string;
  icon: string;
  onClick: (event: MouseEvent) => void;
  disabled?: boolean;
  class?: string;
  testId?: string;
}

export function iconButton(opts: IconButtonOptions): HTMLButtonElement {
  const btn = el(
    'button',
    {
      class: `icon-btn ${opts.class ?? ''}`.trim(),
      type: 'button',
      title: opts.label,
      'aria-label': opts.label,
      disabled: opts.disabled ?? false,
      onclick: opts.onClick,
    },
    icon(opts.icon),
  );
  if (opts.testId) btn.dataset.testid = opts.testId;
  return btn;
}

export function debounce<A extends unknown[]>(
  fn: (...args: A) => void,
  waitMs: number,
): (...args: A) => void {
  let timer: ReturnType<typeof setTimeout> | null = null;
  return (...args: A) => {
    if (timer) clearTimeout(timer);
    timer = setTimeout(() => {
      timer = null;
      fn(...args);
    }, waitMs);
  };
}

export function throttleRaf(fn: () => void): () => void {
  let scheduled = false;
  return () => {
    if (scheduled) return;
    scheduled = true;
    requestAnimationFrame(() => {
      scheduled = false;
      fn();
    });
  };
}

/** 元素内字符偏移 → Range（用于恢复阅读位置、搜索高亮） */
export function rangeAtOffset(root: Node, offset: number): Range | null {
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
  let remaining = offset;
  let node = walker.nextNode();
  while (node) {
    const len = node.textContent?.length ?? 0;
    if (remaining <= len) {
      const range = document.createRange();
      range.setStart(node, Math.max(0, remaining));
      range.collapse(true);
      return range;
    }
    remaining -= len;
    node = walker.nextNode();
  }
  return null;
}

/** Range → 元素内字符偏移 */
export function textOffsetWithin(root: Node, node: Node, nodeOffset: number): number {
  try {
    const range = document.createRange();
    range.setStart(root, 0);
    range.setEnd(node, nodeOffset);
    return range.toString().length;
  } catch {
    return 0;
  }
}
