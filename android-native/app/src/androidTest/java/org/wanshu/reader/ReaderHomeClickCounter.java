package org.wanshu.reader;

import android.view.View;

public final class ReaderHomeClickCounter implements View.OnClickListener {
    int clicks;
    @Override public void onClick(View view) { clicks++; }
}
