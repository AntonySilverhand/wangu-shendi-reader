package org.wanshu.reader.core.net;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class UrlSafetyValidatorTest {

    @Test
    public void testValidUrlsPass() {
        String[] valid = new String[] {
                "https://wanshuge.org/book/36780/",
                "https://wanshuge.org/book/36780",
                "https://www.wanshuge.org/book/36780/",
                "https://wanshuge.org/book/36780/1.html",
                "https://wanshuge.org/book/36780/43.html",
                "https://wanshuge.org/book/36780/80.html",
                "https://wanshuge.org/book/36780_13375259.html",
                "https://wanshuge.org/book/36780/13375259_1.html",
                "https://wanshuge.org/book/36780/13375259_39.html",
                "http://wanshuge.org/book/36780/"
        };

        for (int i = 0; i < valid.length; i++) {
            try {
                UrlSafetyValidator.validateUrl(valid[i]);
            } catch (Exception e) {
                fail("Expected valid URL to pass: " + valid[i] + ", error: " + e.getMessage());
            }
        }
    }

    @Test
    public void testExternalDomainsRejected() {
        assertRejected("https://evil.com/book/36780/");
        assertRejected("https://wanshuge.org.attacker.com/book/36780/");
        assertRejected("https://fake-wanshuge.org/book/36780/");
        assertRejected("https://baidu.com/book/36780/");
    }

    @Test
    public void testPathTraversalAndEncodingRejected() {
        assertRejected("https://wanshuge.org/book/36780/../../etc/passwd");
        assertRejected("https://wanshuge.org/book/36780/%2e%2e/test");
        assertRejected("https://wanshuge.org/book/36780//1.html");
    }

    @Test
    public void testIllegalPortsAndUserInfoRejected() {
        assertRejected("https://wanshuge.org:8080/book/36780/");
        assertRejected("https://wanshuge.org:22/book/36780/");
        assertRejected("https://user:pass@wanshuge.org/book/36780/");
    }

    @Test
    public void testNonBookPathsAndExcessivePagesRejected() {
        assertRejected("https://wanshuge.org/admin/dashboard");
        assertRejected("https://wanshuge.org/book/99999/1.html");
        assertRejected("https://wanshuge.org/book/36780/13375259_40.html"); // Max 39
        assertRejected("https://wanshuge.org/book/36780_1234567890123.html"); // >12 digits
        assertRejected("ftp://wanshuge.org/book/36780/");
    }

    private void assertRejected(String url) {
        try {
            UrlSafetyValidator.validateUrl(url);
            fail("Expected URL to be rejected: " + url);
        } catch (SecurityException e) {
            assertNotNull(e.getMessage());
        }
    }
}
