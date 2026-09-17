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
            int marginDp
    ) {
        bindWithHighlight(block, colors, fontSizeSp, lineSpacingMultiplier, marginDp, null, -1, -1);
    }

    public void bindWithHighlight(
            TextBlock block,
            ThemeColors colors,
            float fontSizeSp,
            float lineSpacingMultiplier,
            int marginDp,
            String highlightQuery,
            int activeOffsetStart,
            int activeOffsetEnd
    ) {
        this.currentBlock = block;
        if (block == null) {
            textView.setText("");
            return;
        }

        String text = block.getText();
        if (highlightQuery != null && !highlightQuery.trim().isEmpty()) {
            android.text.SpannableString spannable = new android.text.SpannableString(text);
            String lowerText = text.toLowerCase();
            String lowerQ = highlightQuery.trim().toLowerCase();
            int qLen = lowerQ.length();
            int start = 0;
            while (start < lowerText.length()) {
                int found = lowerText.indexOf(lowerQ, start);
                if (found == -1) break;
                spannable.setSpan(
                        new android.text.style.BackgroundColorSpan(android.graphics.Color.parseColor("#FFF59D")),
                        found,
                        found + qLen,
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
                start = found + qLen;
            }
            if (activeOffsetStart >= 0 && activeOffsetEnd <= text.length() && activeOffsetEnd > activeOffsetStart) {
                spannable.setSpan(
                        new android.text.style.BackgroundColorSpan(android.graphics.Color.parseColor("#FF9800")),
                        activeOffsetStart,
                        activeOffsetEnd,
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            }
            textView.setText(spannable);
        } else {
            textView.setText(text);
        }

        textView.setTextSize(fontSizeSp > 0 ? fontSizeSp : 18f);
        textView.setLineSpacing(0f, lineSpacingMultiplier > 0 ? lineSpacingMultiplier : 1.8f);

        if (colors != null) {
            textView.setTextColor(colors.text);
        }

        int padPx = (int) (textView.getContext().getResources().getDisplayMetrics().density * marginDp);
        itemView.setPadding(padPx, 4, padPx, 16);
    }

    public TextView getTextView() {
        return textView;
    }

    public TextBlock getCurrentBlock() {
        return currentBlock;
    }
}
