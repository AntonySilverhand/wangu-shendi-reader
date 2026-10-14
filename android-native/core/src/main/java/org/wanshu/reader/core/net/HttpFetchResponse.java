package org.wanshu.reader.core.net;

public class HttpFetchResponse {
    private static final byte[] NO_BYTES = new byte[0];

    private final int statusCode;
    private final String body;
    private final byte[] bodyBytes;
    private final Long retryAfterSeconds;
    private final String contentType;
    private final String finalUrl;

    public HttpFetchResponse(
            int statusCode,
            String body,
            Long retryAfterSeconds,
            String contentType,
            String finalUrl
    ) {
        this(statusCode, body, NO_BYTES, retryAfterSeconds, contentType, finalUrl);
    }

    public HttpFetchResponse(
            int statusCode,
            String body,
            byte[] bodyBytes,
            Long retryAfterSeconds,
            String contentType,
            String finalUrl
    ) {
        this.statusCode = statusCode;
        this.body = body != null ? body : "";
        this.bodyBytes = bodyBytes != null ? bodyBytes : NO_BYTES;
        this.retryAfterSeconds = retryAfterSeconds;
        this.contentType = contentType;
        this.finalUrl = finalUrl;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getBody() {
        return body;
    }

    /** Raw body; only filled for binary fetches (images), empty for text responses. */
    public byte[] getBodyBytes() {
        return bodyBytes;
    }

    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public String getContentType() {
        return contentType;
    }

    public String getFinalUrl() {
        return finalUrl;
    }

    public boolean isSuccessful() {
        return statusCode >= 200 && statusCode < 300;
    }

    public boolean isNotFound() {
        return statusCode == 404;
    }

    public boolean isRateLimited() {
        return statusCode == 429;
    }
}
