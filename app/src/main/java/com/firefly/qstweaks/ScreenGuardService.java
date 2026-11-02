package com.firefly.qstweaks;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

/**
 * 「息屏守护」后台服务 —— 不显示任何通知。
 *
 * 工作方式：每 2 秒核对一次 screen_off_timeout，发现被 ColorOS 改回去就重写。
 * 因为不是前台服务，进程被系统清理后它就停了；要让它在划掉后台后仍然工作，
 * 需要开启「无障碍保活」（GuardAccessibilityService 会把进程留住）。
 */
public class ScreenGuardService extends Service {

    private static final long INTERVAL_MS = 2000L;

    /** 进程被重建时 onStartCommand 可能没来得及跑，先判断一下 */
    private static volatile boolean alive = false;

    static boolean isAlive() { return alive; }

    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean running = true;
    private final java.util.concurrent.atomic.AtomicBoolean busy =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    public static void start(android.content.Context ctx) {
        if (!isEnabled(ctx)) return;
        try {
            ctx.startService(new Intent(ctx, ScreenGuardService.class));
        } catch (Throwable t) {
            Log.w(Executor.TAG, "guard start failed", t);
        }
    }

    public static void stop(android.content.Context ctx) {
        try {
            ctx.stopService(new Intent(ctx, ScreenGuardService.class));
        } catch (Throwable ignored) {}
    }

    static boolean isEnabled(android.content.Context ctx) {
        return Executor.prefs(ctx).getBoolean("screen_guard", true);
    }

    static void setEnabled(android.content.Context ctx, boolean on) {
        Executor.prefs(ctx).edit().putBoolean("screen_guard", on).apply();
        if (on) start(ctx); else stop(ctx);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!isEnabled(this)) { stopSelf(); return START_NOT_STICKY; }
        alive = true;
        main.post(tick);
        return START_STICKY;
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            if (busy.compareAndSet(false, true)) {
                new Thread(() -> {
                    try {
                        ScreenStep want = Executor.savedScreen(ScreenGuardService.this);
                        if (want != null) {
                            int cur = Settings.System.getInt(getContentResolver(),
                                    Settings.System.SCREEN_OFF_TIMEOUT, -1);
                            if (Math.abs(cur - want.timeoutMs) > 2000) {
                                // putInt 在 ColorOS 上会"假成功"，所以先 su 静默写并读回
                                Executor.suWriteQuiet(want.timeoutMs);
                                int now = Settings.System.getInt(getContentResolver(),
                                        Settings.System.SCREEN_OFF_TIMEOUT, -1);
                                if (Math.abs(now - want.timeoutMs) > 2000) {
                                    // 最后再试一次 Java 写（不打日志，避免 2 秒刷屏）
                                    Executor.setScreenTimeout(
                                            getApplicationContext(), want, false);
                                }
                            }
                        }
                    } catch (Throwable ignored) {
                    } finally {
                        busy.set(false);
                    }
                }, "qstweaks-guard").start();
            }
            if (running) main.postDelayed(this, INTERVAL_MS);
        }
    };

    @Override
    public void onDestroy() {
        running = false;
        alive = false;
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}