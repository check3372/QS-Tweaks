package com.firefly.qstweaks;

import android.content.Context;

/**
 * 记录模块心跳（"LSPosed 模块：已激活"）。
 * 由 TileCommandReceiver / PingReceiver 接收模块广播后调用。
 */
public final class XposedProbe {

    public static final String KEY_PING = "module_ping";
    private static final long WINDOW_MS = 10L * 60_000L;

    private XposedProbe() {}

    public static boolean isActive(Context ctx) {
        long t = ctx.getSharedPreferences(Cmd.PREFS, Context.MODE_PRIVATE).getLong(KEY_PING, 0L);
        return System.currentTimeMillis() - t < WINDOW_MS;
    }

    public static void markActive(Context ctx) {
        ctx.getSharedPreferences(Cmd.PREFS, Context.MODE_PRIVATE)
                .edit().putLong(KEY_PING, System.currentTimeMillis()).apply();
    }
}