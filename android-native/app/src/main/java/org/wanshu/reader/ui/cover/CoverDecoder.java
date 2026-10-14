package org.wanshu.reader.ui.cover;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.File;

/** Decodes a cover file down to roughly the size it will be shown at. */
public final class CoverDecoder {
    private CoverDecoder() {}

    /** Largest power-of-two sample size that keeps the image at least as large as the target. */
    public static int sampleSize(int srcWidth, int srcHeight, int targetWidth, int targetHeight) {
        int sample = 1;
        if (srcWidth <= 0 || srcHeight <= 0 || targetWidth <= 0 || targetHeight <= 0) {
            return sample;
        }
        while (srcWidth / (sample * 2) >= targetWidth && srcHeight / (sample * 2) >= targetHeight) {
            sample *= 2;
        }
        return sample;
    }

    /** Returns null when the file is not a decodable image. */
    public static Bitmap decode(File file, int targetWidth, int targetHeight) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, targetWidth, targetHeight);
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }
}
