package org.wanshu.reader.core.model;

import java.util.Objects;

public class BookKey {
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

    public boolean isOnlineBook() {
        return "36780".equals(id);
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
