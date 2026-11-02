package com.firefly.qstweaks;

/**
 * 命令协议常量。
 *
 * 模块(主进程/SystemUI) ↔ 执行器 之间用 "前缀:参数" 形式的短命令。
 * 例：5g:on / 5g:off / screen:15（秒，仅 15 / 300 两档）
 */
public final class Cmd {

    /** SharedPreferences 文件名（存 5G 估算状态 / 模块心跳） */
    public static final String PREFS = "qs_tweaks";

    /** 5G 当前状态（true=允许 NR），Executor 每次执行后写 */
    public static final String KEY_5G_ON = "5g_on";

    /** 兼容旧版：模块 → 主进程的遗留请求键（当前未使用） */
    @Deprecated
    public static final String KEY_REQUEST = "request";

    private Cmd() {}

    public static final String PREFIX_5G = "5g:";
    public static final String PREFIX_SCREEN = "screen:";

    public static String g5(LteTarget t) { return PREFIX_5G + t.key; }
    public static String screen(int seconds) { return PREFIX_SCREEN + seconds; }
}