package com.firefly.qstweaks;

import android.content.Context;
import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 在 SystemUI 里"伪装"成一个 QSTileImpl 实例。
 *
 * 编译期没有 SystemUI 的类，运行时：
 *  - XposedHelpers 找到 QSTileImpl
 *  - 用它的构造器 new 一个对象
 *  - Hook QSTileImpl 的 click / longClick / getTileLabel / handleUpdateState，
 *    只在 this 是我们的实例时接管
 *  - 往 mTile 写状态，系统磁贴 View 就会渲染成我们的样子
 */
final class FakeTile {

    final Class<?> implCls;
    final Object impl;
    final TileMirror mirror;
    final Context ctx;

    /** QSTileImpl 的方法只需要 hook 一次，所有 FakeTile 共用 */
    private static volatile boolean methodsHooked = false;

    static FakeTile create(Context ctx, Object host, TileMirror mirror) throws Throwable {
        Class<?> impl = XposedHelpers.findClassIfExists(
                "com.android.systemui.qs.tileimpl.QSTileImpl", ctx.getClassLoader());
        if (impl == null) throw new ClassNotFoundException("QSTileImpl");

        Object obj = null;
        for (Constructor<?> c : impl.getDeclaredConstructors()) {
            Class<?>[] pt = c.getParameterTypes();
            if (pt.length == 1 && pt[0].isInstance(host)) {
                c.setAccessible(true);
                obj = c.newInstance(host);
                break;
            }
        }
        if (obj == null) throw new NoSuchMethodException("QSTileImpl(QSTileHost)");

        FakeTile t = new FakeTile(impl, obj, mirror, ctx);
        if (Reflect.get(obj, "mContext") == null) Reflect.set(obj, "mContext", ctx);
        if (Reflect.get(obj, "mHandler") == null) {
            Reflect.set(obj, "mHandler", new android.os.Handler(android.os.Looper.getMainLooper()));
        }
        // QSTileImpl 构造器里可能已经调过一些用到 mStatusBarService 的东西；
        // 这里再补一次（instance 字段，不会影响其他磁贴）
        if (Reflect.get(obj, "mStatusBarService") == null) {
            Object tsm = findExistingTileServiceManager(host);
            if (tsm != null) {
                Reflect.set(obj, "mStatusBarService", tsm);
                ModuleEntry.log("patched mStatusBarService for " + mirror.spec);
            } else {
                ModuleEntry.log("mStatusBarService 未找到，磁贴点击可能无效");
            }
        }

        if (!methodsHooked) {
            methodsHooked = true;
            intercept(impl, "click");
            intercept(impl, "longClick");
            intercept(impl, "getTileLabel");
            intercept(impl, "handleUpdateState");
        }
        return t;
    }

    /** 从 host 已有的磁贴里借一个 TileServiceManager */
    @SuppressWarnings("unchecked")
    private static Object findExistingTileServiceManager(Object host) {
        try {
            // AOSP: QSHost#getTiles() -> List<QSTile>，每块 tile 的 mStatusBarService
            Object tiles = Reflect.call(host, "getTiles");
            if (tiles instanceof Iterable) {
                for (Object tile : (Iterable<Object>) tiles) {
                    if (tile == null) continue;
                    Object s = Reflect.get(tile, "mStatusBarService");
                    if (s != null) return s;
                }
            }
            // 退路：QSTileHost#mTileServiceManagerMap / mTileServices
            Object map = Reflect.get(host, "mTileServiceManagerMap");
            if (map instanceof java.util.Map && !((java.util.Map<?, ?>) map).isEmpty()) {
                return ((java.util.Map<?, ?>) map).values().iterator().next();
            }
            Object list = Reflect.get(host, "mTileServices");
            if (list instanceof java.util.List && !((java.util.List<?>) list).isEmpty()) {
                return ((java.util.List<?>) list).get(0);
            }
        } catch (Throwable t) {
            ModuleEntry.log("findExistingTileServiceManager failed " + t);
        }
        return null;
    }

    private FakeTile(Class<?> implCls, Object impl, TileMirror mirror, Context ctx) {
        this.implCls = implCls;
        this.impl = impl;
        this.mirror = mirror;
        this.ctx = ctx;
    }

    // ------------------------------------------------------------

    /** 对应的 FakeTile 实例（不是我们的 tile 就返回 null） */
    private static FakeTile owner(XC_MethodHook.MethodHookParam p) {
        Object self = p.thisObject;
        if (self == null) return null;
        for (FakeTile t : TileInjector.allTiles()) {
            if (t.impl == self) return t;
        }
        return null;
    }

    private static void intercept(Class<?> impl, String name) {
        for (Method m : impl.getDeclaredMethods()) {
            if (!m.getName().equals(name)) continue;
            try {
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        FakeTile t = owner(p);
                        if (t == null) return;
                        switch (name) {
                            case "click":
                                p.setResult(null);
                                t.mirror.click();
                                new android.os.Handler(android.os.Looper.getMainLooper())
                                        .postDelayed(t::render, 900);
                                break;
                            case "longClick":
                                p.setResult(null);
                                t.mirror.longClick();
                                break;
                            case "getTileLabel":
                                p.setResult(t.mirror.label());
                                break;
                            case "handleUpdateState":
                                if (p.args != null && p.args.length > 0 && p.args[0] instanceof Tile) {
                                    p.setResult(null);
                                    t.applyTo((Tile) p.args[0]);
                                }
                                break;
                            default:
                                break;
                        }
                    }
                });
            } catch (Throwable e) {
                ModuleEntry.log("intercept " + name + " failed " + e);
            }
        }
    }

    // ------------------------------------------------------------ 渲染

    void render() {
        try {
            Tile tile = (Tile) Reflect.get(impl, "mTile");
            if (tile != null) applyTo(tile);
        } catch (Throwable t) {
            ModuleEntry.log("render failed " + t);
        }
    }

    void applyTo(Tile tile) {
        try {
            boolean on = mirror.isOn();
            tile.setState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            tile.setLabel(mirror.label());
            tile.setSubtitle(mirror.subtitle());
            tile.setIcon(Icon.createWithResource(ctx.getPackageName(),
                    "5g".equals(mirror.spec) ? R.drawable.ic_tile_5g : R.drawable.ic_tile_screen));
            tile.updateTile();
        } catch (Throwable t) {
            ModuleEntry.log("applyTo failed " + t);
        }
    }
}