package org.wanshu.reader.core.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import org.junit.Test;

public class SourceHttpClientTest {

    @Test
    public void testSuccessfulFetch() throws Exception {
        FakeHttpConnectionFactory factory = new FakeHttpConnectionFactory();
        String url = "https://wanshuge.org/book/36780_13375259.html";
        factory.addResponse(url, 200, "<html><body>Hello World</body></html>".getBytes(StandardCharsets.UTF_8), null);

        SourceHttpClient client = new SourceHttpClient(factory, false);
        HttpFetchResponse resp = client.fetch(url, null);

        assertTrue(resp.isSuccessful());
        assertEquals(200, resp.getStatusCode());
        assertEquals("<html><body>Hello World</body></html>", resp.getBody());
    }

    @Test
    public void testRetryAfterHeader() throws Exception {
        FakeHttpConnectionFactory factory = new FakeHttpConnectionFactory();
        String url = "https://wanshuge.org/book/36780_13375259.html";
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Retry-After", "120");
        factory.addResponse(url, 429, "Too Many Requests".getBytes(StandardCharsets.UTF_8), headers);

        SourceHttpClient client = new SourceHttpClient(factory, false);
        HttpFetchResponse resp = client.fetch(url, null);

        assertTrue(resp.isRateLimited());
        assertEquals(429, resp.getStatusCode());
        assertEquals(Long.valueOf(120), resp.getRetryAfterSeconds());
    }

    @Test
    public void testNotFound404() throws Exception {
        FakeHttpConnectionFactory factory = new FakeHttpConnectionFactory();
        String url = "https://wanshuge.org/book/36780/13375259_5.html";
        factory.addResponse(url, 404, "Page Not Found".getBytes(StandardCharsets.UTF_8), null);

        SourceHttpClient client = new SourceHttpClient(factory, false);
        HttpFetchResponse resp = client.fetch(url, null);

        assertTrue(resp.isNotFound());
        assertEquals(404, resp.getStatusCode());
    }

    @Test
    public void testSafeRedirectFollowed() throws Exception {
        FakeHttpConnectionFactory factory = new FakeHttpConnectionFactory();
        String url1 = "https://wanshuge.org/book/36780/1.html";
        String url2 = "https://wanshuge.org/book/36780/2.html";

        Map<String, String> h1 = new HashMap<String, String>();
        h1.put("Location", url2);
        factory.addResponse(url1, 302, null, h1);
        factory.addResponse(url2, 200, "Redirected Page".getBytes(StandardCharsets.UTF_8), null);

        SourceHttpClient client = new SourceHttpClient(factory, false);
        HttpFetchResponse resp = client.fetch(url1, null);

        assertTrue(resp.isSuccessful());
        assertEquals("Redirected Page", resp.getBody());
        assertEquals(2, factory.getRequestedUrls().size());
    }

    @Test
    public void testExternalRedirectRejected() {
        FakeHttpConnectionFactory factory = new FakeHttpConnectionFactory();
        String url1 = "https://wanshuge.org/book/36780/1.html";
        String evilUrl = "https://evil.com/book/36780/";

        Map<String, String> h1 = new HashMap<String, String>();
        h1.put("Location", evilUrl);
        factory.addResponse(url1, 302, null, h1);

        SourceHttpClient client = new SourceHttpClient(factory, false);
        try {
            client.fetch(url1, null);
            fail("Expected SecurityException on external redirect target");
        } catch (Exception e) {
            // Either SecurityException or IOException wrapping SecurityException
            Throwable cause = e;
            boolean foundSecurity = false;
            while (cause != null) {
                if (cause instanceof SecurityException) {
                    foundSecurity = true;
                    break;
                }
                cause = cause.getCause();
            }
            assertTrue("Expected SecurityException in cause chain", foundSecurity);
        }
    }

    @Test
    public void testExceeds4MiBLimitRejected() {
        FakeHttpConnectionFactory factory = new FakeHttpConnectionFactory();
        String url = "https://wanshuge.org/book/36780_13375259.html";
        // 4.2 MiB data
        byte[] largeData = new byte[(int) (4.2 * 1024 * 1024)];
        factory.addResponse(url, 200, largeData, null);

        SourceHttpClient client = new SourceHttpClient(factory, false);
        try {
            client.fetch(url, null);
            fail("Expected IOException when exceeding 4MiB response limit");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("exceeded maximum limit"));
        }
    }

    @Test
    public void testCancellationAbortsRequest() {
        FakeHttpConnectionFactory factory = new FakeHttpConnectionFactory();
        String url = "https://wanshuge.org/book/36780_13375259.html";
        factory.addResponse(url, 200, "Content".getBytes(StandardCharsets.UTF_8), null);

        CancellationToken token = new CancellationToken();
        token.cancel();

        SourceHttpClient client = new SourceHttpClient(factory, false);
        try {
            client.fetch(url, token);
            fail("Expected CancellationException when token is cancelled");
        } catch (CancellationException e) {
            assertNotNull(e.getMessage());
        } catch (Exception e) {
            fail("Expected CancellationException but got: " + e);
        }
    }

    @Test
    public void testTooManyRedirectsRejected() {
        FakeHttpConnectionFactory factory = new FakeHttpConnectionFactory();
        for (int i = 1; i <= 7; i++) {
            String curr = "https://wanshuge.org/book/36780/" + i + ".html";
            String next = "https://wanshuge.org/book/36780/" + (i + 1) + ".html";
            Map<String, String> h = new HashMap<String, String>();
            h.put("Location", next);
            factory.addResponse(curr, 302, null, h);
        }

        SourceHttpClient client = new SourceHttpClient(factory, false);
        try {
            client.fetch("https://wanshuge.org/book/36780/1.html", null);
            fail("Expected IOException for too many redirects");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("Too many redirects"));
        }
    }

    @Test
    public void testTlsErrorDoesNotFallbackToHttp() {
        FakeHttpConnectionFactory factory = new FakeHttpConnectionFactory();
        String url = "https://wanshuge.org/book/36780/1.html";
        factory.addError(url, new javax.net.ssl.SSLException("Untrusted certificate authority"));

        // allowHttpFallback is true, but TLS failure must NOT fallback to HTTP
        SourceHttpClient client = new SourceHttpClient(factory, true);
        try {
            client.fetch(url, null);
            fail("Expected SSLException, no fallback permitted on TLS errors");
        } catch (javax.net.ssl.SSLException e) {
            assertTrue(e.getMessage().contains("Untrusted certificate"));
        } catch (Exception e) {
            fail("Expected SSLException but got: " + e);
        }

        // Verify only 1 request was attempted (no HTTP fallback)
        assertEquals(1, factory.getRequestedUrls().size());
        assertEquals(url, factory.getRequestedUrls().get(0));
    }

    @Test
    public void testServerError500() throws Exception {
        FakeHttpConnectionFactory factory = new FakeHttpConnectionFactory();
        String url = "https://wanshuge.org/book/36780_13375259.html";
        factory.addResponse(url, 500, "Internal Server Error".getBytes(StandardCharsets.UTF_8), null);

        SourceHttpClient client = new SourceHttpClient(factory, false);
        HttpFetchResponse resp = client.fetch(url, null);

        assertFalse(resp.isSuccessful());
        assertEquals(500, resp.getStatusCode());
        assertEquals("Internal Server Error", resp.getBody());
    }
}
