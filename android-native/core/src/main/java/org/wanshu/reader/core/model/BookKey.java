package org.wanshu.reader.core.model;

import java.util.Objects;
import java.util.regex.Pattern;

public class BookKey {
    /** Online book ids are the source's numeric ids; local TXT books use a non-numeric prefix. */
    private static final Pattern ONLINE_ID_PATTERN = Pattern.compile("^[1-9]\\d{0,8}$");

    private final String id;

    public BookKey(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Book ID must not be empty");
        }
        this.id = id.trim();
    }

    public String getId() {
        return id;
    }

    public static boolean isOnlineId(String id) {
        return id != null && ONLINE_ID_PATTERN.matcher(id).matches();
    }

    public boolean isOnlineBook() {
        return isOnlineId(id);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BookKey bookKey = (BookKey) o;
        return Objects.equals(id, bookKey.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return id;
    }
}
