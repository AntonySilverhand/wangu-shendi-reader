import { el, icon, iconButton } from '../dom.ts';
import { beginOverlay } from './overlay-state.ts';

export interface SheetOptions {
  title: string;
  body: HTMLElement;
  testId?: string;
  onClose?: () => void;
  /** 头部右侧附加按钮 */
  headActions?: HTMLElement[];
  wide?: boolean;
}

export interface SheetHandle {
  close: () => void;
  root: HTMLElement;
  body: HTMLElement;
  setTitle: (title: string) => void;
}

let active: SheetHandle | null = null;

export function openSheet(opts: SheetOptions): SheetHandle {
  const endOverlay = beginOverlay();
  active?.close();
  const root = document.getElementById('overlay-root') ?? document.body;
  const previouslyFocused = document.activeElement as HTMLElement | null;

  const backdrop = el('div', { class: 'sheet-backdrop', role: 'presentation' });
  const closeBtn = iconButton({
    label: '关闭',
    icon: 'close',
    testId: 'sheet-close',
    onClick: () => handle.close(),
  });
  const titleEl = el('h2', { text: opts.title, id: 'sheet-title' });
  const head = el(
    'div',
    { class: 'sheet-head' },
    titleEl,
    ...(opts.headActions ?? []),
    closeBtn,
  );
  const sheet = el(
    'section',
    {
      class: 'sheet',
      role: 'dialog',
      'aria-modal': 'true',
      'aria-labelledby': 'sheet-title',
      style: opts.wide ? { width: 'min(680px, calc(100vw - 32px))' } : undefined,
    },
    head,
    opts.body,
  );
  if (opts.testId) sheet.dataset.testid = opts.testId;

  root.appendChild(backdrop);
  root.appendChild(sheet);
  requestAnimationFrame(() => {
    backdrop.classList.add('show');
    sheet.classList.add('show');
  });

  const onKeydown = (event: KeyboardEvent) => {
    if (event.key === 'Escape') {
      event.stopPropagation();
      handle.close();
      return;
    }
    if (event.key === 'Tab') {
      const focusables = sheet.querySelectorAll<HTMLElement>(
        'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])',
      );
      if (focusables.length === 0) return;
      const first = focusables[0]!;
      const last = focusables[focusables.length - 1]!;
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    }
  };
  document.addEventListener('keydown', onKeydown, true);

  let closed = false;
  const handle: SheetHandle = {
    root: sheet,
    body: opts.body,
    setTitle: (t: string) => {
      titleEl.textContent = t;
    },
    close: () => {
      if (closed) return;
      closed = true;
      document.removeEventListener('keydown', onKeydown, true);
      backdrop.classList.remove('show');
      sheet.classList.remove('show');
      const remove = () => {
        backdrop.remove();
        sheet.remove();
      };
      if (matchMedia('(prefers-reduced-motion: reduce)').matches) remove();
      else setTimeout(remove, 260);
      if (sheet.contains(document.activeElement)) (document.activeElement as HTMLElement)?.blur();
      if (previouslyFocused?.isConnected) previouslyFocused.focus();
      opts.onClose?.();
      if (active === handle) active = null;
      endOverlay();
    },
  };

  backdrop.addEventListener('click', () => handle.close());
  active = handle;

  // 初始焦点：第一个输入/按钮，但不打扰屏幕阅读器朗读标题
  requestAnimationFrame(() => {
    const focusTarget = sheet.querySelector<HTMLElement>(
      'input:not([type="hidden"]), button:not(.icon-btn), [tabindex="0"]',
    );
    focusTarget?.focus({ preventScroll: true });
  });

  return handle;
}

export function sheetSection(title: string, ...children: (Node | string)[]): HTMLElement {
  return el(
    'section',
    { class: 'field' },
    el('label', { text: title }),
    ...children,
  );
}

export function sheetDivider(text?: string): HTMLElement {
  return el('hr', {
    style: {
      border: 0,
      borderTop: '1px solid var(--border)',
      margin: '2px 0',
      width: '100%',
    },
    'aria-label': text,
  });
}

export function sheetIconLabel(name: string, text: string): HTMLElement {
  return el('span', { style: { display: 'inline-flex', alignItems: 'center', gap: '6px' } }, icon(name, 16), text);
}

export function closeActiveSheet(): boolean {
  if (!active) return false;
  active.close();
  return true;
}

export function hasActiveSheet(): boolean {
  return active !== null;
}
