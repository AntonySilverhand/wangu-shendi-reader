package org.wanshu.reader.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** Width-driven wrapping actions, never squeeze hit targets into a fixed button row. */
public final class ActionFlowLayout extends ViewGroup {
    public ActionFlowLayout(Context context) { super(context); }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int available = Math.max(0, MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight());
        int x = 0, y = 0, rowHeight = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            child.measure(MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            if (x > 0 && x + child.getMeasuredWidth() > available) {
                y += rowHeight; x = 0; rowHeight = 0;
            }
            x += child.getMeasuredWidth();
            rowHeight = Math.max(rowHeight, child.getMeasuredHeight());
        }
        setMeasuredDimension(resolveSize(MeasureSpec.getSize(widthSpec), widthSpec),
                resolveSize(y + rowHeight + getPaddingTop() + getPaddingBottom(), heightSpec));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int available = getWidth() - getPaddingRight();
        int x = getPaddingLeft(), y = getPaddingTop(), rowHeight = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            int w = child.getMeasuredWidth(), h = child.getMeasuredHeight();
            if (x > getPaddingLeft() && x + w > available) {
                y += rowHeight; x = getPaddingLeft(); rowHeight = 0;
            }
            child.layout(x, y, x + w, y + h);
            x += w; rowHeight = Math.max(rowHeight, h);
        }
    }
}
