package org.wanshu.reader.ui.reader;

import android.text.Layout;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import org.wanshu.reader.core.text.AnchorMapper;
import org.wanshu.reader.core.text.TextBlock;
import org.wanshu.reader.ui.reader.adapter.BlockViewHolder;

/** Coarse block placement followed by bounded line correction; no persistent frame loop. */
public final class ReaderRestoreAnchorRunnable implements Runnable {
    private final ReaderView view;
    private final int paragraph, offset;
    private final List<TextBlock> blocks;
    private int attempt;
    public ReaderRestoreAnchorRunnable(ReaderView view, int paragraph, int offset) {
        this.view = view; this.paragraph = paragraph; this.offset = offset;
        this.blocks = view.getAdapter().getBlocks();
    }
    @Override
    public void run() {
        if (!view.isCurrentRestore(this) || blocks.isEmpty() || view.getAdapter().getBlocks() != blocks || !view.isAttachedToWindow()) return;
        RecyclerView list = view.getRecyclerView();
        int blockIndex = AnchorMapper.findBlockIndexForAnchor(blocks, paragraph, offset);
        int position = blockIndex + 1;
        int anchorY = Math.round(list.getHeight() * 0.3f);
        if (attempt++ == 0) {
            ((LinearLayoutManager) list.getLayoutManager()).scrollToPositionWithOffset(position, anchorY);
            list.postOnAnimation(this);
            return;
        }
        RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(position);
        if (!(holder instanceof BlockViewHolder)) {
            if (attempt < 3) list.postOnAnimation(this);
            return;
        }
        TextView text = ((BlockViewHolder) holder).getTextView();
        Layout layout = text.getLayout();
        if (layout == null) return;
        int charOffset = AnchorMapper.getOffsetInBlock(blocks.get(blockIndex), paragraph, offset);
        int line = layout.getLineForOffset(Math.min(charOffset, text.length()));
        float textTop = holder.itemView.getY() + text.getTop() + text.getTotalPaddingTop();
        list.scrollBy(0, Math.round(textTop + layout.getLineTop(line) - anchorY));
    }
}
