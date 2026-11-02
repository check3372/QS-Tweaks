package com.firefly.qstweaks;

import android.content.Context;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 往控制中心塞两个磁贴：custom(qstweaks_5g) / custom(qstweaks_screen)
 *
 * 流程：
 *   QSHost 的列表方法返回里追加 spec  →  QSTileHost.createTile(spec) 被调用
 *   →  我们 new 出一个 QSTileImpl 实例（FakeTile），返回它
 *
 * 不同 Android 版本列表方法名/返回类型不一样，这里全部用候选方式覆盖：
 *   String[]  ← getTiles / getCurrentSpecs / getDefaultSpecs
 *   List<Tile> ← getTiles（A13+）
 */
final class TileInjector {

    static final String SPEC_5G = "custom(qstweaks_5g)";
    static final String SPEC_SCREEN = "custom(qstweaks_screen)";

    /** 伪造的 QSTileImpl 实例缓存：spec -> FakeTile（用于防止重复注入） */
    private static final Map<String, FakeTile> TILES = new ConcurrentHashMap<>();
    private static volatile boolean installed = false;

    private TileInjector() {}

    static void ensureInstalled(ClassLoader cl) {
        if (installed) return;
        installed = true;
        install(cl);
    }

    static FakeTile tile(String key) {
        return TILES.get(key);
    }

    static java.util.Collection<FakeTile> allTiles() {
        return TILES.values();
    }

    static void install(ClassLoader cl) {
        Class<?> host = XposedHelpers.findClassIfExists("com.android.systemui.qs.QSTileHost", cl);
        if (host == null) {
            ModuleEntry.log("QSTileHost 找不到（ROM 可能改了类名），磁贴注入跳过");
            return;
        }

        for (Method m : host.getDeclaredMethods()) {
            final String n = m.getName();
            final Class<?>[] pt = m.getParameterTypes();
            final Class<?> rt = m.getReturnType();

            if (n.equals("createTile") && pt.length == 1 && pt[0] == String.class) {
                tryHook(m, new CreateTileHook(), "createTile");
            } else if (pt.length == 0 && rt == String[].class
                    && (n.equals("getTiles") || n.equals("getCurrentSpecs") || n.equals("getDefaultSpecs"))) {
                tryHook(m, new StringListHook(), n);
            } else if (pt.length == 0 && List.class.isAssignableFrom(rt) && n.equals("getTiles")) {
                tryHook(m, new TileListHook(host), "getTiles(List)");
            }
        }
    }

    /** hook 失败（已经 hook 过 / 抽象方法等）只记日志，不影响其他方法 */
    private static void tryHook(Method m, XC_MethodHook hook, String tag) {
        try {
            XposedBridge.hookMethod(m, hook);
            ModuleEntry.log("hooked " + tag);
        } catch (Throwable t) {
            // NoSuchMethodError / "method already hooked" 都走这里
            ModuleEntry.log("hook " + tag + " 失败: " + t);
        }
    }

    // ------------------------------------------------------------------

    /** createTile 返回我们伪造的 QSTileImpl 实例 */
    private static final class CreateTileHook extends XC_MethodHook {
        @Override protected void afterHookedMethod(MethodHookParam p) {
            try {
                String spec = (String) p.args[0];
                if (spec == null) return;
                String key = spec.contains("qstweaks_5g") ? "5g"
                        : spec.contains("qstweaks_screen") ? "screen" : null;
                if (key == null) return;

                Context ctx = ModuleEntry.sysUiCtx;
                if (ctx == null) ctx = (Context) Reflect.get(p.thisObject, "mContext");
                if (ctx == null) { ModuleEntry.log("context null"); return; }

                FakeTile ft = TILES.get(key);
                if (ft == null) {
                    TileMirror mirror = new TileMirror(key,
                            key.equals("5g") ? TileInjector.PKG + ".FiveGTileService"
                                    : TileInjector.PKG + ".ScreenTileService");
                    mirror.bind(ctx);
                    ft = FakeTile.create(ctx, p.thisObject, mirror);
                    TILES.put(key, ft);
                    ModuleEntry.log("created tile " + key);
                }
                p.setResult(ft.impl);
                ft.render();
            } catch (Throwable t) {
                ModuleEntry.log("createTile hook error " + t);
            }
        }
    }

    private static final class StringListHook extends XC_MethodHook {
        @Override protected void afterHookedMethod(MethodHookParam p) {
            String[] arr = (String[]) p.getResult();
            if (arr == null) return;
            for (String s : arr) if (s != null && s.contains("qstweaks")) return;
            String[] out = new String[arr.length + 2];
            System.arraycopy(arr, 0, out, 0, arr.length);
            out[arr.length] = SPEC_5G;
            out[arr.length + 1] = SPEC_SCREEN;
            p.setResult(out);
        }
    }

    private static final class TileListHook extends XC_MethodHook {
        private final Class<?> hostCls;
        private Method createTileSingle;
        TileListHook(Class<?> hostCls) { this.hostCls = hostCls; }

        @Override protected void afterHookedMethod(MethodHookParam p) throws Throwable {
            Object r = p.getResult();
            if (!(r instanceof List)) return;
            if (createTileSingle == null) {
                for (Method m : hostCls.getDeclaredMethods()) {
                    if (m.getName().equals("createTile") && m.getParameterCount() == 1
                            && m.getParameterTypes()[0] == String.class) {
                        m.setAccessible(true);
                        createTileSingle = m;
                        break;
                    }
                }
            }
            if (createTileSingle == null) return;
            @SuppressWarnings("unchecked")
            List<Object> l = new ArrayList<>((List<Object>) r);
            // 避免重复注入：我们的磁贴对象在 TILES.values() 里
            for (Object o : l) {
                if (o == null) continue;
                for (FakeTile ft : TILES.values()) {
                    if (ft.impl == o) return;
                }
            }
            for (String spec : new String[]{SPEC_5G, SPEC_SCREEN}) {
                Object t = createTileSingle.invoke(p.thisObject, spec);
                if (t != null) l.add(t);
            }
            p.setResult(l);
        }
    }

    static final String PKG = "com.firefly.qstweaks";
}