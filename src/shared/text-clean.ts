/**
 * 正文清洗（同构）：去掉采集站/盗版站混进正文的东西——分页提示、站点水印与广告行、HTML 残留。
 *
 * 所有正文出口都走这里：在线书源解析、TXT 导入与整本下载入库、读取已缓存/已导入的章节、导出 TXT。
 * 读取时也清洗，所以升级前导入的书和旧缓存不用重新导入/获取。清洗是幂等的（清洗结果再清洗不变）。
 *
 * 原则：宁可漏删，不可误删正文。整行规则只作用于短行（广告行都很短）；行内规则只认带网址/站名的
 * 固定写法；作者的话（求票、推荐新书、公众号等）属于原文，不动。
 */

/** 整行规则只看这么长以内的行：真实广告行都很短，长段落一定是正文 */
const JUNK_LINE_MAX = 80;

/**
 * 分页提示：原站一章分几页，采集时把“下一页”提示一起抓了进来，
 * 如“本小章还未完，请点击下一页继续阅读后面精彩内容！”“-->>本章未完，点击下一页继续阅读”。
 * 它所在的位置就是原网页的分页处，前一段可能在句中被截断（见 OPEN_END_RE）。
 */
const PAGE_BREAK_RE = /^[^\p{L}\p{N}]*(?:本小?章节?(?:还|尚)?未完|请?点击下一页|请翻页)/u;

/** 整行广告/模板行（只对短行生效） */
const JUNK_LINE_RES: readonly RegExp[] = [
  /^[(（]?本章完[)）]?$/,
  // 起点等站自动插入的“(未完待续。如果您喜欢这部作品，欢迎您来起点投推荐票…)”
  /^[(（]?未完待续/,
  /如有问题请点击此处反馈/,
  // 采集站的占位章：“本章节内容正在手打中，当您看到这段文字，请您稍后刷新页面…”
  /本章节内容正在手打中/,
  /天才一秒记住|一秒记住[【\[『「：:，,]|无弹窗|最新网址|网址发布页|请记住本(?:书|站)/,
  // “看最新章节内容，请下载…”“网站内容更新慢，请下载阅读最新章节内容。”
  /最新章节/,
  /章节错误.{0,6}(?:点此|报送|举报)/,
  /(?:手机|移动)(?:用户|版|阅读)请?(?:访问|浏览|阅读|地址)/,
  // “特殊时刻,请大家多多收藏支持：()万古神帝小说网”
  /[(（]\s*[)）]\s*\S{0,16}(?:小说网|书城|中文网)\s*$/,
];

/** 行内水印与 HTML 残留：只删这些片段，保留同一段里的正文 */
const INLINE_JUNK_RES: readonly RegExp[] = [
  /<!--[\s\S]*?-->/g,
  // HTML 标签，包括“<bstyle="color:red">”这类缺空格的残缺标签；中文文本里不会出现“<字母”
  /<\/?[a-z][a-z0-9]*(?:[\s=/][^<>]*)?>/gi,
  // 带网址的括号水印：“【△網www.AiQuxs.】”“【△網www..】”
  /[【\[［(（][^【】\[\]［］()（）]{0,12}(?:www|ｗｗｗ|https?:)[^【】\[\]［］()（）]{0,40}[】\]］)）]/gi,
  /(?:https?:\/\/|www[.．])[\w.．\-/?=&%#]*/gi,
  // “本文由。lwxs520。首发”“顶点更新最快”“首发哦亲”“@·无错首发~~”“（笔趣阁）”“笔趣阁5200”
  /本文由\S{1,24}?首发/g,
  /顶点更新最快|首发哦亲|[@·\s]*无错首发[~～]*/g,
  /[（(]?笔趣阁\w*[）)]?/g,
];

/** 含这些字符才需要跑行内规则（绝大多数段落走快速路径） */
const INLINE_SUSPECT_RE = /[<&]|www|ｗｗｗ|https?:|首发|笔趣阁|更新最快/i;

const ENTITIES: Readonly<Record<string, string>> = {
  nbsp: ' ',
  emsp: ' ',
  ensp: ' ',
  amp: '&',
  lt: '<',
  gt: '>',
  quot: '"',
  '#39': "'",
};

/**
 * 分页处被截断的半句：段末是逗号/顿号/分号/冒号时与下一段接上。段末是汉字的不接——
 * 实测多为作者漏了句号（“…头发却微微松了松”/“很显然…”），硬接会连成一句读不通的话。
 */
const OPEN_END_RE = /[，、,；;：:]$/;

/** 整章只有占位/广告文字时显示的说明（不留空白章，也不留误导性的“请稍后刷新”） */
export const EMPTY_CHAPTER_NOTE = '（本章在来源中没有正文）';

function stripInlineJunk(text: string): string {
  const trimmed = text.trim();
  if (!INLINE_SUSPECT_RE.test(trimmed)) return trimmed;
  let t = trimmed;
  for (const re of INLINE_JUNK_RES) t = t.replace(re, '');
  t = t.replace(/&(#39|[a-z]+);/gi, (m, name: string) => ENTITIES[name.toLowerCase()] ?? m);
  return t === trimmed ? trimmed : t.replace(/[ \t]{2,}/g, ' ').trim();
}

/** 单行是否是采集站插入的分页提示/广告/模板行（TXT 切章时用来避免把它当标题） */
export function isJunkLine(line: string): boolean {
  const t = stripInlineJunk(line);
  if (!t) return true;
  if (t.length > JUNK_LINE_MAX) return false;
  return PAGE_BREAK_RE.test(t) || JUNK_LINE_RES.some((re) => re.test(t));
}

/**
 * 清洗一章的段落：去掉分页提示、广告行、水印与 HTML 残留；分页提示处被截断的半句与下一段接上。
 * 整章都是占位/广告时返回一条说明，不返回空章。
 */
export function cleanParagraphs(paragraphs: readonly string[]): string[] {
  const out: string[] = [];
  let joinNext = false;
  let hadText = false;
  for (const raw of paragraphs) {
    if (raw.trim()) hadText = true;
    const line = stripInlineJunk(raw);
    if (!line) continue;
    if (line.length <= JUNK_LINE_MAX) {
      if (PAGE_BREAK_RE.test(line)) {
        const prev = out[out.length - 1];
        joinNext = prev !== undefined && OPEN_END_RE.test(prev);
        continue;
      }
      if (JUNK_LINE_RES.some((re) => re.test(line))) continue;
    }
    if (joinNext) out[out.length - 1] += line;
    else out.push(line);
    joinNext = false;
  }
  if (out.length === 0 && hadText) return [EMPTY_CHAPTER_NOTE];
  return out;
}

/** 清洗后的字数（与 ChapterResult.charCount 同口径：段落字符数之和） */
export function countChars(paragraphs: readonly string[]): number {
  return paragraphs.reduce((n, p) => n + p.length, 0);
}
