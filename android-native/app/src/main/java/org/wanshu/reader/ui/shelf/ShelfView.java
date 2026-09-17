package org.wanshu.reader.ui.shelf;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;
import org.wanshu.reader.core.model.ReadingAnchor;
import org.wanshu.reader.data.content.entity.BookEntity;

public class ShelfView extends FrameLayout {
    private final LinearLayout booksContainer;
    private final Button importButton;
    private final LinearLayout progressLayout;
    private final TextView progressText;
    private final ProgressBar progressBar;

    public ShelfView(Context context) {
        super(context);
        setBackgroundColor(Color.parseColor("#FAF7F2"));

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // 1. Header
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(32, 32, 32, 24);
        header.setBackgroundColor(Color.parseColor("#EFEBE4"));

        TextView headerTitle = new TextView(context);
        headerTitle.setText("书架");
        headerTitle.setTextSize(22f);
        headerTitle.setTypeface(Typeface.DEFAULT_BOLD);
        headerTitle.setTextColor(Color.parseColor("#2C2723"));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f);
        header.addView(headerTitle, titleParams);

        importButton = new Button(context);
        importButton.setText("导入 TXT");
        header.addView(importButton);
        root.addView(header);

        // 2. Import progress bar (initially hidden)
        progressLayout = new LinearLayout(context);
        progressLayout.setOrientation(LinearLayout.HORIZONTAL);
        progressLayout.setGravity(Gravity.CENTER_VERTICAL);
        progressLayout.setPadding(32, 16, 32, 16);
        progressLayout.setVisibility(View.GONE);

        progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleSmall);
        progressLayout.addView(progressBar);

        progressText = new TextView(context);
        progressText.setPadding(16, 0, 0, 0);
        progressText.setTextSize(14f);
        progressText.setTextColor(Color.parseColor("#666666"));
        progressLayout.addView(progressText);

        root.addView(progressLayout);

        // 3. Books scroll container
        ScrollView scrollView = new ScrollView(context);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        booksContainer = new LinearLayout(context);
        booksContainer.setOrientation(LinearLayout.VERTICAL);
        booksContainer.setPadding(24, 16, 24, 32);
        scrollView.addView(booksContainer);

        root.addView(scrollView);
        addView(root);
    }

    public void setImportClickListener(View.OnClickListener listener) {
        importButton.setOnClickListener(listener);
    }

    public void showImportProgress(String message) {
        progressLayout.setVisibility(View.VISIBLE);
        progressText.setText(message != null ? message : "正在导入...");
    }

    public void hideImportProgress() {
        progressLayout.setVisibility(View.GONE);
    }

    public void displayBooks(List<ShelfItemModel> items, ShelfController controller) {
        booksContainer.removeAllViews();
        Context ctx = getContext();

        if (items == null || items.isEmpty()) {
            TextView emptyView = new TextView(ctx);
            emptyView.setText("书架暂无书籍");
            emptyView.setPadding(32, 48, 32, 48);
            emptyView.setGravity(Gravity.CENTER);
            booksContainer.addView(emptyView);
            return;
        }

        for (int i = 0; i < items.size(); i++) {
            ShelfItemModel item = items.get(i);
            BookEntity book = item.getBook();
            ReadingAnchor anchor = item.getAnchor();

            LinearLayout card = new LinearLayout(ctx);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(28, 24, 28, 24);
            card.setBackgroundColor(Color.WHITE);

            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            cardParams.setMargins(0, 8, 0, 16);
            card.setLayoutParams(cardParams);

            // Title
            TextView title = new TextView(ctx);
            title.setText(book.title);
            title.setTextSize(18f);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            title.setTextColor(Color.parseColor("#2C2723"));
            card.addView(title);

            // Subtitle
            TextView subtitle = new TextView(ctx);
            String typeStr = "LOCAL_TXT".equals(book.sourceType) ? "本地 TXT" : "在线书源";
            subtitle.setText(book.author + " · " + typeStr + (book.chapterCount > 0 ? " · 共 " + book.chapterCount + " 章" : ""));
            subtitle.setTextSize(13f);
            subtitle.setTextColor(Color.parseColor("#888888"));
            subtitle.setPadding(0, 6, 0, 8);
            card.addView(subtitle);

            // Progress text
            TextView progress = new TextView(ctx);
            if (anchor != null && anchor.getChapterId() != null && !anchor.getChapterId().isEmpty()) {
                progress.setText("上次读到: 第 " + anchor.getChapterId() + " 章 (段落 " + (anchor.getParagraphIndex() + 1) + ")");
            } else {
                progress.setText("未读");
            }
            progress.setTextSize(14f);
            progress.setTextColor(Color.parseColor("#4A6B82"));
            progress.setPadding(0, 0, 0, 12);
            card.addView(progress);

            // Buttons row
            LinearLayout btnRow = new LinearLayout(ctx);
            btnRow.setOrientation(LinearLayout.HORIZONTAL);

            Button continueBtn = new Button(ctx);
            continueBtn.setText("继续阅读");
            continueBtn.setOnClickListener(new ShelfContinueClickListener(controller, book.bookId, anchor));
            btnRow.addView(continueBtn);

            Button startBtn = new Button(ctx);
            startBtn.setText("从头阅读");
            startBtn.setOnClickListener(new ShelfStartClickListener(controller, book.bookId));
            btnRow.addView(startBtn);

            if ("LOCAL_TXT".equals(book.sourceType)) {
                Button deleteBtn = new Button(ctx);
                deleteBtn.setText("删除");
                deleteBtn.setTextColor(Color.parseColor("#B00020"));
                deleteBtn.setOnClickListener(new ShelfDeleteClickListener(controller, book.bookId, book.title));
                btnRow.addView(deleteBtn);
            }

            card.addView(btnRow);
            booksContainer.addView(card);
        }
    }
}
