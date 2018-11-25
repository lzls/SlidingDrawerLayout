/*
 * Created on 18-9-29 下午9:26.
 * Copyright © 2018 刘振林. All rights reserved.
 */

package com.liuzhenlin.sliding_drawer_layout.sample.utils;

import android.annotation.SuppressLint;

import java.lang.reflect.Method;

/**
 * @author <a href="mailto:2233788867@qq.com">刘振林</a>
 */
@SuppressLint("PrivateApi")
public class SystemProperties {

    private SystemProperties() throws IllegalAccessException {
        throw new IllegalAccessException("no instance!");
    }

    public static long getLong(String key, long def) {
        try {
            Class<?> clz = Class.forName("android.os.SystemProperties");
            Method getLong = clz.getMethod("getLong", String.class, long.class);
            return (long) getLong.invoke(clz, key, def);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return def;
    }

    public static long getLong(String key) {
        return getLong(key, 0L);
    }

    public static String getString(String key, String def) {
        try {
            Class<?> clz = Class.forName("android.os.SystemProperties");
            Method get = clz.getMethod("get", String.class, String.class);
            return (String) get.invoke(clz, key, def);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return def;
    }

    public static String getString(String key) {
        return getString(key, "");
    }

    public static int getInt(String key, int def) {
        try {
            Class<?> clz = Class.forName("android.os.SystemProperties");
            Method getInt = clz.getMethod("getInt", String.class, int.class);
            return (int) getInt.invoke(clz, key, def);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return def;
    }

    public static int getInt(String key) {
        return getInt(key, 0);
    }

    public static boolean getBoolean(String key, boolean def) {
        try {
            Class<?> clz = Class.forName("android.os.SystemProperties");
            Method getBoolean = clz.getMethod("getBoolean", String.class, boolean.class);
            return (boolean) getBoolean.invoke(clz, key, def);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return def;
    }

    public static boolean getBoolean(String key) {
        return getBoolean(key, false);
    }

    /**
     * 通过反射设置系统属性
     */
    public static void setProperty(String key, String value) {
        try {
            Class<?> clz = Class.forName("android.os.SystemProperties");
            Method set = clz.getMethod("set", String.class, String.class);
            set.invoke(clz, key, value);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
