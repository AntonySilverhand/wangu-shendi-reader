import { el } from '../dom.ts';
import { beginOverlay } from './overlay-state.ts';

let toastRoot: HTMLElement | null = null;
let hideTimer: ReturnType<typeof setTimeout> | null = null;

export function showToast(text: string, ms = 2200): void {
  if (!toastRoot) toastRoot = document.getElementById('toast-root');
  if (!toastRoot) return;
  if (hideTimer) {
    clearTimeout(hideTimer);
    hideTimer = null;
  }
  const existing = toastRoot.querySelector('.toast');
  existing?.remove();
  const node = el('div', { class: 'toast', role: 'status', text });
  toastRoot.appendChild(node);
  hideTimer = setTimeout(() => {
    node.classList.add('hide');
    setTimeout(() => node.remove(), 300);
  }, ms);
}

export function confirmDialog(message: string, confirmLabel = '确定'): Promise<boolean> {
  return new Promise((resolve) => {
    const endOverlay = beginOverlay();
    const root = document.getElementById('overlay-root') ?? document.body;
    const backdrop = el('div', { class: 'sheet-backdrop show' });
    const okBtn = el(
      'button',
      { class: 'btn primary', type: 'button' },
      confirmLabel,
    );
    const cancelBtn = el('button', { class: 'btn', type: 'button' }, '取消');
    const dialog = el(
      'section',
      {
        class: 'sheet show',
        role: 'alertdialog',
        'aria-modal': 'true',
        style: { maxHeight: 'none' },
      },
      el(
        'div',
        { class: 'sheet-body' },
        el('p', { text: message, style: { margin: '0', lineHeight: '1.7' } }),
        el('div', { class: 'row-actions', style: { justifyContent: 'flex-end' } }, cancelBtn, okBtn),
      ),
    );
    const finish = (value: boolean) => {
      backdrop.remove();
      dialog.remove();
      document.removeEventListener('keydown', onKey, true);
      endOverlay();
      resolve(value);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') finish(false);
    };
    okBtn.addEventListener('click', () => finish(true));
    cancelBtn.addEventListener('click', () => finish(false));
    backdrop.addEventListener('click', () => finish(false));
    document.addEventListener('keydown', onKey, true);
    root.appendChild(backdrop);
    root.appendChild(dialog);
    requestAnimationFrame(() => okBtn.focus({ preventScroll: true }));
  });
}
