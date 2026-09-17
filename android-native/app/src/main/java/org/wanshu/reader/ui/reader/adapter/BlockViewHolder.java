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
        this.currentBlock = block;
        if (block == null) {
            textView.setText("");
            return;
        }

        textView.setText(block.getText());
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
