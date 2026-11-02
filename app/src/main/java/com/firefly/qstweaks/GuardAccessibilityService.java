package com.firefly.qstweaks;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

/**
 * 无障碍服务保活（Scene 用的也是这一招）。
 *
 * 原理：无障碍服务一旦被用户在系统里开启，就由系统绑定并持有；
 * ColorOS 的"清理后台"不会把无障碍服务一起杀掉，进程因此能活下来。
 * 这个服务本身什么都不做，唯一的职责是：
 *   1. 进程保活
 *   2. onServiceConnected 时拉起 ScreenGuardService，并顺手补写一次息屏时间
 *
 * 代价：需要用户在「无障碍」里手动开启，App 里只做跳转。
 */
public class GuardAccessibilityService extends AccessibilityService {

    private static final String TAG = Executor.TAG;

    static final String SETTINGS_ACTION = Settings.ACTION_ACCESSIBILITY_SETTINGS;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "accessibility service connected");
        Context ctx = getApplicationContext();
        // 无障碍已连上 = 进程被系统保住了，把守护服务和息屏值一起拉起来
        ScreenGuardService.start(ctx);
        new Thread(() -> {
            ScreenStep want = Executor.savedScreen(ctx);
            if (want != null) {
                int cur = Settings.System.getInt(ctx.getContentResolver(),
                        Settings.System.SCREEN_OFF_TIMEOUT, -1);
                if (Math.abs(cur - want.timeoutMs) > 2000) {
                    Executor.setScreenTimeout(ctx, want);
                }
            }
        }, "qstweaks-a11y").start();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 不监听任何事件，只为保活
    }

    @Override
    public void onInterrupt() {
    }

    /** 系统里是否已经开启本服务 */
    static boolean isEnabled(Context ctx) {
        try {
            String flat = Settings.Secure.getString(ctx.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (flat == null) return false;
            ComponentName me = new ComponentName(ctx, GuardAccessibilityService.class);
            String target = me.flattenToString();
            return flat.contains(target) || flat.contains(me.flattenToShortString());
        } catch (Throwable t) {
            return false;
        }
    }

    /** 跳转到无障碍设置页（不同 ROM 路径不同，多试几个） */
    static boolean openSettings(Context ctx) {
        String[] actions = {
                "android.settings.ACCESSIBILITY_SETTINGS",
                "com.android.settings.accessibility.AccessibilitySettings",
                "com.oplus.settings.OPLUSAccessibilitySettings",
        };
        for (String a : actions) {
            try {
                Intent i = new Intent(a);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(i);
                return true;
            } catch (Throwable ignored) {}
        }
        return false;
    }
}