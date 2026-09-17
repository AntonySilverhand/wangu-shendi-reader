package org.wanshu.reader.download;

public class GateResult {
    public final boolean allowed;
    public final String reason;

    public GateResult(boolean allowed, String reason) {
        this.allowed = allowed;
        this.reason = reason != null ? reason : "";
    }
}
