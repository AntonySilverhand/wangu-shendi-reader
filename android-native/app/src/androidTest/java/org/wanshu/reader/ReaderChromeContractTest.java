package org.wanshu.reader;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ReaderChromeContractTest {
    @Test public void homeRemainsReachableAcrossAspectRatiosAndFontScales() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new ReaderChromeContractRunnable(
                InstrumentationRegistry.getInstrumentation().getTargetContext()));
    }
}
