package org.wanshu.reader.download;

public class PauseReason {
    public static final String NONE = "";
    public static final String USER_PAUSED = "用户已暂停";
    public static final String APP_HIDDEN = "离开应用已暂停";
    public static final String NOT_READING = "未在阅读当前书籍";
    public static final String OFFLINE = "无网络连接";
    public static final String METERED = "仅非计费网络下载";
    public static final String BATTERY_LOW = "电量不足 (≤20%)";
    public static final String POWER_SAVE = "省电模式已开启";
    public static final String STORAGE_LOW = "存储空间已达上限";
    public static final String HIGH_LOAD = "高负载任务执行中";
    public static final String RATE_LIMITED = "源站访问受限冷却中";
}
