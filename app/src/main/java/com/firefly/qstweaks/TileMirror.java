package com.firefly.qstweaks;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

/**
 * 让 ColorOS 原生控制中心里的「5G / 息屏」磁贴真正干活。
 *
 * 思路：
 *  - TileService 绑定不依赖 QUERY_ALL_PACKAGES：显式 setComponent 绑定即可
 *  - 拿到 ITileService 代理后直接调 onClick()，由 TileService 自己的 onClick 执行命令
 *    （TileService 已在 onTileAdded 时被系统首次启动，之后过来的点击都会走它的 onClick）
 *  - 状态用 TileService.requestListeningState + 我们自己的 onStartListening 刷新
 *
 * 安全：我们让本 App 把模块所需的私有权限通过 root 授予 SystemUI(uid 1000)，
 * 否则 sendBroadcast 到 CommandService 会被权限拦截 —— 见 BootReceiver / README。
 */
final class TileMirror implements ServiceConnection {

    private static final String TAG = Executor.TAG;

    final String spec;                // "5g" / "screen"
    final String tileServiceClass;
    private volatile Object iface;    // ITileService 代理

    TileMirror(String spec, String tileServiceClass) {
        this.spec = spec;
        this.tileServiceClass = tileServiceClass;
    }

    /** 在 SystemUI 进程调用；绑定失败只记日志 */
    void bind(Context ctx) {
        try {
            android.content.ComponentName cn =
                    new android.content.ComponentName(ModuleEntry.PKG, tileServiceClass);
            android.content.Intent i = new android.content.Intent().setComponent(cn);
            boolean ok = ctx.bindService(i, this, Context.BIND_AUTO_CREATE);
            Log.d(TAG, "bind " + tileServiceClass + " => " + ok);
        } catch (Throwable t) {
            Log.w(TAG, "bind " + tileServiceClass + " failed", t);
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        try {
            Class<?> stub = Class.forName("android.service.quicksettings.ITileService$Stub");
            iface = stub.getMethod("asInterface", IBinder.class).invoke(null, service);
            Log.d(TAG, "connected " + spec);
        } catch (Throwable t) {
            Log.w(TAG, "asInterface failed", t);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        iface = null;
    }

    @Override
    public void onNullBinding(ComponentName name) {
        Log.w(TAG, "null binding: " + spec);
    }

    void click() {
        invoke("onClick");
    }

    /** ITileService 没有 onLongClick，长按只能打开 App */
    void longClick() {
        invoke("onStartListening");   // 刷新一下状态，顺手；
        try {
            android.content.Context ctx = ModuleEntry.sysUiCtx;
            if (ctx == null) return;
            android.content.Intent i = new android.content.Intent();
            i.setClassName(ModuleEntry.PKG, "com.firefly.qstweaks.MainActivity");
            i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Throwable t) {
            Log.w(TAG, "open app failed", t);
        }
    }

    private void invoke(String method) {
        Object i = iface;
        if (i == null) {
            Log.w(TAG, "not bound yet: " + spec);
            return;
        }
        try {
            java.lang.reflect.Method m = i.getClass().getMethod(method);
            m.setAccessible(true);
            m.invoke(i);
        } catch (Throwable t) {
            Log.w(TAG, "invoke " + method + " failed", t);
        }
    }

    boolean isOn() {
        Context ctx = ModuleEntry.sysUiCtx;
        if (ctx == null) return false;
        if ("screen".equals(spec)) {
            // 5 分钟档显示为"开启"状态（点亮），15 秒为"关闭"
            return ScreenStep.current(ctx.getContentResolver()) == ScreenStep.M5;
        }
        return Executor.is5gOn(ctx);
    }

    String label() {
        Context ctx = ModuleEntry.sysUiCtx;
        if ("screen".equals(spec)) {
            ScreenStep s = ctx == null ? ScreenStep.S15
                    : ScreenStep.current(ctx.getContentResolver());
            return s.label;      // "15 秒" / "5 分钟"
        }
        return "5G";
    }

    /** 副标题；息屏磁贴用主标题显示档位，不需要副标题 */
    String subtitle() {
        if ("screen".equals(spec)) return "";
        return isOn() ? "已开启" : "已关闭";
    }
}