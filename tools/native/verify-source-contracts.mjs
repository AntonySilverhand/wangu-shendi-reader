#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import { execSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const repoRoot = path.resolve(__dirname, '../..');
const contractsDir = path.join(repoRoot, 'contracts/native');

console.log('=== Verifying Native Source Contracts Against TS Baseline ===\n');

// 1. Ensure dump contracts has been run
console.log('==> Ensuring native contracts JSON is generated...');
try {
  execSync('bash tools/native/build.sh dump-contracts', {
    cwd: repoRoot,
    stdio: 'inherit',
    env: { ...process.env },
  });
} catch (err) {
  console.error('Failed to run dump-contracts:', err);
  process.exit(1);
}

const baselinePath = path.join(contractsDir, 'source-contracts.json');
const nativePath = path.join(contractsDir, 'source-contracts-native.json');
const syntheticPath = path.join(contractsDir, 'synthetic-contracts.json');

if (!fs.existsSync(baselinePath)) {
  console.error(`Baseline contracts missing at ${baselinePath}`);
  process.exit(1);
}
if (!fs.existsSync(nativePath)) {
  console.error(`Native contracts missing at ${nativePath}`);
  process.exit(1);
}
if (!fs.existsSync(syntheticPath)) {
  console.error(`Synthetic contracts missing at ${syntheticPath}`);
  process.exit(1);
}

const baseline = JSON.parse(fs.readFileSync(baselinePath, 'utf8'));
const native = JSON.parse(fs.readFileSync(nativePath, 'utf8'));
const synthetic = JSON.parse(fs.readFileSync(syntheticPath, 'utf8'));

let passedChecks = 0;
let failedChecks = 0;

function assertEqual(label, actual, expected) {
  const actStr = JSON.stringify(actual);
  const expStr = JSON.stringify(expected);
  if (actStr === expStr) {
    passedChecks++;
    return true;
  } else {
    failedChecks++;
    console.error(`FAIL: ${label}`);
    console.error(`  Expected: ${expStr}`);
    console.error(`  Actual:   ${actStr}`);
    return false;
  }
}

// 1. Verify Single Pages
console.log('\n--- 1. Single Page Contracts ---');
const basePages = baseline.singlePages || [];
const natPages = native.singlePages || [];

assertEqual('Single pages count', natPages.length, basePages.length);

const natPagesByFile = new Map(natPages.map((p) => [p.file, p]));

for (const bp of basePages) {
  const np = natPagesByFile.get(bp.file);
  if (!np) {
    failedChecks++;
    console.error(`FAIL: Missing native single page for ${bp.file}`);
    continue;
  }

  const prefix = `[SinglePage ${bp.file}]`;
  assertEqual(`${prefix} id`, np.id, bp.id);
  assertEqual(`${prefix} pageIndex`, np.pageIndex, bp.pageIndex);
  assertEqual(`${prefix} title`, np.title, bp.title);
  assertEqual(`${prefix} declaredPageIndex`, np.declaredPageIndex, bp.declaredPageIndex);
  assertEqual(`${prefix} prevChapterId`, np.prevChapterId, bp.prevChapterId);
  assertEqual(`${prefix} nextChapterId`, np.nextChapterId, bp.nextChapterId);
  assertEqual(`${prefix} sameChapterPages`, np.sameChapterPages, bp.sameChapterPages);
  assertEqual(`${prefix} paragraphsCount`, np.paragraphsCount, bp.paragraphsCount);
  assertEqual(`${prefix} paragraphs`, np.paragraphs, bp.paragraphs);
}

// 2. Verify Assembled Chapters
console.log('\n--- 2. Assembled Chapters Contracts ---');
const baseAssembled = baseline.assembledChapters || [];
const natAssembled = native.assembledChapters || [];

assertEqual('Assembled chapters count', natAssembled.length, baseAssembled.length);

const natAssembledByCase = new Map(natAssembled.map((c) => [c.caseName, c]));

for (const ba of baseAssembled) {
  const na = natAssembledByCase.get(ba.caseName);
  if (!na) {
    failedChecks++;
    console.error(`FAIL: Missing native assembled chapter for ${ba.caseName}`);
    continue;
  }

  const prefix = `[Assembled ${ba.caseName}]`;
  assertEqual(`${prefix} id`, na.result.id, ba.result.id);
  assertEqual(`${prefix} title`, na.result.title, ba.result.title);
  assertEqual(`${prefix} prevId`, na.result.prevId, ba.result.prevId);
  assertEqual(`${prefix} nextId`, na.result.nextId, ba.result.nextId);
  assertEqual(`${prefix} pageCount`, na.result.pageCount, ba.result.pageCount);
  assertEqual(`${prefix} complete`, na.result.complete, ba.result.complete);
  assertEqual(`${prefix} missingPages`, na.result.missingPages, ba.result.missingPages);
  if (ba.caseName === 'chapter-38626103-5pages') {
    // Legacy TS incorrectly deduplicated intra-page duplicate sentence '“帝尘永恒！”' on page 1.
    // Native sourceSemanticsVersion=5 preserves intra-page repeated paragraphs (verified in synthetic contracts).
    const natParas = na.result.paragraphs;
    const baseParas = ba.result.paragraphs;
    assertEqual(`${prefix} index 10`, natParas[10], '“帝尘永恒！”');
    assertEqual(`${prefix} index 11 (preserved intra-page duplicate)`, natParas[11], '“帝尘永恒！”');
    const natWithoutDuplicate = natParas.slice(0, 11).concat(natParas.slice(12));
    assertEqual(`${prefix} paragraphs (accounting for sourceSemanticsVersion=5 intra-page fix)`, natWithoutDuplicate, baseParas);
  } else {
    assertEqual(`${prefix} paragraphs`, na.result.paragraphs, ba.result.paragraphs);
  }
}

// 3. Verify TOC Contracts
console.log('\n--- 3. TOC Contracts ---');
const baseToc = baseline.toc;
const natToc = native.toc;

assertEqual('TOC page 1 rawCount', natToc.page1.rawCount, baseToc.page1.rawCount);
assertEqual('TOC page 1 totalPagesParsed', natToc.page1.totalPagesParsed, baseToc.page1.totalPagesParsed);
assertEqual('TOC page 2 rawCount', natToc.page2.rawCount, baseToc.page2.rawCount);
assertEqual('TOC page 2 totalPagesParsed', natToc.page2.totalPagesParsed, baseToc.page2.totalPagesParsed);
assertEqual('TOC merged totalEntries', natToc.merged.totalEntries, baseToc.merged.totalEntries);
assertEqual('TOC merged firstEntry id', natToc.merged.firstEntry?.id, baseToc.merged.firstEntry?.id);
assertEqual('TOC merged firstEntry title', natToc.merged.firstEntry?.title, baseToc.merged.firstEntry?.title);
assertEqual('TOC merged lastEntry id', natToc.merged.lastEntry?.id, baseToc.merged.lastEntry?.id);
assertEqual('TOC merged lastEntry title', natToc.merged.lastEntry?.title, baseToc.merged.lastEntry?.title);

// 4. Verify Synthetic Contracts
console.log('\n--- 4. Synthetic Contracts (P0.6) ---');
const natSyn = native.synthetic;

// 4.1 Intra-page legitimate duplicate
const intraContract = synthetic.intraPageLegitimateDuplicate;
assertEqual(
  'Synthetic intraPageLegitimateDuplicate: native output matches expected',
  natSyn.intraPageLegitimateDuplicate.nativeOutput,
  intraContract.nativeExpectedOutput,
);

// 4.2 Non-adjacent missing page
const nonAdjContract = synthetic.nonAdjacentMissingPageRepetition;
assertEqual(
  'Synthetic nonAdjacentMissingPageRepetition: native output matches expected',
  natSyn.nonAdjacentMissingPageRepetition.nativeOutput,
  nonAdjContract.nativeExpectedOutput,
);
assertEqual(
  'Synthetic nonAdjacentMissingPageRepetition: complete is false',
  natSyn.nonAdjacentMissingPageRepetition.complete,
  nonAdjContract.complete,
);
assertEqual(
  'Synthetic nonAdjacentMissingPageRepetition: missingPages is [1]',
  natSyn.nonAdjacentMissingPageRepetition.missingPages,
  nonAdjContract.missingPages,
);

// 4.3 Long paragraph and emoji
const longContract = synthetic.longParagraphAndEmoji;
assertEqual(
  'Synthetic longParagraphAndEmoji: total chars match',
  natSyn.longParagraphAndEmoji.totalChars,
  longContract.longParagraphPrefix.length +
    longContract.repeatUnit.length * longContract.repeatCount +
    longContract.emojiSequence.length,
);
if (natSyn.longParagraphAndEmoji.blocksCount >= 2) {
  passedChecks++;
} else {
  failedChecks++;
  console.error('FAIL: longParagraphAndEmoji blocksCount expected >= 2, got', natSyn.longParagraphAndEmoji.blocksCount);
}

// Summary
console.log('\n======================================================');
console.log(`Contract Verification Finished: ${passedChecks} checks passed, ${failedChecks} checks failed.`);
console.log('======================================================');

if (failedChecks > 0) {
  console.error(`\nFAILED with ${failedChecks} contract mismatches!`);
  process.exit(1);
} else {
  console.log('\nSUCCESS: 100% Contract Parity between Native Java and TypeScript Baseline!');
  process.exit(0);
}
