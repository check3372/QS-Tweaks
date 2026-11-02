package com.firefly.qstweaks;

import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;
import android.widget.Toast;

/**
 * 息屏时间磁贴：点击在「15 秒」和「5 分钟」之间切换。
 *
 * ColorOS 会在 App 进程结束后把 SCREEN_OFF_TIMEOUT 恢复成默认值。
 * 这里的自检只是兜底，真正持续保持由 ScreenGuardService（常驻前台服务）负责，
 * 详见 README「关于划掉后台就失效」。
 */
public class ScreenTileService extends TileService {

    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    public void onStartListening() {
        super.onStartListening();
        enforce();
        updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        final ScreenStep cur = ScreenStep.current(getContentResolver());
        final ScreenStep next = ScreenStep.next(cur);
        // 先别动显示，写完确认成功再刷，避免"看起来切了其实没切"
        final Context app = getApplicationContext();
        new Thread(() -> {
            final boolean ok = Executor.setScreenTimeout(app, next);
            main.post(() -> {
                if (!ok) {
                    // 写失败：只提示，不回滚成假状态
                    Toast.makeText(app, "息屏时间写入失败，点开 QS Tweaks 查看诊断",
                            Toast.LENGTH_SHORT).show();
                } else {
                    ScreenGuardService.start(app);
                }
                updateTile();
            });
        }, "qstweaks-screen").start();
    }

    /** 供模块/系统调用：打开 App 设置页 */
    public void openAppFromTile() {
        try {
            startActivityAndCollapse(Actions.openApp(this));
        } catch (Throwable t) {
            Log.w(Executor.TAG, "open app failed", t);
        }
    }

    /**
     * 当前值 != 用户上次选的档位 就补写回去。
     * （ColorOS 重置出来的值可能正好是 30000ms，用"是否在两档里"判断会漏）
     */
    private void enforce() {
        final ScreenStep want = Executor.savedScreen(this);
        if (want == null) return;
        final int cur = android.provider.Settings.System.getInt(
                getContentResolver(), android.provider.Settings.System.SCREEN_OFF_TIMEOUT, -1);
        if (cur == want.timeoutMs) return;
        final Context app = getApplicationContext();
        new Thread(() -> Executor.setScreenTimeout(app, want), "qstweaks-enforce").start();
    }

    private void updateTile() {
        applyTile(ScreenStep.current(getContentResolver()));
    }

    private void applyTile(ScreenStep s) {
        Tile t = getQsTile();
        if (t == null) return;
        // 5 分钟档 = 亮（ACTIVE），和 5G 磁贴开启时的观感一致
        t.setState(s == ScreenStep.M5 ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.setLabel("息屏");
        t.setSubtitle(s.label);
        t.setIcon(android.graphics.drawable.Icon.createWithResource(
                getPackageName(), R.drawable.ic_tile_screen));
        try { t.updateTile(); } catch (Throwable ignored) {}
    }

    public static void requestUpdate(Context ctx) {
        try {
            TileService.requestListeningState(ctx, new ComponentName(ctx, ScreenTileService.class));
        } catch (Throwable e) {
            Log.w(Executor.TAG, "requestListeningState failed", e);
        }
    }
}