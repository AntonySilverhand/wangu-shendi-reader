package org.wanshu.reader.ui.theme;

import android.graphics.Color;

public class ReaderThemeConfig {
    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";
    public static final String THEME_SEPIA = "sepia";
    public static final String THEME_EYECARE = "eyecare";
    public static final String THEME_OLED = "oled";

    public static ThemeColors getThemeColors(String theme) {
        if (theme == null) {
            theme = THEME_LIGHT;
        }

        switch (theme.toLowerCase()) {
            case THEME_DARK:
                return new ThemeColors(
                        Color.parseColor("#1E1E1E"),
                        Color.parseColor("#D0D0D0"),
                        Color.parseColor("#888888"),
                        Color.parseColor("#282828"),
                        Color.parseColor("#333333")
                );
            case THEME_SEPIA:
                return new ThemeColors(
                        Color.parseColor("#F4ECD8"),
                        Color.parseColor("#3D3024"),
                        Color.parseColor("#786650"),
                        Color.parseColor("#E8DFC8"),
                        Color.parseColor("#DDD2B8")
                );
            case THEME_EYECARE:
                return new ThemeColors(
                        Color.parseColor("#CCE8CF"),
                        Color.parseColor("#1B3B22"),
                        Color.parseColor("#42634A"),
                        Color.parseColor("#BBDCBE"),
                        Color.parseColor("#A8CEAB")
                );
            case THEME_OLED:
                return new ThemeColors(
                        Color.parseColor("#000000"),
                        Color.parseColor("#B8B8B8"),
                        Color.parseColor("#666666"),
                        Color.parseColor("#111111"),
                        Color.parseColor("#222222")
                );
            case THEME_LIGHT:
            default:
                return new ThemeColors(
                        Color.parseColor("#FAF7F2"),
                        Color.parseColor("#2C2723"),
                        Color.parseColor("#666666"),
                        Color.parseColor("#EFEBE4"),
                        Color.parseColor("#E5DFC5")
                );
        }
    }
}
