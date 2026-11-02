package com.firefly.qstweaks;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 磁贴相关广播统一入口。
 *  - 模块（SystemUI）想执行命令：sendBroadcast(ACTION_RUN, package=PKG)
 *  - 模块的心跳：ACTION_PING
 */
public class TileCommandReceiver extends BroadcastReceiver {

    /** 模块 → 主进程 执行命令 */
    public static final String ACTION_RUN = "com.firefly.qstweaks.RUN";
    /** 模块 → 主进程 心跳 */
    public static final String ACTION_PING = "com.firefly.qstweaks.MODULE_PING";
    /** 通知栏「强制写入」按钮：拉起进程把息屏时间写回去 */
    public static final String ACTION_FIX_SCREEN = "com.firefly.qstweaks.FIX_SCREEN";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        final String action = intent == null ? null : intent.getAction();
        if (ACTION_PING.equals(action)) {
            XposedProbe.markActive(ctx);
            return;
        }
        if (ACTION_FIX_SCREEN.equals(action)) {
            final PendingResult pr = goAsync();
            final Context app = ctx.getApplicationContext();
            new Thread(() -> {
                try {
                    ScreenStep want = Executor.savedScreen(app);
                    if (want != null) Executor.setScreenTimeout(app, want);
                    ScreenGuardService.start(app);
                    ScreenTileService.requestUpdate(app);
                } finally {
                    pr.finish();
                }
            }, "qstweaks-fix-screen").start();
            return;
        }
        final String cmd = intent == null ? null : intent.getStringExtra(Executor.EXTRA_CMD);
        if (cmd == null) return;
        final Intent svc = new Intent(ctx, CommandService.class);
        svc.putExtra(Executor.EXTRA_CMD, cmd);
        try {
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                ctx.startForegroundService(svc);
            } else {
                ctx.startService(svc);
            }
        } catch (Throwable t) {
            // 极少数 ROM 拒绝后台启动服务：在接收器线程里直接执行
            final PendingResult pr = goAsync();
            final Context app = ctx.getApplicationContext();
            new Thread(() -> {
                try {
                    Executor.runLocal(app, cmd);
                } finally {
                    pr.finish();
                }
            }, "qstweaks-cmd").start();
        }

        // 每次来命令顺带把人拉起来：Android 11+ 里前台服务把进程拉起的成本最低，
        // 也让 ColorOS 少一次"这个进程是僵尸"的判断
        ScreenGuardService.start(ctx);
    }
}