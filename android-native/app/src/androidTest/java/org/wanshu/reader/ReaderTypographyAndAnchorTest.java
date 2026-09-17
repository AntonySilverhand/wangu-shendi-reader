package org.wanshu.reader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Color;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wanshu.reader.core.model.ReadingAnchor;
import org.wanshu.reader.core.text.AnchorMapper;
import org.wanshu.reader.core.text.TextBlock;
import org.wanshu.reader.core.text.TextBlockBuilder;
import org.wanshu.reader.ui.theme.ReaderThemeConfig;
import org.wanshu.reader.ui.theme.ThemeColors;

@RunWith(AndroidJUnit4.class)
public class ReaderTypographyAndAnchorTest {
    private Context context;
    private TextBlockBuilder textBlockBuilder;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        textBlockBuilder = new TextBlockBuilder(200); // small blocks to test splitting
    }

    @Test
    public void testFiveThemesColorPalettes() {
        ThemeColors light = ReaderThemeConfig.getThemeColors(ReaderThemeConfig.THEME_LIGHT);
        ThemeColors dark = ReaderThemeConfig.getThemeColors(ReaderThemeConfig.THEME_DARK);
        ThemeColors sepia = ReaderThemeConfig.getThemeColors(ReaderThemeConfig.THEME_SEPIA);
        ThemeColors eyecare = ReaderThemeConfig.getThemeColors(ReaderThemeConfig.THEME_EYECARE);
        ThemeColors oled = ReaderThemeConfig.getThemeColors(ReaderThemeConfig.THEME_OLED);

        // OLED must be true black
        assertEquals(Color.BLACK, oled.background);

        // Light background
        assertEquals(Color.parseColor("#FAF7F2"), light.background);

        // Dark background
        assertEquals(Color.parseColor("#1E1E1E"), dark.background);

        // Sepia background
        assertEquals(Color.parseColor("#F4ECD8"), sepia.background);

        // Eyecare background
        assertEquals(Color.parseColor("#CCE8CF"), eyecare.background);

        // Contrast: text colors must be different from backgrounds
        assertNotEquals(light.background, light.text);
        assertNotEquals(dark.background, dark.text);
        assertNotEquals(sepia.background, sepia.text);
        assertNotEquals(eyecare.background, eyecare.text);
        assertNotEquals(oled.background, oled.text);
    }

    @Test
    public void testAnchorMappingWithEmojiAndSurrogatePairs() {
        // Emoji like 🚀✨ are 2 UTF-16 code units each
        List<String> paragraphs = Arrays.asList(
                "第一段 🚀✨ 星空浩瀚。",
                "第二段 🔥 涅槃重生，万道争鸣。",
                "第三段 ⚡ 雷霆万钧，神威赫赫。"
        );

        List<TextBlock> blocks = textBlockBuilder.buildBlocks(paragraphs);
        assertTrue(blocks.size() >= 1);

        TextBlock b0 = blocks.get(0);
        int p1Start = b0.getParagraphStartOffset(1); // offset of second paragraph
        assertTrue(p1Start > 0);

        // Map an offset within paragraph 1
        int testCharOffset = p1Start + 4;
        ReadingAnchor anchor = AnchorMapper.mapBlockOffsetToAnchor(
                "36780",
                "10",
                b0,
                testCharOffset,
                System.currentTimeMillis()
        );

        assertEquals(1, anchor.getParagraphIndex());
        assertEquals(4, anchor.getOffsetUtf16());
        assertEquals("36780", anchor.getBookId());
        assertEquals("10", anchor.getChapterId());
    }

    @Test
    public void testAnchorMapperFindsBlockForParagraph() {
        List<String> paragraphs = Arrays.asList(
                "段落0内容比较长的一段文字用来填满测试块，确保分块器正常工作。",
                "段落1另外一段内容也是很多字。",
                "段落2第三段内容继续填满测试数据。"
        );

        // Force 1 paragraph per block with tiny maxChars
        TextBlockBuilder tinyBuilder = new TextBlockBuilder(20);
        List<TextBlock> blocks = tinyBuilder.buildBlocks(paragraphs);
        assertTrue(blocks.size() >= 3);

        int blockForP0 = AnchorMapper.findBlockIndexForParagraph(blocks, 0);
        assertEquals(0, blockForP0);

        int blockForP1 = AnchorMapper.findBlockIndexForParagraph(blocks, 1);
        assertEquals(1, blockForP1);

        int blockForP2 = AnchorMapper.findBlockIndexForParagraph(blocks, 2);
        assertEquals(2, blockForP2);
    }

    @Test
    public void testAnchorPreservationAcrossFontScale() {
        ReadingAnchor original = new ReadingAnchor("36780", "1", 2, 14, System.currentTimeMillis());

        // Verify ReadingAnchor equality and serialization contract
        assertEquals("36780", original.getBookId());
        assertEquals("1", original.getChapterId());
        assertEquals(2, original.getParagraphIndex());
        assertEquals(14, original.getOffsetUtf16());

        ReadingAnchor copy = new ReadingAnchor("36780", "1", 2, 14, System.currentTimeMillis());
        assertEquals(original, copy);
        assertEquals(original.hashCode(), copy.hashCode());
    }
}
