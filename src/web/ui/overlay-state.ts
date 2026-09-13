/** 所有 sheet/确认框共享的生命周期；替换弹窗时先注册新弹窗，避免短暂恢复沉浸。 */
const overlays = new Set<symbol>();
const listeners = new Set<() => void>();

export function hasBlockingOverlay(): boolean {
  return overlays.size > 0;
}

export function onOverlayChange(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function beginOverlay(): () => void {
  const token = Symbol();
  overlays.add(token);
  for (const listener of listeners) listener();
  return () => {
    if (!overlays.delete(token)) return;
    for (const listener of listeners) listener();
  };
}
