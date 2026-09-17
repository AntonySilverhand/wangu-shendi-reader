import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';
import {
  parseChapterPage,
  parseTocPageHtml,
  parseTocTotalPages,
  classifyTocItems,
  mergeTocPages,
  fetchChapter,
} from '../../src/shared/source.ts';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const repoRoot = path.resolve(__dirname, '../..');
const fixturesDir = path.join(repoRoot, 'test/fixtures');
const contractsDir = path.join(repoRoot, 'contracts/native');

fs.mkdirSync(contractsDir, { recursive: true });

// 1. Fixtures manifest
const fixturePurposes = {
  'chapter-13375259-p1.html': '老版本模板降级：#content 回退、无 base64 编码',
  'chapter-8924760-p1.html': '早期单页章节：常规单页 base64 解析',
  'chapter-38621328-p1.html': '多物理页章节第 1 页（共 3 页）',
  'chapter-38621328-p2.html': '多物理页章节第 2 页（共 3 页）',
  'chapter-38621328-p3.html': '多物理页章节第 3 页（末页终章证据）',
  'chapter-38621330-p2.html': '独立分页：含跨章邻接链接',
  'chapter-38621571-p1.html': '回环探针章节第 1 页',
  'chapter-38621571-p2.html': '回环探针章节第 2 页',
  'chapter-38621571-p3.html': '回环探针章节第 3 页',
  'chapter-38621571-loopback.html': '回环探针章节探针页：重复第一页正文，自报 index 偏小',
  'chapter-38626103-p1.html': '5 物理页长章第 1 页',
  'chapter-38626103-p2.html': '5 物理页长章第 2 页',
  'chapter-38626103-p3.html': '5 物理页长章第 3 页',
  'chapter-38626103-p4.html': '5 物理页长章第 4 页',
  'chapter-38626103-p5.html': '5 物理页长章第 5 页（末页）',
  'toc-page1.html': '目录第 1 页：包含置顶新书番外块与尾部快捷链接',
  'toc-page2.html': '目录第 2 页：分页目录与翻页导航',
};

const fixtureFiles = fs.readdirSync(fixturesDir).sort();
const manifest = [];

for (const file of fixtureFiles) {
  const filePath = path.join(fixturesDir, file);
  const buf = fs.readFileSync(filePath);
  const hash = crypto.createHash('sha256').update(buf).digest('hex');
  manifest.push({
    file,
    relPath: `test/fixtures/${file}`,
    purpose: fixturePurposes[file] || '未分类 fixture',
    sha256: hash,
    bytes: buf.length,
  });
}

fs.writeFileSync(
  path.join(contractsDir, 'fixtures-manifest.json'),
  JSON.stringify(manifest, null, 2),
  'utf8',
);
console.log(`Manifest written: ${manifest.length} fixtures.`);

// 2. Parse individual pages
const singlePageContracts = [];
for (const item of manifest) {
  if (!item.file.startsWith('chapter-')) continue;
  const content = fs.readFileSync(path.join(fixturesDir, item.file), 'utf8');

  // determine id and pageIndex from filename
  // chapter-{id}-p{page}.html or loopback
  const match = item.file.match(/chapter-(\d+)-(?:p(\d+)|loopback)\.html/);
  if (!match) continue;
  const id = match[1];
  const pageIndex = match[2] ? parseInt(match[2], 10) - 1 : 3; // loopback is probed as 3 in test

  const parsed = parseChapterPage(content, id, pageIndex);
  singlePageContracts.push({
    file: item.file,
    id,
    pageIndex,
    title: parsed.title,
    declaredPageIndex: parsed.declaredPageIndex,
    prevChapterId: parsed.prevChapterId,
    nextChapterId: parsed.nextChapterId,
    sameChapterPages: parsed.sameChapterPages,
    paragraphsCount: parsed.paragraphs.length,
    paragraphs: parsed.paragraphs,
  });
}

// 3. Assemble chapters using mock fetcher
async function assembleChapter(id, routes) {
  const fetcher = async (urlStr) => {
    const u = new URL(urlStr);
    const p = u.pathname;
    const body = routes[p];
    if (body === undefined) {
      return new Response('', { status: 404, headers: { 'content-type': 'text/html' } });
    }
    return new Response(body, { status: 200, headers: { 'content-type': 'text/html; charset=utf-8' } });
  };
  return await fetchChapter(id, { fetcher, retries: 0 });
}

const assembledContracts = [];

// chapter 13375259 (single page fallback)
{
  const id = '13375259';
  const routes = {
    [`/book/36780_${id}.html`]: fs.readFileSync(path.join(fixturesDir, 'chapter-13375259-p1.html'), 'utf8'),
  };
  const result = await assembleChapter(id, routes);
  assembledContracts.push({
    caseName: 'chapter-13375259-fallback',
    result,
  });
}

// chapter 8924760 (single page)
{
  const id = '8924760';
  const routes = {
    [`/book/36780_${id}.html`]: fs.readFileSync(path.join(fixturesDir, 'chapter-8924760-p1.html'), 'utf8'),
  };
  const result = await assembleChapter(id, routes);
  assembledContracts.push({
    caseName: 'chapter-8924760-single',
    result,
  });
}

// chapter 38621328 (3 pages)
{
  const id = '38621328';
  const routes = {
    [`/book/36780_${id}.html`]: fs.readFileSync(path.join(fixturesDir, 'chapter-38621328-p1.html'), 'utf8'),
    [`/book/36780/${id}_1.html`]: fs.readFileSync(path.join(fixturesDir, 'chapter-38621328-p2.html'), 'utf8'),
    [`/book/36780/${id}_2.html`]: fs.readFileSync(path.join(fixturesDir, 'chapter-38621328-p3.html'), 'utf8'),
  };
  const result = await assembleChapter(id, routes);
  assembledContracts.push({
    caseName: 'chapter-38621328-3pages',
    result,
  });
}

// chapter 38621571 (3 pages + loopback)
{
  const id = '38621571';
  const routes = {
    [`/book/36780_${id}.html`]: fs.readFileSync(path.join(fixturesDir, 'chapter-38621571-p1.html'), 'utf8'),
    [`/book/36780/${id}_1.html`]: fs.readFileSync(path.join(fixturesDir, 'chapter-38621571-p2.html'), 'utf8'),
    [`/book/36780/${id}_2.html`]: fs.readFileSync(path.join(fixturesDir, 'chapter-38621571-p3.html'), 'utf8'),
    [`/book/36780/${id}_3.html`]: fs.readFileSync(path.join(fixturesDir, 'chapter-38621571-loopback.html'), 'utf8'),
  };
  const result = await assembleChapter(id, routes);
  assembledContracts.push({
    caseName: 'chapter-38621571-loopback',
    result,
  });
}

// chapter 38626103 (5 pages)
{
  const id = '38626103';
  const routes = {
    [`/book/36780_${id}.html`]: fs.readFileSync(path.join(fixturesDir, 'chapter-38626103-p1.html'), 'utf8'),
    [`/book/36780/${id}_1.html`]: fs.readFileSync(path.join(fixturesDir, 'chapter-38626103-p2.html'), 'utf8'),
    [`/book/36780/${id}_2.html`]: fs.readFileSync(path.join(fixturesDir, 'chapter-38626103-p3.html'), 'utf8'),
    [`/book/36780/${id}_3.html`]: fs.readFileSync(path.join(fixturesDir, 'chapter-38626103-p4.html'), 'utf8'),
    [`/book/36780/${id}_4.html`]: fs.readFileSync(path.join(fixturesDir, 'chapter-38626103-p5.html'), 'utf8'),
  };
  const result = await assembleChapter(id, routes);
  assembledContracts.push({
    caseName: 'chapter-38626103-5pages',
    result,
  });
}

// 4. TOC contracts
const toc1Html = fs.readFileSync(path.join(fixturesDir, 'toc-page1.html'), 'utf8');
const toc2Html = fs.readFileSync(path.join(fixturesDir, 'toc-page2.html'), 'utf8');

const toc1Raw = parseTocPageHtml(toc1Html, 1);
const toc2Raw = parseTocPageHtml(toc2Html, 2);
const toc1Classified = classifyTocItems(toc1Raw);
const toc2Classified = classifyTocItems(toc2Raw);
const mergedToc = mergeTocPages([toc1Raw, toc2Raw]);

const tocContract = {
  page1: {
    rawCount: toc1Raw.length,
    classifiedCount: toc1Classified.length,
    totalPagesParsed: parseTocTotalPages(toc1Html),
  },
  page2: {
    rawCount: toc2Raw.length,
    classifiedCount: toc2Classified.length,
    totalPagesParsed: parseTocTotalPages(toc2Html),
  },
  merged: {
    totalEntries: mergedToc.length,
    firstEntry: mergedToc[0],
    lastEntry: mergedToc[mergedToc.length - 1],
    entriesSample: mergedToc.slice(0, 10),
  },
};

const sourceContracts = {
  singlePages: singlePageContracts,
  assembledChapters: assembledContracts,
  toc: tocContract,
};

fs.writeFileSync(
  path.join(contractsDir, 'source-contracts.json'),
  JSON.stringify(sourceContracts, null, 2),
  'utf8',
);
console.log('source-contracts.json written.');

// 5. Synthetic contracts (P0.6)
const syntheticContracts = {
  intraPageLegitimateDuplicate: {
    description: '页内重复合法句：同页内相邻相同段落属于原文，不应被错误删除',
    page0Paragraphs: [
      '第一段：两军对垒，气氛凝重。',
      '“杀！”',
      '“杀！”',
      '“杀！”',
      '后退者死，唯有向前。',
    ],
    page1Paragraphs: [
      '后退者死，唯有向前。',
      '战鼓轰鸣，天地变色。',
    ],
    legacyTsOutput: [
      '第一段：两军对垒，气氛凝重。',
      '“杀！”',
      '后退者死，唯有向前。',
      '战鼓轰鸣，天地变色。',
    ],
    nativeExpectedOutput: [
      '第一段：两军对垒，气氛凝重。',
      '“杀！”',
      '“杀！”',
      '“杀！”',
      '后退者死，唯有向前。',
      '战鼓轰鸣，天地变色。',
    ],
    rationale: 'sourceSemanticsVersion 5 仅在跨页边界消除上页末段与下页首段的重复，页内连续相同段落完整保留。',
  },
  nonAdjacentMissingPageRepetition: {
    description: '非相邻缺页重复：中间有缺失页时，不得将非相邻页误作相邻页进行跨页去重',
    page0Paragraphs: [
      '天地玄黄，宇宙洪荒。',
      '日月盈昃，辰宿列张。',
    ],
    missingPageIndex: 1,
    page2Paragraphs: [
      '日月盈昃，辰宿列张。',
      '寒来暑往，秋收冬藏。',
    ],
    nativeExpectedOutput: [
      '天地玄黄，宇宙洪荒。',
      '日月盈昃，辰宿列张。',
      '日月盈昃，辰宿列张。',
      '寒来暑往，秋收冬藏。',
    ],
    missingPages: [1],
    complete: false,
    rationale: '由于第 1 页缺失，第 0 页与第 2 页并非连续物理页，不能确定重复是否为跨页重叠，必须完整保留以待补全。',
  },
  longParagraphAndEmoji: {
    description: '超长段落与 Emoji/代理对边界：验证 UTF-16 code units 偏移与显示块切分',
    longParagraphPrefix: '万古神帝正文长段开始：',
    repeatUnit: '天地初开，大道争锋。⚡',
    repeatCount: 600,
    emojiSequence: '🐉📖⚡𠮷',
    rationale: '测试段落字符数超过 8000 UTF-16 code units 时的大段拆分与代理对完整性。',
  },
};

fs.writeFileSync(
  path.join(contractsDir, 'synthetic-contracts.json'),
  JSON.stringify(syntheticContracts, null, 2),
  'utf8',
);
console.log('synthetic-contracts.json written.');
