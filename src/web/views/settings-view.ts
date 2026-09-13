/**
 * 设置 / 数据 / 下载 / 关于 面板。
 */
import { clear, el } from '../dom.ts';
import { openSheet, sheetSection, type SheetHandle } from '../ui/sheet.ts';
import { showToast, confirmDialog } from '../ui/toast.ts';
import {
  FONT_LABELS,
  THEME_LABELS,
  type FontName,
  type Settings,
  type SettingsStore,
  type ThemeName,
} from '../store/settings.ts';
import type { PersonalStore } from '../store/personal.ts';
import type { TocStore } from '../store/toc.ts';
import { formatBytes, storageStats, clearBookContent, type StorageStats } from '../store/db.ts';
import type { DownloadManager, DownloadState } from '../store/download.ts';
import { formatDate } from '../format.ts';
import { isNativeDisplayAvailable } from '../native-display.ts';

export interface SettingsDeps {
  settings: SettingsStore;
  personal: PersonalStore;
  toc: TocStore;
  download: DownloadManager;
  bookId: string;
  bookTitle: string;
  getCurrentChapterIndex: () => number;
  onLayoutChanged: () => void;
  onContentCleared: () => void;
  onImportPersonal: (json: string, mode: 'merge' | 'replace') => void;
  onExportPersonal: () => string;
  onImportTxt: (file: File) => Promise<void>;
  onExportTxt: (fromIndex: number, toIndex: number) => Promise<void>;
  onInstallPwa?: () => void;
  onOpenAbout?: () => void;
}

const THEME_SWATCH: Record<ThemeName, string> = {
  light: '#fffefb',
  dark: '#1e2023',
  black: '#000',
  eink: '#ededed',
  paper: '#f5edda',
};

function segmented<T extends string>(
  options: { value: T; label: string; hint?: string }[],
  current: T,
  onSelect: (value: T) => void,
  swatch?: (value: T) => string | null,
): HTMLElement {
  const wrap = el('div', { class: 'seg', role: 'group' });
  for (const opt of options) {
    const color = swatch?.(opt.value) ?? null;
    const btn = el(
      'button',
      {
        type: 'button',
        'aria-pressed': String(opt.value === current),
        title: opt.hint ?? opt.label,
      },
      color ? el('span', { class: 'swatch', style: { background: color } }) : null,
      opt.label,
    );
    btn.addEventListener('click', () => {
      for (const b of wrap.querySelectorAll('button')) b.setAttribute('aria-pressed', 'false');
      btn.setAttribute('aria-pressed', 'true');
      onSelect(opt.value);
    });
    wrap.appendChild(btn);
  }
  return wrap;
}

function slider(
  label: string,
  value: number,
  min: number,
  max: number,
  step: number,
  format: (v: number) => string,
  onInput: (v: number) => void,
): HTMLElement {
  const valueEl = el('span', { class: 'value', text: format(value) });
  const input = el('input', {
    type: 'range',
    min: String(min),
    max: String(max),
    step: String(step),
    value: String(value),
    'aria-label': label,
  }) as HTMLInputElement;
  input.addEventListener('input', () => {
    const v = parseFloat(input.value);
    valueEl.textContent = format(v);
    onInput(v);
  });
  return el(
    'div',
    { class: 'field' },
    el('label', {}, label, ' ', valueEl),
    el('div', { class: 'range-row' }, input),
  );
}

function toggle(
  label: string,
  hint: string,
  checked: boolean,
  onChange: (v: boolean) => void,
): HTMLElement {
  const input = el('input', { type: 'checkbox', checked, 'aria-label': label }) as HTMLInputElement;
  input.addEventListener('change', () => onChange(input.checked));
  return el(
    'div',
    { class: 'toggle-row' },
    el('span', { class: 'label' }, el('strong', { text: label }), el('small', { text: hint })),
    el('label', { class: 'switch' }, input, el('span', { class: 'track' })),
  );
}

export function openSettingsSheet(deps: SettingsDeps): SheetHandle {
  const body = el('div', { class: 'sheet-body' });
  const s = deps.settings.get();

  const apply = (patch: Partial<Settings>) => {
    deps.settings.update(patch);
    deps.onLayoutChanged();
  };

  body.appendChild(
    sheetSection(
      '主题',
      segmented<ThemeName>(
        (Object.keys(THEME_LABELS) as ThemeName[]).map((t) => ({ value: t, label: THEME_LABELS[t] })),
        s.theme,
        (theme) => {
          apply({ theme });
          renderPaperToggle();
        },
        (t) => THEME_SWATCH[t],
      ),
    ),
  );

  body.appendChild(
    sheetSection(
      '字体',
      segmented<FontName>(
        (Object.keys(FONT_LABELS) as FontName[]).map((f) => ({ value: f, label: FONT_LABELS[f] })),
        s.font,
        (font) => {
          apply({ font });
          for (const b of document.querySelectorAll<HTMLElement>('.seg button')) {
            const label = b.textContent ?? '';
            b.style.fontFamily =
              label === '宋体'
                ? 'var(--font-serif)'
                : label === '楷体'
                  ? 'var(--font-kai)'
                  : label === '黑体'
                    ? 'var(--font-hei)'
                    : '';
          }
        },
      ),
    ),
  );

  body.appendChild(
    slider('字号', s.fontSize, 14, 28, 1, (v) => `${v}px`, (v) => apply({ fontSize: v })),
  );
  body.appendChild(
    slider('行距', s.lineHeight, 1.3, 2.4, 0.05, (v) => v.toFixed(2), (v) => apply({ lineHeight: v })),
  );
  body.appendChild(
    slider('段间距', s.paraGap, 0, 1.6, 0.05, (v) => `${v.toFixed(2)}em`, (v) => apply({ paraGap: v })),
  );
  body.appendChild(
    slider('页边距', s.margin, 8, 64, 2, (v) => `${v}px`, (v) => apply({ margin: v })),
  );
  body.appendChild(
    slider('宽屏行宽', s.maxWidth, 24, 60, 1, (v) => `约 ${v} 字/行`, (v) => apply({ maxWidth: v })),
  );

  const paperWrap = el('div');
  const renderPaperToggle = () => {
    clear(paperWrap);
    paperWrap.appendChild(
      toggle('纸张纹理', '静态细纹，可关闭', deps.settings.get().paperTexture, (v) => apply({ paperTexture: v })),
    );
  };
  renderPaperToggle();
  body.appendChild(paperWrap);

  body.appendChild(
    toggle('滚动时自动隐藏工具栏', '点击正文可随时唤出', s.autoHideBars, (v) => apply({ autoHideBars: v })),
  );
  body.appendChild(
    toggle('预取下一章', '阅读时提前缓存下一章，省流量模式自动关闭', s.prefetch, (v) => apply({ prefetch: v })),
  );
  body.appendChild(
    toggle('阅读时保持屏幕常亮', '默认关闭；开启后仅在阅读页生效', s.keepScreenAwake, (v) =>
      apply({ keepScreenAwake: v }),
    ),
  );
  if (isNativeDisplayAvailable()) {
    body.appendChild(
      toggle('沉浸式阅读', '阅读时隐藏系统状态栏与导航栏，边缘轻扫呼出', s.immersiveReading, (v) =>
        apply({ immersiveReading: v }),
      ),
    );
  }

  const resetBtn = el('button', { class: 'btn ghost small', type: 'button' }, '恢复默认排版');
  resetBtn.addEventListener('click', async () => {
    const ok = await confirmDialog('恢复默认主题与排版设置？（不影响书签和进度）');
    if (!ok) return;
    deps.settings.update({
      theme: 'light',
      font: 'system',
      fontSize: 18,
      lineHeight: 1.8,
      paraGap: 0.7,
      margin: 20,
      maxWidth: 38,
      paperTexture: true,
      autoHideBars: true,
      prefetch: true,
      keepScreenAwake: false,
      immersiveReading: false,
    });
    deps.onLayoutChanged();
    showToast('已恢复默认设置');
  });
  body.appendChild(el('div', { class: 'row-actions' }, resetBtn));

  return openSheet({ title: '主题与排版', body, testId: 'settings-sheet' });
}

/* ------------------------------------------------------------------ */

export function openDataSheet(deps: SettingsDeps): SheetHandle {
  const body = el('div', { class: 'sheet-body' });
  const statsBox = el('div', { class: 'stat-grid' });

  const refreshStats = async () => {
    const stats: StorageStats = await storageStats(deps.bookId);
    clear(statsBox);
    statsBox.appendChild(stat('已缓存章节', String(stats.chapters)));
    statsBox.appendChild(stat('正文体积', formatBytes(stats.bytes)));
    statsBox.appendChild(
      stat('浏览器已用', stats.usage != null ? formatBytes(stats.usage) : '不可用'),
    );
    statsBox.appendChild(stat('可用配额', stats.quota != null ? formatBytes(stats.quota) : '不可用'));
  };
  void refreshStats();

  body.appendChild(sheetSection('正文缓存（可随时重新获取）', statsBox));
  const clearBtn = el('button', { class: 'btn danger small', type: 'button' }, '清理正文缓存');
  clearBtn.addEventListener('click', async () => {
    const ok = await confirmDialog('清理全部已缓存正文？书签和阅读进度会保留。');
    if (!ok) return;
    const n = await clearBookContent(deps.bookId);
    showToast(`已清理 ${n} 章缓存`);
    deps.onContentCleared();
    void refreshStats();
  });
  body.appendChild(el('div', { class: 'row-actions' }, clearBtn));

  const dlBtn = el('button', { class: 'btn primary small', type: 'button' }, '下载章节…');
  dlBtn.addEventListener('click', () => openDownloadSheet(deps));
  body.appendChild(
    sheetSection('离线阅读', el('p', { class: 'loading-note', style: { textAlign: 'left' }, text: '下载后可在断网时阅读。未下载章节仍需联网。' }), el('div', { class: 'row-actions' }, dlBtn)),
  );

  // TXT 导入 / 导出
  const txtInput = el('input', {
    type: 'file',
    accept: '.txt,text/plain',
    style: { display: 'none' },
  }) as HTMLInputElement;
  txtInput.addEventListener('change', async () => {
    const file = txtInput.files?.[0];
    txtInput.value = '';
    if (!file) return;
    try {
      await deps.onImportTxt(file);
    } catch (err) {
      showToast(err instanceof Error ? err.message : '导入失败');
    }
  });
  const importTxtBtn = el('button', { class: 'btn small', type: 'button' }, '导入 TXT');
  importTxtBtn.addEventListener('click', () => txtInput.click());
  const exportTxtBtn = el('button', { class: 'btn small', type: 'button' }, '导出已缓存为 TXT');
  exportTxtBtn.addEventListener('click', async () => {
    const from = 0;
    const to = Math.max(0, deps.toc.mainCount - 1);
    if (deps.toc.mainCount === 0) {
      showToast('目录尚未加载');
      return;
    }
    await deps.onExportTxt(from, to);
  });
  body.appendChild(
    sheetSection(
      '本地文件',
      el('p', { class: 'loading-note', style: { textAlign: 'left' }, text: '书源不可用时，可导入 TXT 继续阅读。' }),
      el('div', { class: 'row-actions' }, importTxtBtn, exportTxtBtn, txtInput),
    ),
  );

  // 个人数据导出 / 导入
  const storageNote = el('p', {
    class: 'loading-note',
    style: { textAlign: 'left' },
    text: deps.personal.storageHealthy
      ? '个人数据存储正常（每次变更立即落盘）'
      : `⚠ 个人数据写入失败：${deps.personal.storageError ?? '未知原因'}（书签/进度可能无法保存）`,
  });
  const exportPersonalBtn = el('button', { class: 'btn small', type: 'button' }, '导出书签与进度');
  exportPersonalBtn.addEventListener('click', () => {
    const json = deps.onExportPersonal();
    const blob = new Blob([json], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = el('a', { href: url, download: `万古神帝-阅读数据-${new Date().toISOString().slice(0, 10)}.json` });
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    showToast('已导出个人数据');
  });
  const importPersonalInput = el('input', {
    type: 'file',
    accept: '.json,application/json',
    style: { display: 'none' },
  }) as HTMLInputElement;
  importPersonalInput.addEventListener('change', async () => {
    const file = importPersonalInput.files?.[0];
    importPersonalInput.value = '';
    if (!file) return;
    const text = await file.text();
    const merge = await confirmDialog('合并导入（保留现有书签）？选择取消则整体替换。', '合并导入');
    try {
      deps.onImportPersonal(text, merge ? 'merge' : 'replace');
      showToast('导入完成');
    } catch {
      showToast('文件格式不正确');
    }
  });
  const importPersonalBtn = el('button', { class: 'btn small', type: 'button' }, '恢复阅读数据');
  importPersonalBtn.addEventListener('click', () => importPersonalInput.click());
  body.appendChild(
    sheetSection(
      '个人数据（书签、进度、设置）',
      el('div', { class: 'row-actions' }, exportPersonalBtn, importPersonalBtn, importPersonalInput),
      storageNote,
    ),
  );

  if (deps.onInstallPwa) {
    const installBtn = el('button', { class: 'btn small', type: 'button' }, '安装到主屏幕');
    installBtn.addEventListener('click', () => deps.onInstallPwa?.());
    body.appendChild(sheetSection('应用', el('div', { class: 'row-actions' }, installBtn)));
  }

  if (deps.onOpenAbout) {
    const aboutBtn = el('button', { class: 'btn small', type: 'button' }, '帮助与关于');
    aboutBtn.addEventListener('click', () => deps.onOpenAbout?.());
    body.appendChild(sheetSection('帮助', el('div', { class: 'row-actions' }, aboutBtn)));
  }

  return openSheet({ title: '数据与缓存', body, testId: 'data-sheet' });
}

function stat(label: string, value: string): HTMLElement {
  return el('div', { class: 'stat' }, el('b', { text: value }), el('span', { text: label }));
}

/* ------------------------------------------------------------------ */

export function openDownloadSheet(deps: SettingsDeps): SheetHandle {
  const body = el('div', { class: 'sheet-body' });
  const currentIndex = Math.max(0, deps.getCurrentChapterIndex());
  let fromIndex = currentIndex;
  let toIndex = Math.min(deps.toc.mainCount - 1, currentIndex + 49);

  const infoEl = el('div', { class: 'loading-note', style: { textAlign: 'left' } });
  const fromInput = el('input', { type: 'number', min: '1', inputmode: 'numeric', 'aria-label': '起始章号' }) as HTMLInputElement;
  const toInput = el('input', { type: 'number', min: '1', inputmode: 'numeric', 'aria-label': '结束章号' }) as HTMLInputElement;

  const syncInputs = () => {
    const fromEntry = deps.toc.entryAt(fromIndex);
    const toEntry = deps.toc.entryAt(toIndex);
    fromInput.value = fromEntry?.number != null ? String(fromEntry.number) : String(fromIndex + 1);
    toInput.value = toEntry?.number != null ? String(toEntry.number) : String(toIndex + 1);
    const count = Math.max(0, toIndex - fromIndex + 1);
    infoEl.textContent = `第 ${fromIndex + 1} – ${toIndex + 1} 章，共 ${count} 章${
      deps.toc.isComplete ? '' : `（目录仅加载 ${deps.toc.mainCount} 章）`
    }`;
  };

  const applyNumbers = () => {
    const fromNum = parseInt(fromInput.value, 10);
    const toNum = parseInt(toInput.value, 10);
    if (!Number.isFinite(fromNum) || !Number.isFinite(toNum)) return;
    const fi = deps.toc.nearestToNumber(fromNum);
    const ti = deps.toc.nearestToNumber(toNum);
    if (fi >= 0) fromIndex = fi;
    if (ti >= 0) toIndex = Math.max(fi, ti);
    syncInputs();
  };
  fromInput.addEventListener('change', applyNumbers);
  toInput.addEventListener('change', applyNumbers);

  const quick = (count: number | 'all') => {
    if (count === 'all') {
      fromIndex = 0;
      toIndex = Math.max(0, deps.toc.mainCount - 1);
    } else {
      fromIndex = currentIndex;
      toIndex = Math.min(deps.toc.mainCount - 1, currentIndex + count - 1);
    }
    syncInputs();
  };

  body.appendChild(
    sheetSection(
      '章节范围',
      el('div', { class: 'range-row' }, fromInput, el('span', { text: '—' }), toInput),
      el(
        'div',
        { class: 'seg' },
        ...[50, 100, 300].map((n) =>
          el('button', { type: 'button', onclick: () => quick(n) }, `当前起 ${n} 章`),
        ),
        el('button', { type: 'button', onclick: () => quick('all') }, '全部章节'),
      ),
      infoEl,
    ),
  );

  const progressBox = el('div', { class: 'download-progress' });
  const logBox = el('div', { class: 'log-list' });
  const startBtn = el('button', { class: 'btn primary', type: 'button', id: 'download-start' }, '开始下载');
  const cancelBtn = el('button', { class: 'btn', type: 'button', disabled: true }, '取消');
  const retryBtn = el('button', { class: 'btn', type: 'button', disabled: true }, '重试失败章节');

  const renderState = (state: DownloadState) => {
    const pct = state.total > 0 ? Math.round((state.done / state.total) * 100) : 0;
    clear(progressBox);
    const bar = el('div', { class: 'progress-track' }, el('div', { class: 'progress-fill', style: { width: `${pct}%` } }));
    progressBox.appendChild(bar);
    progressBox.appendChild(
      el('div', {
        class: 'loading-note',
        style: { textAlign: 'left' },
        text:
          state.status === 'running'
            ? `下载中 ${state.done}/${state.total}（跳过 ${state.skipped}）`
            : state.status === 'cancelling'
              ? '正在取消…'
              : state.finishedAt
                ? `已完成 ${state.done}/${state.total}（跳过 ${state.skipped}，失败 ${state.failed.length}）`
                : '尚未开始',
      }),
    );
    clear(logBox);
    for (const f of state.failed.slice(0, 30)) {
      logBox.appendChild(el('div', { text: `✕ ${f.title || f.id}：${f.message}` }));
    }
    startBtn.disabled = state.status === 'running';
    cancelBtn.disabled = state.status !== 'running';
    retryBtn.disabled = state.status === 'running' || state.failed.length === 0;
  };

  const unsub = deps.download.subscribeDownload(renderState);
  renderState(deps.download.getState());

  startBtn.addEventListener('click', () => {
    if (!deps.toc.isComplete) {
      showToast('目录尚未加载完整，先下载目录');
      void deps.toc.loadRemaining().then((result) => {
        if (result === 'exhausted') showToast('部分目录页加载失败，可稍后重试');
      });
      return;
    }
    void deps.download.start(fromIndex, toIndex);
  });
  cancelBtn.addEventListener('click', () => deps.download.cancel());
  retryBtn.addEventListener('click', () => void deps.download.retryFailed());

  body.appendChild(sheetSection('进度', progressBox, logBox));
  body.appendChild(el('div', { class: 'row-actions' }, startBtn, cancelBtn, retryBtn));

  syncInputs();
  const handle = openSheet({ title: '下载章节', body, testId: 'download-sheet' });
  const origClose = handle.close;
  handle.close = () => {
    unsub();
    origClose();
  };
  return handle;
}

/* ------------------------------------------------------------------ */

export function openAboutSheet(deps: SettingsDeps): SheetHandle {
  const body = el('div', { class: 'sheet-body' });
  body.appendChild(
    sheetSection(
      '关于',
      el('p', { class: 'loading-note', style: { textAlign: 'left' }, text: `${deps.bookTitle} · 个人阅读器` }),
      el('p', {
        class: 'loading-note',
        style: { textAlign: 'left' },
        text: '正文按需从书源获取并缓存在本机；书签、进度只保存在浏览器本地，不会自动跨设备同步。',
      }),
    ),
  );
  const shortcuts: [string, string][] = [
    ['← / →', '上一章 / 下一章'],
    ['T', '打开或关闭目录'],
    ['F', '本章内搜索'],
    ['B', '添加或取消书签'],
    ['S / ,', '主题与排版设置'],
    ['Esc', '关闭浮层 / 搜索'],
    ['Home / End', '章首 / 章尾（浏览器默认）'],
  ];
  const list = el('div', { class: 'kv-list' });
  for (const [k, v] of shortcuts) {
    list.appendChild(el('div', { class: 'kv' }, el('span', { text: v }), el('span', { text: k })));
  }
  body.appendChild(sheetSection('键盘快捷键（输入框聚焦时不拦截）', list));
  body.appendChild(
    sheetSection(
      '数据说明',
      el('p', {
        class: 'loading-note',
        style: { textAlign: 'left' },
        text: `个人数据更新于 ${formatDate(deps.personal.getProgress(deps.bookId)?.updatedAt ?? Date.now())}`,
      }),
    ),
  );
  return openSheet({ title: '帮助与关于', body });
}
