package org.wanshu.reader.ui.cover;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CoverDecoderTest {

    @Test
    public void smallImageIsNotSubsampled() {
        assertEquals(1, CoverDecoder.sampleSize(120, 160, 192, 256));
    }

    @Test
    public void largeImageIsSubsampledButStaysAtLeastTargetSize() {
        // 800x1066 shown at 192x256: 800/2=400>=192 and 1066/2>=256 -> 2; 800/4=200>=192 and 1066/4=266>=256 -> 4; 800/8=100 < 192 stop
        assertEquals(4, CoverDecoder.sampleSize(800, 1066, 192, 256));
    }

    @Test
    public void bothDimensionsMustStayLargeEnough() {
        // width would allow 8, but the height only allows 2
        assertEquals(2, CoverDecoder.sampleSize(2000, 600, 100, 250));
    }

    @Test
    public void invalidSizesFallBackToOne() {
        assertEquals(1, CoverDecoder.sampleSize(0, 100, 10, 10));
        assertEquals(1, CoverDecoder.sampleSize(100, 100, 0, 10));
    }
}
