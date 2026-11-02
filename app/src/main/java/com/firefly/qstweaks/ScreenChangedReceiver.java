package com.firefly.qstweaks;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;

/**
 * 亮屏 / 解锁时补一次息屏时间，并重启守护服务。
 *
 * 后台进程被杀后 ColorOS 会把 SCREEN_OFF_TIMEOUT 改回默认值，
 * 所以这里只做一件事：当前值 != 用户上次选的档位 → 写回去。
 */
public class ScreenChangedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            ScreenStep want = Executor.savedScreen(app);
            if (want != null) {
                int cur = Settings.System.getInt(app.getContentResolver(),
                        Settings.System.SCREEN_OFF_TIMEOUT, -1);
                Log.d(Executor.TAG, "screen-on: cur=" + cur + " want=" + want.timeoutMs);
                if (cur != want.timeoutMs) {
                    Executor.setScreenTimeout(app, want);
                }
            }
            ScreenGuardService.start(app);
        }, "qstweaks-screen-on").start();
    }
}