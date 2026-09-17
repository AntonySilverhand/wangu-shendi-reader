package org.wanshu.reader.ui.reader;

import org.wanshu.reader.ui.theme.ThemeColors;

public class ReaderApplyTypographyRunnable implements Runnable {
    private final ReaderView view;
    private final ThemeColors colors;
    private final float fontSizeSp;
    private final float lineSpacingMultiplier;
    private final int marginDp;

    public ReaderApplyTypographyRunnable(
            ReaderView view,
            ThemeColors colors,
            float fontSizeSp,
            float lineSpacingMultiplier,
            int marginDp
    ) {
        this.view = view;
        this.colors = colors;
        this.fontSizeSp = fontSizeSp;
        this.lineSpacingMultiplier = lineSpacingMultiplier;
        this.marginDp = marginDp;
    }

    @Override
    public void run() {
        view.setTypography(colors, fontSizeSp, lineSpacingMultiplier, marginDp);
    }
}
