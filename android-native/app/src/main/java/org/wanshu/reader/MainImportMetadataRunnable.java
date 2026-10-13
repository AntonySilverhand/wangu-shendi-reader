package org.wanshu.reader;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import java.util.Locale;
import org.wanshu.reader.ui.shelf.ShelfController;

/** A document provider may perform remote I/O even for its display name. Never query it on UI. */
public final class MainImportMetadataRunnable implements Runnable {
    private final Context context;
    private final Uri uri;
    private final ShelfController shelf;
    public MainImportMetadataRunnable(Context context, Uri uri, ShelfController shelf) {
        this.context = context.getApplicationContext(); this.uri = uri; this.shelf = shelf;
    }
    @Override public void run() {
        String title = null;
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            try (Cursor cursor = context.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (column >= 0) title = cursor.getString(column);
                }
            } catch (Exception ignored) { /* Use URI name if the provider has no metadata. */ }
        }
        if (title == null) title = uri.getLastPathSegment();
        if (title != null && title.toLowerCase(Locale.ROOT).endsWith(".txt")) title = title.substring(0, title.length() - 4);
        shelf.handleImportUri(uri, title);
    }
}
