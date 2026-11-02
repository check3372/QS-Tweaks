package com.firefly.qstweaks;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 执行器。所有"动作"集中在这里，App 磁贴、模块注入的磁贴、设置页都调它。
 *
 * 5G：首选 TelephonyManager#setAllowedNetworkTypesForReason（需 MODIFY_PHONE_STATE），
 *     失败 → 老 API → su shell。
 * 息屏：Settings.System.putInt（需 WRITE_SECURE_SETTINGS），失败 → su shell。
 */
public final class Executor {

    public static final String TAG = "QSTweaks";
    public static final String EXTRA_CMD = "cmd";

    private Executor() {}

    // ===================================================================
    //  入口（请在后台线程调用）
    // ===================================================================

    public static void runLocal(Context ctx, String cmd) {
        Log.d(TAG, "run: " + cmd);
        long t0 = System.currentTimeMillis();
        boolean ok = false;
        try {
            if (cmd.startsWith(Cmd.PREFIX_5G)) {
                ok = set5g(ctx, LteTarget.fromKey(cmd.substring(Cmd.PREFIX_5G.length())));
            } else if (cmd.startsWith(Cmd.PREFIX_SCREEN)) {
                ok = setScreenTimeout(ctx, ScreenStep.fromSeconds(
                        Integer.parseInt(cmd.substring(Cmd.PREFIX_SCREEN.length()))));
            }
        } catch (Throwable t) {
            Log.w(TAG, "run failed", t);
        }
        Log.d(TAG, "run done ok=" + ok + " in " + (System.currentTimeMillis() - t0) + "ms");
    }

    // ===================================================================
    //  息屏
    // ===================================================================

    static final String KEY_SCREEN = "screen_step_seconds";
    static final String KEY_5G_MODE = "5g_mode";

    /**
     * 写入息屏时间，记下用户的选择，并在写完之后校验一次“真的写进去了”。
     *
     * @param allowShell true = 允许走 su 兜底和打详细日志（磁贴点击 / App 操作时用）
     *                   false = 只在 Java 层写（守护服务高频调用时用，避免刷 su）
     */
    @SuppressLint("WrongConstant")
    static boolean setScreenTimeout(Context ctx, ScreenStep step) {
        return setScreenTimeout(ctx, step, true);
    }

    @SuppressLint("WrongConstant")
    static boolean setScreenTimeout(Context ctx, ScreenStep step, boolean allowShell) {
        final int ms = step.timeoutMs;

        // 1) 先试 Java 写
        Boolean javaOk = javaWrite(ctx, ms);

        // 2) 读回判断。日志证明 ColorOS 上 putInt 返回 true 但值是 300000，
        //    所以"写成功"必须靠读回，而且一旦读回不对就必须走 su 再写一次
        int now = readTimeout(ctx);
        boolean effective = Math.abs(now - ms) <= 2000;

        Boolean suOk = null;
        if (allowShell && !effective) {
            suOk = suWrite(ctx, ms);
            now = readTimeout(ctx);
            effective = Math.abs(now - ms) <= 2000;
        }

        if (allowShell) {
            Log.d(TAG, "screen " + step.label + " java=" + javaOk + " su=" + suOk
                    + " readback=" + now + " effective=" + effective);
        }
        if (effective) {
            prefs(ctx).edit().putInt(KEY_SCREEN, step.seconds).apply();
        } else if (allowShell) {
            Log.w(TAG, "息屏写入未生效 (readback=" + now + ")");
        }
        return effective;
    }

    private static Integer readTimeout(Context ctx) {
        try {
            return Settings.System.getInt(ctx.getContentResolver(),
                    Settings.System.SCREEN_OFF_TIMEOUT, -1);
        } catch (Throwable t) {
            return -1;
        }
    }

    /** Java 层写；返回 null 表示抛异常，false 表示返回了 false */
    private static Boolean javaWrite(Context ctx, int ms) {
        try {
            boolean ok = Settings.System.putInt(ctx.getContentResolver(),
                    Settings.System.SCREEN_OFF_TIMEOUT, ms);
            if (!ok) Log.w(TAG, "putInt=false");
            return ok;
        } catch (Throwable t) {
            Log.w(TAG, "putInt threw: " + t.getClass().getSimpleName() + " " + t.getMessage());
            return null;
        }
    }

    /**
     * root 写。
     * 注意解法：su 之前是把整串塞进 `su -c sh -c "..."`，某些 su（KernelSU 的部分版本）
     * 会把它当成一个参数直接 exec，导致命令根本没跑但退出码是 0。
     * 这里改成 `su -c` + 单条 argv，并把 su 的 stdout/stderr 全部记下来。
     */
    private static Boolean suWrite(Context ctx, int ms) {
        String cmd = "settings put system screen_off_timeout " + ms;
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            String out = readAll(p.getInputStream());
            String err = readAll(p.getErrorStream());
            int code = p.waitFor();
            Log.d(TAG, "su [" + cmd + "] exit=" + code
                    + (out.trim().isEmpty() ? "" : " out=" + out.trim())
                    + (err.trim().isEmpty() ? "" : " err=" + err.trim()));
            return code == 0;
        } catch (Throwable t) {
            Log.w(TAG, "su exec failed: " + t);
            return null;
        }
    }

    /** 用户上次选的档位；没选过返回 null */
    static ScreenStep savedScreen(Context ctx) {
        int s = prefs(ctx).getInt(KEY_SCREEN, -1);
        return s < 0 ? null : ScreenStep.fromSeconds(s);
    }

    // ===================================================================
    //  5G
    // ===================================================================

    private static boolean set5g(Context ctx, LteTarget target) {
        int subId = SubscriptionManager.getDefaultDataSubscriptionId();
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            int[] ids = activeSubIds(ctx);
            if (ids.length > 0) subId = ids[0];
        }

        boolean ok = tryTelephony(ctx, target, subId);
        if (!ok) {
            final int mode = (target == LteTarget.G5_ON) ? MODE_NR_ON : MODE_NR_OFF;
            // 走 root 持久化：as 命令、settings 命令都写进同一个脚本跑一次
            final StringBuilder sb = new StringBuilder();
            if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                sb.append("cmd phone set-preferred-network-type ").append(subId).append(' ')
                        .append(mode).append("; ");
            }
            for (int s : activeSubIds(ctx)) {
                int slot = SubscriptionManager.getSlotIndex(s);
                if (slot >= 0) {
                    sb.append("settings put global preferred_network_mode").append(slot)
                            .append(' ').append(mode).append("; ");
                }
            }
            sb.append("settings put global preferred_network_mode ").append(mode).append("; ");
            sb.append("setprop persist.radio.networkmode ").append(mode).append("; ");
            sb.append("setprop persist.vendor.radio.network_mode ").append(mode);
            ok = sh(sb.toString());
            // 记下目标模式，重启后由 ScreenGuard/BootReceiver 方案统一重放（见 README）
            prefs(ctx).edit().putInt(KEY_5G_MODE, mode).apply();
        }

        // 真实结果以读回为准（su / telephony 都可能被运营商拒绝）
        Boolean real = read5g(ctx);
        if (real == null) {
            // 读不到（没有 READ_PHONE_STATE 之类）就信命令结果
            real = target == LteTarget.G5_ON;
        } else if (real != (target == LteTarget.G5_ON)) {
            Log.w(TAG, "5g target=" + target + " but actual=" + real + " (被运营商/基带拒绝？)");
        }
        mark5g(ctx, real);
        return real == (target == LteTarget.G5_ON);
    }

    private static boolean tryTelephony(Context ctx, LteTarget target, int subId) {
        try {
            TelephonyManager tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm == null) return false;
            if (subId > 0) tm = tm.createForSubscriptionId(subId);

            long base = TelephonyManager.NETWORK_TYPE_BITMASK_LTE
                    | TelephonyManager.NETWORK_TYPE_BITMASK_UMTS
                    | TelephonyManager.NETWORK_TYPE_BITMASK_GSM;
            long nr = 0L;
            try {
                nr = (long) TelephonyManager.class.getField("NETWORK_TYPE_BITMASK_NR").get(null);
            } catch (Throwable ignored) {}
            if (target == LteTarget.G5_ON && nr == 0L) return false;
            final long mask = target == LteTarget.G5_ON ? (base | nr) : base;

            // API 31+
            try {
                java.lang.reflect.Method m = TelephonyManager.class
                        .getMethod("setAllowedNetworkTypesForReason", int.class, long.class);
                int reason = (int) TelephonyManager.class
                        .getField("ALLOWED_NETWORK_TYPES_REASON_USER").get(null);
                m.invoke(tm, reason, mask);
                return true;
            } catch (Throwable t) {
                Log.d(TAG, "setAllowedNetworkTypesForReason failed", t);
            }
            // API 29~30
            try {
                java.lang.reflect.Method m = TelephonyManager.class
                        .getMethod("setPreferredNetworkTypeBitmask", long.class);
                m.invoke(tm, mask);
                return true;
            } catch (Throwable t) {
                Log.d(TAG, "setPreferredNetworkTypeBitmask failed", t);
            }
            // 老 API
            try {
                java.lang.reflect.Method m = TelephonyManager.class
                        .getMethod("setPreferredNetworkType", int.class);
                m.invoke(tm, target == LteTarget.G5_ON ? MODE_NR_ON : MODE_NR_OFF);
                return true;
            } catch (Throwable t) {
                Log.d(TAG, "setPreferredNetworkType failed", t);
            }
        } catch (Throwable t) {
            Log.d(TAG, "telephony path failed", t);
        }
        return false;
    }

    /** NETWORK_MODE_* 数值（TelephonyManager.NETWORK_MODE_*）
     *  33 = NETWORK_MODE_NR_LTE_WCDMA_GSM （部分 ROM 上 NR 多模是 15，失败会回退 settings）
     *  9  = NETWORK_MODE_LTE_WCDMA_GSM */
    static final int MODE_NR_ON  = 33;
    static final int MODE_NR_OFF = 9;

    /**
     * 当前有效的订阅 ID 列表。
     * SubscriptionManager#getActiveSubscriptionIdList() 是 @hide，公开 API 里没有，
     * 所以走 SubscriptionManager#getActiveSubscriptionInfoList()（需要 READ_PHONE_STATE）。
     */
    static int[] activeSubIds(Context ctx) {
        try {
            SubscriptionManager sm = (SubscriptionManager) ctx.getSystemService(
                    Context.TELEPHONY_SUBSCRIPTION_SERVICE);
            if (sm == null) return new int[0];
            java.util.List<android.telephony.SubscriptionInfo> list = sm.getActiveSubscriptionInfoList();
            if (list == null) return new int[0];
            int[] out = new int[list.size()];
            for (int i = 0; i < out.length; i++) out[i] = list.get(i).getSubscriptionId();
            return out;
        } catch (Throwable t) {
            Log.w(TAG, "activeSubIds failed", t);
            return new int[0];
        }
    }

    /** 允许 NR 时返回 true；读不到时返回上次成功写入的值 */
    public static boolean is5gOn(Context ctx) {
        if (ctx == null) return true;
        Boolean r = read5g(ctx);
        if (r != null) return r;
        return prefs(ctx).getBoolean(Cmd.KEY_5G_ON, true);
    }

    /** 直接读：null = 读不到 */
    private static Boolean read5g(Context ctx) {
        try {
            TelephonyManager tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm == null) return null;
            long m = tm.getAllowedNetworkTypesForReason(
                    TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER);
            long nr = (long) TelephonyManager.class.getField("NETWORK_TYPE_BITMASK_NR").get(null);
            return (m & nr) != 0;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void mark5g(Context ctx, boolean on) {
        prefs(ctx).edit().putBoolean(Cmd.KEY_5G_ON, on).apply();
    }

    static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(Cmd.PREFS, Context.MODE_PRIVATE);
    }

    // ===================================================================
    //  root shell
    // ===================================================================

    /**
     * 执行 su 命令。
     *
     * 兼容性说明（重要）：
     *  - Magisk 的 su 支持 `su -c "cmd"`
     *  - KernelSU 的 su 对 `-c` 后跟整串的处理不同：它会把后续参数直接 exec，
     *    不会过一遍 shell，所以带管道/引号的命令会失败，甚至 `id` 都返回非 0。
     *    正确写法是 `su -c sh -c "cmd"`，让 sh 再来解释一次。
     *  - 所以这里统一用 `su -c sh -c "<cmd>"`，两种方案都能跑。
     */
    static boolean sh(String cmd) {
        return shImpl(cmd, true);
    }

    /** 执行 su 命令；logOutput=false 时不打日志（守护服务高频调用用） */
    static boolean shQuiet(String cmd) {
        return shImpl(cmd, false);
    }

    private static boolean shImpl(String cmd, boolean logOutput) {
        // 先把命令里的双引号转义，避免二次解析被截断
        final String escaped = cmd.replace("\\", "\\\\").replace("\"", "\\\"");
        final String[] argv = {"su", "-c", "sh -c \"" + escaped + "\""};
        try {
            Process p = Runtime.getRuntime().exec(argv);
            // 必须把 stdout/stderr 读干净，否则进程可能阻塞在写管道上
            drain(p.getInputStream(), logOutput);
            drain(p.getErrorStream(), logOutput);
            boolean ok = p.waitFor() == 0;
            if (logOutput) Log.d(TAG, "sh [" + cmd + "] => " + ok);
            return ok;
        } catch (Throwable e) {
            Log.w(TAG, "sh failed: " + cmd, e);
            return false;
        }
    }

    private static void drain(java.io.InputStream in, boolean logOutput) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (logOutput) Log.v(TAG, "  > " + line);
            }
        } catch (Throwable ignored) {}
    }

    /**
     * 检测是否有可用的 root（Magisk / KernelSU / APatch 都覆盖）。
     */
    public static boolean hasRoot() {
        String out = shCapture("id");
        return out != null && out.contains("uid=0");
    }

    /** su 是否存在于常见位置（仅用于诊断显示） */
    public static boolean suBinaryFound() {
        for (String p : new String[]{"/system/bin/su", "/system/xbin/su", "/sbin/su",
                "/debug_ramdisk/su", "/data/adb/ksu/bin/su", "/data/adb/ksud"}) {
            if (new java.io.File(p).exists()) return true;
        }
        return false;
    }

    /** 供守护服务高频调用：只 su 写，不打日志、不读回 */
    static void suWriteQuiet(int ms) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{
                    "su", "-c", "settings put system screen_off_timeout " + ms});
            String out = readAll(p.getInputStream());
            String err = readAll(p.getErrorStream());
            int code = p.waitFor();
            if (code != 0 || !err.trim().isEmpty()) {
                Log.d(TAG, "suWriteQuiet exit=" + code + " err=" + err.trim());
            }
            if (out.trim().length() > 0) Log.v(TAG, "suWriteQuiet out=" + out.trim());
        } catch (Throwable t) {
            Log.w(TAG, "suWriteQuiet failed", t);
        }
    }

    /** 执行并返回 stdout（失败返回 null） */
    static String shCapture(String cmd) {
        return shCapture(cmd, true);
    }

    static String shCapture(String cmd, boolean log) {
        final String escaped = cmd.replace("\\", "\\\\").replace("\"", "\\\"");
        try {
            Process p = Runtime.getRuntime().exec(
                    new String[]{"su", "-c", "sh -c \"" + escaped + "\""});
            String out = readAll(p.getInputStream());
            drain(p.getErrorStream(), log);
            int code = p.waitFor();
            if (log) Log.d(TAG, "shCapture [" + cmd + "] exit=" + code + " => " + out.trim());
            return out;
        } catch (Throwable e) {
            Log.w(TAG, "shCapture failed: " + cmd, e);
            return null;
        }
    }

    private static String readAll(java.io.InputStream in) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
        } catch (Throwable ignored) {}
        return sb.toString();
    }

    /** 执行任意 shell 片段（su），返回 exit code == 0 */
    static boolean exec(String script) {
        return sh(script);
    }

    /** pm grant，单条命令（不套 sh -c，KernelSU 下最稳） */
    static boolean pmGrant(String pkg, String permission) {
        return shSimple("pm grant " + pkg + " " + permission);
    }

    /** appops set，单条命令 */
    static boolean appopsSet(String pkg, String op, String mode) {
        return shSimple("appops set " + pkg + " " + op + " " + mode);
    }

    /** 只有空格没特殊字符的简单命令：直接 su -c <整串> */
    private static boolean shSimple(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            String out = readAll(p.getInputStream());
            String err = readAll(p.getErrorStream());
            boolean ok = p.waitFor() == 0;
            Log.d(TAG, "su -c [" + cmd + "] => " + ok
                    + (out.isEmpty() ? "" : " | out: " + out.trim())
                    + (err.isEmpty() ? "" : " | err: " + err.trim()));
            return ok;
        } catch (Throwable e) {
            Log.w(TAG, "su -c failed: " + cmd, e);
            return false;
        }
    }
}