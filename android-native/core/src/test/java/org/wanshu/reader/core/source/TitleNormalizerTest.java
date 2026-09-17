package org.wanshu.reader.core.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TitleNormalizerTest {

    @Test
    public void testNormalizeTitle() {
        TitleInfo info1 = TitleNormalizer.normalizeTitle("第一章 八百年后");
        assertEquals("第一章 八百年后", info1.getDisplayTitle());
        assertEquals(Integer.valueOf(1), info1.getNumber());
        assertFalse(info1.isExtra());

        TitleInfo infoSection = TitleNormalizer.normalizeTitle("分节阅读第100节");
        assertEquals("第100章", infoSection.getDisplayTitle());
        assertEquals(Integer.valueOf(100), infoSection.getNumber());
        assertFalse(infoSection.isExtra());

        TitleInfo infoExtra = TitleNormalizer.normalizeTitle("番外第一章 剑皇之道");
        assertEquals("番外第一章 剑皇之道", infoExtra.getDisplayTitle());
        assertEquals(Integer.valueOf(1), infoExtra.getNumber());
        assertTrue(infoExtra.isExtra());

        TitleInfo infoPrefix = TitleNormalizer.normalizeTitle("803 第803章 真的阴间");
        assertEquals("第803章 真的阴间", infoPrefix.getDisplayTitle());
        assertEquals(Integer.valueOf(803), infoPrefix.getNumber());
        assertFalse(infoPrefix.isExtra());
    }
}
