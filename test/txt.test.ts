import { describe, expect, it } from 'vitest';
import { splitTxt } from '../src/web/store/txt.ts';

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
