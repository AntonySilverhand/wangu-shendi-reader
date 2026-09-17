package org.wanshu.reader.ui.reader.adapter;

import android.view.View;
import android.view.ViewGroup;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import org.wanshu.reader.core.text.TextBlock;
import org.wanshu.reader.ui.theme.ThemeColors;

public class ReaderBlockAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private String chapterTitle = "";
    private List<TextBlock> blocks = new ArrayList<TextBlock>();
    private boolean hasPrev = false;
    private boolean hasNext = false;

    private ThemeColors colors;
    private float fontSizeSp = 18f;
    private float lineSpacingMultiplier = 1.8f;
    private int marginDp = 20;

    private View.OnClickListener prevListener;
    private View.OnClickListener shelfListener;
    private View.OnClickListener nextListener;

    private String highlightQuery = null;
    private int activeMatchParagraph = -1;
    private int activeMatchStartOffset = -1;
    private int activeMatchEndOffset = -1;

    public void setSearchHighlights(String query, int activeParagraph, int activeStart, int activeEnd) {
        this.highlightQuery = query;
        this.activeMatchParagraph = activeParagraph;
        this.activeMatchStartOffset = activeStart;
        this.activeMatchEndOffset = activeEnd;
        notifyDataSetChanged();
    }

    public void clearHighlights() {
        this.highlightQuery = null;
        this.activeMatchParagraph = -1;
        this.activeMatchStartOffset = -1;
        this.activeMatchEndOffset = -1;
        notifyDataSetChanged();
    }

    public void setData(
            String title,
            List<TextBlock> blocks,
            boolean hasPrev,
            boolean hasNext
    ) {
        this.chapterTitle = title != null ? title : "";
        this.blocks = blocks != null ? blocks : new ArrayList<TextBlock>();
        this.hasPrev = hasPrev;
        this.hasNext = hasNext;
        notifyDataSetChanged();
    }

    public void setTypography(
            ThemeColors colors,
            float fontSizeSp,
            float lineSpacingMultiplier,
            int marginDp
    ) {
        this.colors = colors;
        this.fontSizeSp = fontSizeSp;
        this.lineSpacingMultiplier = lineSpacingMultiplier;
        this.marginDp = marginDp;
        notifyDataSetChanged();
    }

    public void setListeners(
            View.OnClickListener prevListener,
            View.OnClickListener shelfListener,
            View.OnClickListener nextListener
    ) {
        this.prevListener = prevListener;
        this.shelfListener = shelfListener;
        this.nextListener = nextListener;
    }

    public List<TextBlock> getBlocks() {
        return blocks;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) {
            return ReaderItemType.TYPE_HEADER;
        } else if (position <= blocks.size()) {
            return ReaderItemType.TYPE_BLOCK;
        } else {
            return ReaderItemType.TYPE_FOOTER;
        }
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        if (viewType == ReaderItemType.TYPE_HEADER) {
            return HeaderViewHolder.create(parent);
        } else if (viewType == ReaderItemType.TYPE_BLOCK) {
            return BlockViewHolder.create(parent);
        } else {
            return FooterViewHolder.create(parent);
        }
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).bind(chapterTitle, colors);
        } else if (holder instanceof BlockViewHolder) {
            int blockIndex = position - 1;
            if (blockIndex >= 0 && blockIndex < blocks.size()) {
                TextBlock blk = blocks.get(blockIndex);
                int activeStart = -1;
                int activeEnd = -1;
                if (activeMatchParagraph >= blk.getStartParagraphIndex() && activeMatchParagraph <= blk.getEndParagraphIndex()) {
                    int relP = activeMatchParagraph - blk.getStartParagraphIndex();
                    int pStart = blk.getParagraphStartOffset(relP);
                    activeStart = pStart + activeMatchStartOffset;
                    activeEnd = pStart + activeMatchEndOffset;
                }
                ((BlockViewHolder) holder).bindWithHighlight(
                        blk,
                        colors,
                        fontSizeSp,
                        lineSpacingMultiplier,
                        marginDp,
                        highlightQuery,
                        activeStart,
                        activeEnd
                );
            }
        } else if (holder instanceof FooterViewHolder) {
            ((FooterViewHolder) holder).bind(
                    hasPrev,
                    hasNext,
                    colors,
                    prevListener,
                    shelfListener,
                    nextListener
            );
        }
    }

    @Override
    public int getItemCount() {
        if (blocks.isEmpty() && chapterTitle.isEmpty()) {
            return 0;
        }
        return blocks.size() + 2;
    }
}
