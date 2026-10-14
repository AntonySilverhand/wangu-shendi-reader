package org.wanshu.reader.core.net;

import static org.junit.Assert.assertFalse;
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
        assertRejected("https://wanshuge.org/book/0/1.html");
        assertRejected("https://wanshuge.org/book/012345/1.html"); // leading zero
        assertRejected("https://wanshuge.org/book/1234567890/1.html"); // >9 digits
        assertRejected("https://wanshuge.org/book/36780/1000.html"); // TOC sub pages stop at 999
        assertRejected("https://wanshuge.org/book/36780/abc.html");
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

    @Test
    public void testOtherBookIdsAllowed() {
        String[] valid = new String[] {
                "https://wanshuge.org/book/104429/",
                "https://wanshuge.org/book/104429/16.html",
                "https://wanshuge.org/book/230580/999.html",
                "https://wanshuge.org/book/230580_72836267.html",
                "https://wanshuge.org/book/230580/72836267_2.html",
                "https://www.wanshuge.org/book/1/"
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
    public void testSearchEndpointIsPostOnly() {
        UrlSafetyValidator.validateUrl("https://wanshuge.org/search.html", "POST");
        assertTrue(UrlSafetyValidator.isAllowedRequest("POST", "/search.html"));
        assertFalse(UrlSafetyValidator.isAllowedRequest("GET", "/search.html"));
        assertFalse(UrlSafetyValidator.isAllowedRequest("POST", "/book/36780/"));
        assertFalse(UrlSafetyValidator.isAllowedRequest("PUT", "/search.html"));
        assertRejected("https://wanshuge.org/search.html"); // default is GET
        assertRejectedFor("https://wanshuge.org/search.html", "GET");
        assertRejectedFor("https://wanshuge.org/book/36780/", "POST");
        assertRejectedFor("https://wanshuge.org/search.html/../x", "POST");
        assertRejectedFor("https://evil.com/search.html", "POST");
        assertRejectedFor("https://wanshuge.org/search.htm", "POST");
    }

    @Test
    public void testCoverImagesAreGetImageOnly() {
        UrlSafetyValidator.validateImageUrl("https://wanshuge.org/img/104429.jpg");
        UrlSafetyValidator.validateImageUrl("https://www.wanshuge.org/img/36780.jpg");
        assertTrue(UrlSafetyValidator.isAllowedRequest("GET", "/img/36780.jpg"));
        assertFalse(UrlSafetyValidator.isAllowedRequest("POST", "/img/36780.jpg"));
        // not reachable through the text fetch validation
        assertRejected("https://wanshuge.org/img/36780.jpg");
        String[] badImages = new String[] {
                "https://wanshuge.org/img/0.jpg",
                "https://wanshuge.org/img/abc.jpg",
                "https://wanshuge.org/img/36780.png",
                "https://wanshuge.org/img/../36780.jpg",
                "https://wanshuge.org/img/a/36780.jpg",
                "https://evil.com/img/36780.jpg",
                "https://wanshuge.org/book/36780/",
                "https://wanshuge.org/static/logo.png"
        };
        for (int i = 0; i < badImages.length; i++) {
            try {
                UrlSafetyValidator.validateImageUrl(badImages[i]);
                fail("Expected image URL to be rejected: " + badImages[i]);
            } catch (SecurityException expected) {}
        }
    }

    @Test
    public void testArbitraryPathsStillRejected() {
        assertRejected("https://wanshuge.org/");
        assertRejected("https://wanshuge.org/login.html");
        assertRejected("https://wanshuge.org/user/login");
        assertRejected("https://wanshuge.org/book/");
        assertRejected("https://wanshuge.org/book/36780/x/1.html");
    }

    private void assertRejectedFor(String url, String method) {
        try {
            UrlSafetyValidator.validateUrl(url, method);
            fail("Expected " + method + " " + url + " to be rejected");
        } catch (SecurityException e) {
            assertNotNull(e.getMessage());
        }
    }
}
