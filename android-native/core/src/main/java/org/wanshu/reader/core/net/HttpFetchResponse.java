package org.wanshu.reader.core.net;

public class HttpFetchResponse {
    private final int statusCode;
    private final String body;
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
        this.statusCode = statusCode;
        this.body = body != null ? body : "";
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
