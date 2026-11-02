package com.firefly.qstweaks;

import android.content.Context;
import android.content.Intent;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * LSPosed 入口（作用域：com.android.systemui）。
 *
 *  1. TileInjector —— 在控制中心塞「5G」「息屏」两个磁贴
 *  2. 心跳        —— 向 App 发广播，设置页显示"模块已激活"
 */
public class ModuleEntry implements IXposedHookLoadPackage {

    static final String PKG = "com.firefly.qstweaks";
    static final String TAG = "QSTweaks";

    /** SystemUI 的 Context（Hook Application#onCreate 时拿到） */
    static volatile Context sysUiCtx;

    static void log(String s) {
        XposedBridge.log("[QSTweaks] " + s);
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"com.android.systemui".equals(lpparam.packageName)) return;
        log("SystemUI loaded, Android " + android.os.Build.VERSION.RELEASE);

        try {
            XposedHelpers.findAndHookMethod(
                    "android.app.Application", lpparam.classLoader,
                    "onCreate", new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            sysUiCtx = (Context) p.thisObject;
                            ping();
                        }
                    });
        } catch (Throwable t) {
            log("Application#onCreate hook failed " + t);
        }

        try { TileInjector.install(lpparam.classLoader); }
        catch (Throwable t) { log("TileInjector: " + t); }

        // 移动数据长按二级菜单功能已移除

        new Thread(() -> {
            try {
                Thread.sleep(8000);
                ping();
            } catch (Throwable ignored) {}
        }, "qstweaks-ping").start();
    }

    static void ping() {
        try {
            if (sysUiCtx == null) return;
            Intent i = new Intent(TileCommandReceiver.ACTION_PING);
            i.setPackage(PKG);
            i.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            sysUiCtx.sendBroadcast(i);
        } catch (Throwable ignored) {}
    }
}