package com.firefly.qstweaks;

import java.lang.reflect.Field;

/** 反射工具 */
final class Reflect {
    private Reflect() {}

    static Field find(Class<?> k, String name) {
        for (Class<?> c = k; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {}
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    static <T> T get(Object o, String name) {
        if (o == null) return null;
        Field f = find(o.getClass(), name);
        if (f == null) return null;
        try { return (T) f.get(o); } catch (Throwable t) { return null; }
    }

    static boolean set(Object o, String name, Object v) {
        if (o == null) return false;
        Field f = find(o.getClass(), name);
        if (f == null) return false;
        try { f.set(o, v); return true; } catch (Throwable t) { return false; }
    }

    /** 在给定类的层级里（而不是实例的运行时类）找字段，用于 SystemUI 多态/代理对象 */
    @SuppressWarnings("unchecked")
    static <T> T getOfType(Class<?> declared, Object o, String name) {
        if (o == null) return null;
        Field f = find(declared, name);
        if (f == null) return null;
        try { return (T) f.get(o); } catch (Throwable t) { return null; }
    }

    /** 调用无参/有参方法（find in hierarchy） */
    static Object call(Object o, String name, Object... args) {
        if (o == null) return null;
        for (Class<?> c = o.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals(name) || m.getParameterCount() != args.length) continue;
                try {
                    m.setAccessible(true);
                    return m.invoke(o, args);
                } catch (Throwable ignored) {}
            }
        }
        return null;
    }
}