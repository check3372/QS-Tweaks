package com.firefly.qstweaks;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 开机用 root 给本 App 授予关键权限（外部 su 管理器若禁止开机提权，这一步会静默失败，
 * 用户可以在 App 里手动点一次「用 Root 授予全部权限」）。
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        final PendingResult pr = goAsync();
        final Context app = ctx.getApplicationContext();
        final String pkg = ctx.getPackageName();
        new Thread(() -> {
            try {
                Executor.pmGrant(pkg, "android.permission.WRITE_SECURE_SETTINGS");
                Executor.pmGrant(pkg, "android.permission.WRITE_SETTINGS");
                Executor.appopsSet(pkg, "android:write_settings", "allow");
                Executor.pmGrant(pkg, "android.permission.MODIFY_PHONE_STATE");
                Log.d(Executor.TAG, "boot grant done");
            } finally {
                pr.finish();
            }
        }, "qstweaks-boot").start();

        // 开机拉起息屏守护（若用户开着）
        ScreenGuardService.start(app);
    }
}