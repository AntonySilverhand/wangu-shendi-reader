package org.wanshu.reader.ui.reader.adapter;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import org.wanshu.reader.core.text.TextBlock;
import org.wanshu.reader.ui.theme.ThemeColors;

public class BlockViewHolder extends RecyclerView.ViewHolder {
    private final TextView textView;
    private TextBlock currentBlock;

    public BlockViewHolder(View itemView, TextView textView) {
        super(itemView);
        this.textView = textView;
        this.textView.setTextIsSelectable(true);
    }

    public static BlockViewHolder create(ViewGroup parent) {
        Context context = parent.getContext();
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView tv = new TextView(context);
        tv.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        tv.setTextIsSelectable(true);
        layout.addView(tv);

        return new BlockViewHolder(layout, tv);
    }

    public void bind(
            TextBlock block,
            ThemeColors colors,
            float fontSizeSp,
            float lineSpacingMultiplier,
            int marginDp,
            java.util.List<org.wanshu.reader.core.search.ChapterSearchMatch> matches,
            int activeMatchIndex
    ) {
        this.currentBlock = block;
        if (block == null) {
            textView.setText("");
            return;
        }

        textView.setTextSize(fontSizeSp > 0 ? fontSizeSp : 18f);
        textView.setLineSpacing(0f, lineSpacingMultiplier > 0 ? lineSpacingMultiplier : 1.8f);

        if (colors != null) {
            textView.setTextColor(colors.text);
        }

        int padPx = (int) (textView.getContext().getResources().getDisplayMetrics().density * marginDp);
        itemView.setPadding(padPx, 4, padPx, 16);

        String text = block.getText();
        if (matches == null || matches.isEmpty()) {
            textView.setText(text);
            return;
        }

        int startP = block.getStartParagraphIndex();
        int endP = block.getEndParagraphIndex();
        android.text.SpannableString spannable = new android.text.SpannableString(text);
        boolean hasSpans = false;

        for (int i = 0; i < matches.size(); i++) {
            org.wanshu.reader.core.search.ChapterSearchMatch m = matches.get(i);
            int pIdx = m.getParagraphIndex();
            if (pIdx >= startP && pIdx <= endP) {
                int relP = pIdx - startP;
                int pOffset = block.getParagraphStartOffset(relP);
                int s = Math.max(0, pOffset + m.getStartOffset());
                int e = Math.min(text.length(), pOffset + m.getEndOffset());
                if (s < e) {
                    boolean isActive = (i == activeMatchIndex);
                    int bgColor = isActive ? android.graphics.Color.parseColor("#FF9800") : android.graphics.Color.parseColor("#FFE082");
                    spannable.setSpan(new android.text.style.BackgroundColorSpan(bgColor), s, e, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    spannable.setSpan(new android.text.style.ForegroundColorSpan(android.graphics.Color.BLACK), s, e, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    hasSpans = true;
                }
            }
        }

        if (hasSpans) {
            textView.setText(spannable);
        } else {
            textView.setText(text);
        }
    }

    public TextView getTextView() {
        return textView;
    }

    public TextBlock getCurrentBlock() {
        return currentBlock;
    }
}
