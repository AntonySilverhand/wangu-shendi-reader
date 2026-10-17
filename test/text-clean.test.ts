import { describe, expect, it } from 'vitest';
import { cleanParagraphs, EMPTY_CHAPTER_NOTE, isJunkLine } from '../src/shared/text-clean.ts';

const PAGE_BREAK = '本小章还未完，请点击下一页继续阅读后面精彩内容！';

describe('cleanParagraphs：采集站插进正文的东西（样例取自真实整本 TXT）', () => {
  it('去掉分页提示，其余段落原样保留', () => {
    expect(cleanParagraphs(['接着，斩杀牲畜，以血祭天。', PAGE_BREAK, '“哗——”'])).toEqual([
      '接着，斩杀牲畜，以血祭天。',
      '“哗——”',
    ]);
    expect(cleanParagraphs(['甲。', '-->>本章未完，点击下一页继续阅读', '乙。'])).toEqual(['甲。', '乙。']);
  });

  it('分页处在逗号/冒号处截断的半句与下一段接上；段末是汉字的不接（多为作者漏了句号）', () => {
    expect(cleanParagraphs(['可以睡得很安稳，', PAGE_BREAK, '越想，张若尘越羡慕。'])).toEqual([
      '可以睡得很安稳，越想，张若尘越羡慕。',
    ]);
    expect(cleanParagraphs(['头发却微微松了松', PAGE_BREAK, '很显然，张若尘猜对了。'])).toEqual([
      '头发却微微松了松',
      '很显然，张若尘猜对了。',
    ]);
  });

  it('HTML 残留：注释整行删除、标签去掉保留文字、残缺标签也能识别', () => {
    expect(cleanParagraphs(['<!--over-->', '</strong>地面在颤动，并且，越来越剧烈。', '<b></b>'])).toEqual([
      '地面在颤动，并且，越来越剧烈。',
    ]);
    expect(cleanParagraphs(['正文。', '<b>如有问题请点击此处反馈给我们</b>'])).toEqual(['正文。']);
    expect(cleanParagraphs(['甲&nbsp;乙。'])).toEqual(['甲 乙。']);
  });

  it('行内水印只删水印本身', () => {
    expect(cleanParagraphs(['“画圣就在血神教，应该可以镇压一切动乱。【△網www.AiQuxs.】”'])).toEqual([
      '“画圣就在血神教，应该可以镇压一切动乱。”',
    ]);
    expect(cleanParagraphs(['“恐怕很难与它们抗衡。【△網www..】仅仅只是一个吞天魔龙。”'])).toEqual([
      '“恐怕很难与它们抗衡。仅仅只是一个吞天魔龙。”',
    ]);
    expect(
      cleanParagraphs([
        '双方顺利达成和解。顶点更新最快',
        '飞向赢沙城的各个大街小巷。本文由。lwxs520。首发',
        '那不是每一座冥楼都灯火通明的真相!（笔趣阁）',
        '才能坐稳族长之位。@·无错首发~~',
        '访问 https://example.com/a?b=1 看看',
      ]),
    ).toEqual(['双方顺利达成和解。', '飞向赢沙城的各个大街小巷。', '那不是每一座冥楼都灯火通明的真相!', '才能坐稳族长之位。', '访问 看看']);
  });

  it('整行广告与模板行', () => {
    const junk = [
      '看最新章节内容，请下载，无广告免费阅读最新章节内容。网站已经不更新最新章节内容，已经更新最新章节内容。',
      '网站内容更新慢，请下载阅读最新章节内容。',
      '一秒记住，精彩小说无弹窗免费阅读！',
      '特殊时刻,请大家多多收藏支持：()万古神帝小说网',
      '(未完待续。)',
      '（本章完）',
    ];
    expect(cleanParagraphs(['正文。', ...junk, '下文。'])).toEqual(['正文。', '下文。']);
    for (const line of junk) expect(isJunkLine(line)).toBe(true);
  });

  it('不误伤正文：普通用词、作者的话、超长段落、方括号里的招式名', () => {
    const keep = [
      '张若尘被金色巨手打中，身体轰然炸开。',
      '时间就这样一分一秒的流失。',
      '“你一直冷眼旁观，就是在寻找合适的出手机会，对吧？”',
      '推荐一本玄幻大神的新书《狩猎仙魔》，首发起点，欢迎品读。',
      '他施展出【天罗地网】，将四面八方全部封锁。',
      `${'很长的正文段落，'.repeat(12)}提到最新章节也不删。`,
      '“哗！”',
      '……',
    ];
    expect(cleanParagraphs(keep)).toEqual(keep);
    for (const line of keep) expect(isJunkLine(line)).toBe(false);
  });

  it('整章只有占位/广告时给出说明，不返回空章；本来就空的章节保持为空', () => {
    expect(
      cleanParagraphs([
        '本章节内容正在手打中，当您看到这段文字，请您<bstyle="color:red">稍后刷新</b>页面看是否已经更新，如果长久未更新，请通过下面反馈联系我们！',
        '<b>如有问题请点击此处反馈给我们</b>',
      ]),
    ).toEqual([EMPTY_CHAPTER_NOTE]);
    expect(cleanParagraphs([])).toEqual([]);
    expect(cleanParagraphs(['', '  '])).toEqual([]);
  });

  it('幂等：清洗结果再清洗不变', () => {
    const once = cleanParagraphs(['可以睡得很安稳，', PAGE_BREAK, '<!--over-->', '越想。【△網www.AiQuxs.】', '本章节内容正在手打中']);
    expect(cleanParagraphs(once)).toEqual(once);
    expect(cleanParagraphs([EMPTY_CHAPTER_NOTE])).toEqual([EMPTY_CHAPTER_NOTE]);
  });
});
