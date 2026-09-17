package org.wanshu.reader.core.queue;

public enum RequestPriority {
    BACKGROUND(1),
    LOW(2),
    NORMAL(3),
    HIGH(4);

    private final int level;

    RequestPriority(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }

    public boolean isHighPriority() {
        return this == HIGH;
    }
}
