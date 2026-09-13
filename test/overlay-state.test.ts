import { describe, expect, it } from 'vitest';
import { beginOverlay, hasBlockingOverlay, onOverlayChange } from '../src/web/ui/overlay-state.ts';

describe('shared overlay lifecycle', () => {
  it('replacement and nested confirmation never briefly release immersive suppression', () => {
    const states: boolean[] = [];
    const unsubscribe = onOverlayChange(() => states.push(hasBlockingOverlay()));
    const closeData = beginOverlay();
    const closeDownload = beginOverlay(); // register replacement before closing old sheet
    closeData();
    const closeConfirm = beginOverlay();
    closeConfirm();
    closeConfirm(); // idempotent
    expect(states).toEqual([true, true, true, true, true]);
    closeDownload();
    expect(hasBlockingOverlay()).toBe(false);
    expect(states.at(-1)).toBe(false);
    unsubscribe();
  });
});
