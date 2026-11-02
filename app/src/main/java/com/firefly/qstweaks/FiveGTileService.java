package com.firefly.qstweaks;

import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

/**
 * 5G 磁贴。点击在「允许 NR」和「仅 LTE」之间切换。
 * 注意：TileService 没有 onLongClick 回调（编译期已确认），长按功能只在模块注入的马甲磁贴上有。
 */
public class FiveGTileService extends TileService {

    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        final boolean on = Executor.is5gOn(getApplicationContext());
        final boolean next = !on;
        applyTile(next);   // 乐观更新，真实结果稍后刷新
        final Context app = getApplicationContext();
        new Thread(() -> {
            Executor.runLocal(app, Cmd.g5(next ? LteTarget.G5_ON : LteTarget.G5_OFF));
            main.postDelayed(this::updateTile, 800);
        }, "qstweaks-5g").start();
    }

    /** 供模块/系统调用：打开 App 设置页 */
    public void openAppFromTile() {
        try {
            startActivityAndCollapse(Actions.openApp(this));   // API 34+ 只接受 PendingIntent
        } catch (Throwable t) {
            Log.w(Executor.TAG, "open app failed", t);
        }
    }

    private void updateTile() {
        applyTile(Executor.is5gOn(getApplicationContext()));
    }

    private void applyTile(boolean on) {
        Tile t = getQsTile();
        if (t == null) return;
        t.setState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.setLabel("5G");
        t.setSubtitle(on ? "NR 已允许" : "仅 LTE");
        t.setIcon(android.graphics.drawable.Icon.createWithResource(
                getPackageName(), R.drawable.ic_tile_5g));
        try { t.updateTile(); } catch (Throwable ignored) {}
    }

    /** 外部请求刷新 */
    public static void requestUpdate(Context ctx) {
        try {
            TileService.requestListeningState(ctx, new ComponentName(ctx, FiveGTileService.class));
        } catch (Throwable t) {
            Log.w(Executor.TAG, "requestListeningState failed", t);
        }
    }
}