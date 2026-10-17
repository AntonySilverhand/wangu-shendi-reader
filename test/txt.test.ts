import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { prepareTxtImport, splitTxt } from '../src/web/store/txt.ts';
import { encodeGbkComponent } from '../src/shared/source-69shuba.ts';
import { makeZip } from './helpers/zip.ts';

describe('TXT 导入解析', () => {
  it('识别常见章节标题', () => {
    const text = [
      '第一章 起点',
      '第一段内容。',
      '第二段内容。',
      '',
      '第2章 转折',
      '另一些内容。',
      '番外第一章 后来',
      '番外内容。',
    ].join('\n');
    const chapters = splitTxt(text);
    expect(chapters.map((c) => c.title)).toEqual([
      '第一章 起点',
      '第2章 转折',
      '番外第一章 后来',
    ]);
    expect(chapters[0]!.lines).toEqual(['第一段内容。', '第二段内容。']);
    expect(chapters[1]!.lines).toEqual(['另一些内容。']);
  });

  it('无标题时归入“正文”', () => {
    const chapters = splitTxt('只有一段文字。\n还有一段。');
    expect(chapters).toHaveLength(1);
    expect(chapters[0]!.title).toBe('正文');
    expect(chapters[0]!.lines).toEqual(['只有一段文字。', '还有一段。']);
  });

  it('支持中文数字与阿拉伯数字章节号', () => {
    const chapters = splitTxt('第一百二十三章 标题\n内容\nChapter 5 Something\ncontent');
    expect(chapters).toHaveLength(2);
    expect(chapters[1]!.title).toContain('Chapter 5');
  });
});

describe('TXT / ZIP 导入（编码与格式识别）', () => {
  const here = dirname(fileURLToPath(import.meta.url));
  const sample = () => new Uint8Array(readFileSync(join(here, 'fixtures', 'ixdzs', 'sample-gb18030.txt')));
  /** encodeGbkComponent 的百分号编码 → GBK 字节 */
  const gbk = (text: string): Uint8Array => {
    const enc = encodeGbkComponent(text);
    const out: number[] = [];
    for (let i = 0; i < enc.length; i++) {
      if (enc[i] === '%') {
        out.push(parseInt(enc.slice(i + 1, i + 3), 16));
        i += 2;
      } else out.push(enc.charCodeAt(i));
    }
    return new Uint8Array(out);
  };

  it('站方 ZIP（GB18030 TXT）：解压、识别编码，书名/作者取自文件头，按顶格标题分章', async () => {
    const r = await prepareTxtImport('137695.zip', makeZip('万古神帝之张劫外传.txt', sample()));
    expect(r.title).toBe('万古神帝之张劫外传');
    expect(r.author).toBe('小姚爱运动');
    expect(r.chapters.slice(0, 4).map((c) => c.title)).toEqual(['第1章落魄后裔', '第2章深渊奇遇', '第3章击杀仇人', '第5章美人计']);
    expect(r.chapters[0]!.paragraphs).toEqual(['清平镇的早晨，雾还没有散。', '“劫哥，又去山里？”']);
    const all = r.chapters.flatMap((c) => [c.title, ...c.paragraphs]).join('\n');
    expect(all).not.toMatch(/ixdzs|爱下电子书|已完结|章节内容开始|『/);
  });

  it('直接导入 GB18030 的 TXT（不是 ZIP）', async () => {
    const r = await prepareTxtImport('外传.txt', sample());
    expect(r.title).toBe('万古神帝之张劫外传');
    expect(r.chapters[1]!.title).toBe('第2章深渊奇遇');
  });

  it('普通 GBK TXT 用通用标题规则，不再乱码；UTF-8 照常', async () => {
    const text = '第一章 起点\n　　第一段内容。\n第2章 转折\n　　另一些内容。\n';
    const fromGbk = await prepareTxtImport('我的书.txt', gbk(text));
    expect(fromGbk.title).toBe('我的书');
    expect(fromGbk.chapters.map((c) => c.title)).toEqual(['第一章 起点', '第2章 转折']);
    expect(fromGbk.chapters[0]!.paragraphs).toEqual(['第一段内容。']);
    const fromUtf8 = await prepareTxtImport('我的书.txt', new TextEncoder().encode(text));
    expect(fromUtf8.chapters).toEqual(fromGbk.chapters);
  });

  it('不含 TXT 的 ZIP、空文件给出明确错误', async () => {
    await expect(prepareTxtImport('a.zip', makeZip('cover.jpg', new Uint8Array([1, 2, 3])))).rejects.toThrow('没有可导入的 TXT');
    await expect(prepareTxtImport('a.txt', new Uint8Array())).rejects.toThrow('没有识别到任何章节');
  });
});
