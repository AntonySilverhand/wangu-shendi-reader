package org.wanshu.reader.core.text;

import java.util.ArrayList;
import java.util.List;

public class TextBlockBuilder {
    public static final int DEFAULT_TARGET_BLOCK_SIZE = 6000;

    private final int targetBlockSize;

    public TextBlockBuilder(int targetBlockSize) {
        this.targetBlockSize = targetBlockSize >= 100 ? targetBlockSize : DEFAULT_TARGET_BLOCK_SIZE;
    }

    public TextBlockBuilder() {
        this(DEFAULT_TARGET_BLOCK_SIZE);
    }

    public List<TextBlock> buildBlocks(List<String> paragraphs) {
        List<TextBlock> blocks = new ArrayList<TextBlock>();
        if (paragraphs == null || paragraphs.isEmpty()) {
            return blocks;
        }

        StringBuilder currentText = new StringBuilder();
        List<Integer> currentOffsets = new ArrayList<Integer>();
        int currentStartPara = 0;
        int blockIndex = 0;

        for (int i = 0; i < paragraphs.size(); i++) {
            String p = paragraphs.get(i);
            if (p == null) {
                p = "";
            }

            // If current block is not empty and adding this paragraph exceeds targetBlockSize
            if (currentText.length() > 0 && (currentText.length() + p.length() + 2 > targetBlockSize)) {
                blocks.add(new TextBlock(
                        blockIndex,
                        currentStartPara,
                        i - 1,
                        currentText.toString(),
                        currentOffsets
                ));
                blockIndex++;
                currentText.setLength(0);
                currentOffsets = new ArrayList<Integer>();
                currentStartPara = i;
            }

            // If a single paragraph is larger than targetBlockSize, we split into safe sub-chunks
            if (p.length() > targetBlockSize) {
                // Split large paragraph into safe sub-chunks
                int subOffset = 0;
                while (subOffset < p.length()) {
                    int end = Math.min(subOffset + targetBlockSize, p.length());
                    // Don't split in the middle of a surrogate pair
                    if (end < p.length() && Character.isHighSurrogate(p.charAt(end - 1))) {
                        end--;
                    }
                    String chunk = p.substring(subOffset, end);
                    currentOffsets.add(currentText.length());
                    currentText.append(chunk);

                    blocks.add(new TextBlock(
                            blockIndex,
                            i,
                            i,
                            currentText.toString(),
                            currentOffsets
                    ));
                    blockIndex++;
                    currentText.setLength(0);
                    currentOffsets = new ArrayList<Integer>();
                    currentStartPara = i + 1;
                    subOffset = end;
                }
            } else {
                if (currentText.length() > 0) {
                    currentText.append("\n\n");
                }
                currentOffsets.add(currentText.length());
                currentText.append(p);
            }
        }

        if (currentText.length() > 0) {
            blocks.add(new TextBlock(
                    blockIndex,
                    currentStartPara,
                    paragraphs.size() - 1,
                    currentText.toString(),
                    currentOffsets
            ));
        }

        return blocks;
    }
}
