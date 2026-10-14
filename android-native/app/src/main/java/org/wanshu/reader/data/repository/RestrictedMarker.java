package org.wanshu.reader.data.repository;

/**
 * Remembers that the source only published part of a chapter (member-only) without a schema change:
 * the chapter row's missing_pages_json column is not read anywhere else, so a cached chapter carries
 * this marker there instead of "[]".
 */
public final class RestrictedMarker {
    public static final String EMPTY = "[]";
    public static final String RESTRICTED = "{\"restricted\":true}";

    private RestrictedMarker() {}

    public static String encode(boolean restricted) {
        return restricted ? RESTRICTED : EMPTY;
    }

    public static boolean isRestricted(String json) {
        return json != null && json.contains("\"restricted\":true");
    }
}
