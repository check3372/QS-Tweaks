package com.firefly.qstweaks;

/**
 * 息屏时间档位（只有两档：15 秒、5 分钟），点击磁贴在两者之间切换。
 */
public enum ScreenStep {
    S15(15, "15 秒", 15_000),
    M5(300, "5 分钟", 300_000);

    public final int seconds;
    public final String label;
    public final int timeoutMs;

    ScreenStep(int seconds, String label, int timeoutMs) {
        this.seconds = seconds;
        this.label = label;
        this.timeoutMs = timeoutMs;
    }

    /** 两档循环：15 秒 → 5 分钟 → 15 秒 */
    public static ScreenStep next(ScreenStep cur) {
        return cur == S15 ? M5 : S15;
    }

    /** 从毫秒值反推档位：>= 2.5 分钟算 5 分钟档，其余算 15 秒档 */
    public static ScreenStep fromTimeout(int ms) {
        return ms >= 150_000 ? M5 : S15;
    }

    public static ScreenStep current(android.content.ContentResolver cr) {
        int ms = android.provider.Settings.System.getInt(cr,
                android.provider.Settings.System.SCREEN_OFF_TIMEOUT, 30_000);
        return fromTimeout(ms);
    }

    /** 从秒数反推档位（用于读回用户上次的选择） */
    public static ScreenStep fromSeconds(int seconds) {
        return seconds >= 150 ? M5 : S15;
    }
}