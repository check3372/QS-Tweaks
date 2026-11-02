package com.firefly.qstweaks;

/**
 * 首选网络类型目标。
 *
 * 5G 开关其实就是改「允许的网络类型」位掩码：
 *   ON  = NR | LTE | UMTS | GSM
 *   OFF = LTE | UMTS | GSM
 */
public enum LteTarget {
    G5_ON("on", "5G 开启"),
    G5_OFF("off", "5G 关闭");

    public final String key;
    public final String label;

    LteTarget(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public static LteTarget fromKey(String k) {
        for (LteTarget t : values()) if (t.key.equals(k)) return t;
        return G5_ON;
    }
}