package org.wanshu.reader.navigation;

import java.util.Objects;

public class BookRoute {
    private final String routeType;
    private final String bookId;
    private final String chapterId;
    private final long generation;
    private final int targetParagraph;
    private final int targetOffset;

    public BookRoute(
            String routeType,
            String bookId,
            String chapterId,
            long generation,
            int targetParagraph,
            int targetOffset
    ) {
        this.routeType = routeType != null ? routeType : RouteType.SHELF;
        this.bookId = bookId != null ? bookId : "";
        this.chapterId = chapterId != null ? chapterId : "";
        this.generation = generation;
        this.targetParagraph = targetParagraph;
        this.targetOffset = targetOffset;
    }

    public static BookRoute shelf(long generation) {
        return new BookRoute(RouteType.SHELF, "", "", generation, -1, 0);
    }

    public static BookRoute reader(String bookId, String chapterId, long generation) {
        return new BookRoute(RouteType.READER, bookId, chapterId, generation, -1, 0);
    }

    public static BookRoute readerWithAnchor(
            String bookId,
            String chapterId,
            int paragraphIndex,
            int offsetUtf16,
            long generation
    ) {
        return new BookRoute(RouteType.READER, bookId, chapterId, generation, paragraphIndex, offsetUtf16);
    }

    public boolean isShelf() {
        return RouteType.SHELF.equals(routeType);
    }

    public boolean isReader() {
        return RouteType.READER.equals(routeType);
    }

    public String getRouteType() {
        return routeType;
    }

    public String getBookId() {
        return bookId;
    }

    public String getChapterId() {
        return chapterId;
    }

    public long getGeneration() {
        return generation;
    }

    public int getTargetParagraph() {
        return targetParagraph;
    }

    public int getTargetOffset() {
        return targetOffset;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BookRoute bookRoute = (BookRoute) o;
        return generation == bookRoute.generation &&
                targetParagraph == bookRoute.targetParagraph &&
                targetOffset == bookRoute.targetOffset &&
                Objects.equals(routeType, bookRoute.routeType) &&
                Objects.equals(bookId, bookRoute.bookId) &&
                Objects.equals(chapterId, bookRoute.chapterId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(routeType, bookId, chapterId, generation, targetParagraph, targetOffset);
    }

    @Override
    public String toString() {
        return "BookRoute{" +
                "routeType='" + routeType + '\'' +
                ", bookId='" + bookId + '\'' +
                ", chapterId='" + chapterId + '\'' +
                ", generation=" + generation +
                ", targetParagraph=" + targetParagraph +
                ", targetOffset=" + targetOffset +
                '}';
    }
}
