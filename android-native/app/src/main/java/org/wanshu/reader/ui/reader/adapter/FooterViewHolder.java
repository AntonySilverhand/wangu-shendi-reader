package org.wanshu.reader.ui.reader.adapter;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import androidx.recyclerview.widget.RecyclerView;
import org.wanshu.reader.ui.theme.ThemeColors;

public class FooterViewHolder extends RecyclerView.ViewHolder {
    private final Button prevBtn;
    private final Button shelfBtn;
    private final Button nextBtn;

    public FooterViewHolder(View itemView, Button prevBtn, Button shelfBtn, Button nextBtn) {
        super(itemView);
        this.prevBtn = prevBtn;
        this.shelfBtn = shelfBtn;
        this.nextBtn = nextBtn;
    }

    public static FooterViewHolder create(ViewGroup parent) {
        Context context = parent.getContext();
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(0, 48, 0, 72);
        layout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        Button prev = new Button(context);
        prev.setText("上一章");
        layout.addView(prev);

        Button shelf = new Button(context);
        shelf.setText("书架");
        layout.addView(shelf);

        Button next = new Button(context);
        next.setText("下一章");
        layout.addView(next);

        return new FooterViewHolder(layout, prev, shelf, next);
    }

    public void bind(
            boolean hasPrev,
            boolean hasNext,
            ThemeColors colors,
            View.OnClickListener prevListener,
            View.OnClickListener shelfListener,
            View.OnClickListener nextListener
    ) {
        prevBtn.setEnabled(hasPrev);
        nextBtn.setEnabled(hasNext);

        prevBtn.setOnClickListener(prevListener);
        shelfBtn.setOnClickListener(shelfListener);
        nextBtn.setOnClickListener(nextListener);
    }
}
