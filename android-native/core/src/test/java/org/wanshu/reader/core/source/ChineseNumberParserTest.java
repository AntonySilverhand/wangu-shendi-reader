package org.wanshu.reader.core.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ChineseNumberParserTest {

    @Test
    public void testParsing() {
        assertEquals(Integer.valueOf(1), ChineseNumberParser.parse("一"));
        assertEquals(Integer.valueOf(10), ChineseNumberParser.parse("十"));
        assertEquals(Integer.valueOf(11), ChineseNumberParser.parse("十一"));
        assertEquals(Integer.valueOf(20), ChineseNumberParser.parse("二十"));
        assertEquals(Integer.valueOf(100), ChineseNumberParser.parse("一百"));
        assertEquals(Integer.valueOf(105), ChineseNumberParser.parse("一百零五"));
        assertEquals(Integer.valueOf(803), ChineseNumberParser.parse("八百零三"));
        assertEquals(Integer.valueOf(2771), ChineseNumberParser.parse("二千七百七十一"));
        assertEquals(Integer.valueOf(4208), ChineseNumberParser.parse("四千二百零八"));
        assertEquals(Integer.valueOf(10000), ChineseNumberParser.parse("一万"));
        assertEquals(Integer.valueOf(10005), ChineseNumberParser.parse("一万零五"));

        // Direct digits
        assertEquals(Integer.valueOf(4208), ChineseNumberParser.parse("4208"));

        // Invalid
        assertNull(ChineseNumberParser.parse(""));
        assertNull(ChineseNumberParser.parse(null));
        assertNull(ChineseNumberParser.parse("abc"));
    }
}
