package org.wanshu.reader.ui.reader.anchor;

import android.text.Layout;
import android.view.View;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import org.wanshu.reader.core.model.ReadingAnchor;
import org.wanshu.reader.core.text.AnchorMapper;
import org.wanshu.reader.core.text.TextBlock;
import org.wanshu.reader.ui.reader.adapter.BlockViewHolder;
import org.wanshu.reader.ui.reader.adapter.FooterViewHolder;
import org.wanshu.reader.ui.reader.adapter.HeaderViewHolder;
import org.wanshu.reader.ui.reader.adapter.ReaderBlockAdapter;

public class ReaderAnchorSampler {
    private final RecyclerView recyclerView;
    private final ReaderBlockAdapter adapter;
    private String bookId = "";
    private String chapterId = "";

    public ReaderAnchorSampler(RecyclerView recyclerView, ReaderBlockAdapter adapter) {
        this.recyclerView = recyclerView;
        this.adapter = adapter;
    }

    public void setChapter(String bookId, String chapterId) {
        this.bookId = bookId != null ? bookId : "";
        this.chapterId = chapterId != null ? chapterId : "";
    }

    public ReadingAnchor sampleAnchor() {
        if (bookId.isEmpty() || chapterId.isEmpty()) {
            return null;
        }

        int height = recyclerView.getHeight();
        int width = recyclerView.getWidth();
        if (height <= 0 || width <= 0) {
            return new ReadingAnchor(bookId, chapterId, 0, 0, System.currentTimeMillis());
        }

        // Anchor line is at ~30% height of the reading area
        int anchorY = (int) (height * 0.30f);
        float centerX = width / 2.0f;

        View child = recyclerView.findChildViewUnder(centerX, anchorY);
        if (child == null) {
            RecyclerView.LayoutManager lm = recyclerView.getLayoutManager();
            if (lm instanceof LinearLayoutManager) {
                int firstPos = ((LinearLayoutManager) lm).findFirstVisibleItemPosition();
                child = lm.findViewByPosition(firstPos);
            }
        }

        if (child == null) {
            return new ReadingAnchor(bookId, chapterId, 0, 0, System.currentTimeMillis());
        }

        RecyclerView.ViewHolder holder = recyclerView.getChildViewHolder(child);
        long now = System.currentTimeMillis();

        if (holder instanceof HeaderViewHolder) {
            return new ReadingAnchor(bookId, chapterId, 0, 0, now);
        } else if (holder instanceof BlockViewHolder) {
            BlockViewHolder bvh = (BlockViewHolder) holder;
            TextBlock block = bvh.getCurrentBlock();
            if (block == null) {
                return new ReadingAnchor(bookId, chapterId, 0, 0, now);
            }

            TextView tv = bvh.getTextView();
            int relY = (int) (anchorY - child.getY() - tv.getPaddingTop());
            if (relY < 0) {
                relY = 0;
            }

            Layout layout = tv.getLayout();
            if (layout != null) {
                int line = layout.getLineForVertical(relY);
                int charOffsetInBlock = layout.getOffsetForHorizontal(line, 0);
                return AnchorMapper.mapBlockOffsetToAnchor(bookId, chapterId, block, charOffsetInBlock, now);
            } else {
                return new ReadingAnchor(bookId, chapterId, block.getStartParagraphIndex(), 0, now);
            }
        } else if (holder instanceof FooterViewHolder) {
            List<TextBlock> blocks = adapter.getBlocks();
            if (!blocks.isEmpty()) {
                TextBlock last = blocks.get(blocks.size() - 1);
                return new ReadingAnchor(bookId, chapterId, last.getEndParagraphIndex(), 0, now);
            }
        }

        return new ReadingAnchor(bookId, chapterId, 0, 0, now);
    }
}
