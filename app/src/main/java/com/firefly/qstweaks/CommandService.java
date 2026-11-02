package com.firefly.qstweaks;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 前台服务，执行一次命令马上停（Android 14+ 后台不能起普通 Service）。
 */
public class CommandService extends Service {

    private static final String CH = "svc";
    private final ExecutorService pool = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final String cmd = intent == null ? null : intent.getStringExtra(Executor.EXTRA_CMD);

        try {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(
                        new NotificationChannel(CH, "QS 后台", NotificationManager.IMPORTANCE_MIN));
            }
            Notification n = new Notification.Builder(this, CH)
                    .setSmallIcon(android.R.drawable.stat_notify_sync)
                    .setContentTitle("QS Tweaks")
                    .setContentText(cmd == null ? "idle" : cmd)
                    .setOnlyAlertOnce(true)
                    .build();
            startForeground(0x5151, n);
        } catch (Throwable ignored) {}

        if (cmd == null) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        final android.content.Context app = getApplicationContext();
        pool.execute(() -> {
            Executor.runLocal(app, cmd);
            main.post(() -> {
                // 命令执行完顺手把两个磁贴刷新一下（磁贴状态不会自己跟着 settings 变）
                FiveGTileService.requestUpdate(app);
                ScreenTileService.requestUpdate(app);
                stopSelf(startId);
            });
        });
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        pool.shutdown();
        super.onDestroy();
    }
}