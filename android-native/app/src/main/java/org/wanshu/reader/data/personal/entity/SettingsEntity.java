package org.wanshu.reader.data.personal.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "settings")
public class SettingsEntity {
    @PrimaryKey
    @ColumnInfo(name = "id")
    public int id = 1;

    @NonNull
    @ColumnInfo(name = "theme")
    public String theme = "light";

    @NonNull
    @ColumnInfo(name = "font_family")
    public String fontFamily = "system";

    @ColumnInfo(name = "font_size")
    public float fontSize = 18.0f;

    @ColumnInfo(name = "line_height")
    public float lineHeight = 1.8f;

    @ColumnInfo(name = "paragraph_spacing")
    public float paragraphSpacing = 0.7f;

    @ColumnInfo(name = "page_margin")
    public int pageMargin = 20;

    @ColumnInfo(name = "max_width_chars")
    public int maxWidthChars = 36;

    @ColumnInfo(name = "prefetch_next_chapter")
    public boolean prefetchNextChapter = true;

    @ColumnInfo(name = "keep_screen_awake")
    public boolean keepScreenAwake = false;

    @ColumnInfo(name = "immersive_mode")
    public boolean immersiveMode = false;

    @ColumnInfo(name = "auto_cache_enabled")
    public boolean autoCacheEnabled = false;

    @NonNull
    @ColumnInfo(name = "auto_cache_scope")
    public String autoCacheScope = "ENTIRE_BOOK";

    @ColumnInfo(name = "auto_cache_metered")
    public boolean autoCacheMetered = false;

    @ColumnInfo(name = "max_cache_mb")
    public int maxCacheMb = 256;

    @ColumnInfo(name = "paper_texture_enabled")
    public boolean paperTextureEnabled = true;

    @ColumnInfo(name = "format_version")
    public int formatVersion = 1;
}
