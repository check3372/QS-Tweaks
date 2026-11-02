package com.firefly.qstweaks;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 外部触发命令的入口（调试用）：
 *   adb shell am broadcast -a com.firefly.qstweaks.ACTION_RUN -p com.firefly.qstweaks --es cmd 5g:on
 * 声明为 exported=false，所以只有 adb(root)/同签名可触发，正常 app 打不进来。
 */
public class ActionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        String cmd = intent == null ? null : intent.getStringExtra(Executor.EXTRA_CMD);
        if (cmd == null) return;
        Intent svc = new Intent(ctx, CommandService.class);
        svc.putExtra(Executor.EXTRA_CMD, cmd);
        try {
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                ctx.startForegroundService(svc);
            } else {
                ctx.startService(svc);
            }
        } catch (Throwable t) {
            final PendingResult pr = goAsync();
            final Context app = ctx.getApplicationContext();
            new Thread(() -> {
                try { Executor.runLocal(app, cmd); }
                finally { pr.finish(); }
            }).start();
        }
    }
}