/*
 * Created on 2017/10/12.
 * Copyright © 2017 刘振林. All rights reserved.
 */

package com.liuzhenlin.sliding_drawer_sample.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.support.annotation.ColorInt;
import android.support.annotation.ColorRes;
import android.support.annotation.NonNull;
import android.support.annotation.Px;
import android.support.annotation.RequiresApi;
import android.support.v4.content.res.ResourcesCompat;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * @author <a href="mailto:2233788867@qq.com">刘振林</a>
 */
public class SystemBarUtil {
    private SystemBarUtil() throws IllegalAccessException {
        throw new IllegalAccessException("no instance!");
    }

    @Px
    public static int getStatusHeight(@NonNull Context context) {
        final int resId = context.getResources().getIdentifier("status_bar_height",
                "dimen", "android");
        if (resId > 0) {
            return context.getResources().getDimensionPixelSize(resId);
        }
        return 0;
    }

    @Px
    @RequiresApi(Build.VERSION_CODES.KITKAT)
    public static int getNavigationHeight(@NonNull Context context) {
        final int resId = context.getResources().getIdentifier("navigation_bar_height",
                "dimen", "android");
        if (resId > 0) {
            return context.getResources().getDimensionPixelSize(resId);
        }
        return 0;
    }

    /**
     * 判断是否有虚拟按键
     */
    @SuppressLint("PrivateApi")
    @RequiresApi(Build.VERSION_CODES.KITKAT)
    public static boolean hasNavigationBar(@NonNull Context context) {
        boolean hasNavBar = false;
        final int resId = context.getResources().getIdentifier("config_showNavigationBar",
                "bool", "android");
        if (resId > 0) {
            hasNavBar = context.getResources().getBoolean(resId);
        }

        try {
            Class<?> systemPropertiesClass = Class.forName("android.os.SystemProperties");
            Method m = systemPropertiesClass.getMethod("get", String.class);
            String navBarOverride = (String) m.invoke(systemPropertiesClass, "qemu.hw.mainkeys");

            if ("1".equals(navBarOverride)) {
                hasNavBar = false;
            } else if ("0".equals(navBarOverride)) {
                hasNavBar = true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return hasNavBar;
    }

    /**
     * 设置显示或隐藏状态栏和虚拟按键
     * {@link View#SYSTEM_UI_FLAG_LAYOUT_STABLE}：使View的布局不变，隐藏状态栏或导航栏后，View不会被拉伸。
     * {@link View#SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN}：让decorView全屏显示，
     * 但状态栏不会被隐藏覆盖，状态栏依然可见，decorView顶端布局部分会被状态遮住。
     * {@link View#SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION}：让decorView全屏显示，
     * 但导航栏不会被隐藏覆盖，导航栏依然可见，decorView底端布局部分会被导航栏遮住。
     */
    public static void showSystemBars(@NonNull Window window, boolean show) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            View decorView = window.getDecorView();
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    // The above 3 flags make the content appear
                    // under the system bars so that the content doesn't resize
                    // when the system bars hide and show.
                    | View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide navigation bar
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            // The IMMERSIVE_STICKY flag to prevent the flags of
            // hiding navigation bar and hiding status bar from being force-cleared
            // by the system on any user interaction.
            if (show) {
                // This snippet shows the system bars.
                // It does this by removing all the flags.
                // Make the content appear below status bar and above navigation bar(if the device has).
                flags = (decorView.getSystemUiVisibility() & ~flags);
            } else {
                // This snippet hides the system bars.
                flags |= decorView.getSystemUiVisibility();
            }
            decorView.setSystemUiVisibility(flags);
        } else {
            final int flags = WindowManager.LayoutParams.FLAG_FULLSCREEN
                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
            if (show) {
                window.clearFlags(flags);
            } else {
                window.addFlags(flags);
            }
        }
    }

    /**
     * 设置 半透明状态栏
     */
    @RequiresApi(Build.VERSION_CODES.KITKAT)
    public static void setTranslucentStatus(@NonNull Window window, boolean translucent) {
        final int statusFlag = WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS;
        if (translucent) {
            window.addFlags(statusFlag);
        } else {
            window.clearFlags(statusFlag);
        }
    }

    /**
     * 设置 半透明虚拟按键
     */
    @RequiresApi(Build.VERSION_CODES.KITKAT)
    public static void setTranslucentNavigation(@NonNull Window window, boolean translucent) {
        final int navFlag = WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION;
        if (translucent) {
            window.addFlags(navFlag);
        } else {
            window.clearFlags(navFlag);
        }
    }

    /**
     * 改变状态栏字体颜色（黑/白）
     *
     * @see <a href="https://developer.android.com/reference/android/R.attr.html#windowLightStatusBar"></a>
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    public static void setLightStatus(@NonNull Window window, boolean light) {
        View decorView = window.getDecorView();
        int flags = decorView.getSystemUiVisibility();
        if (light) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        } else {
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        decorView.setSystemUiVisibility(flags);
    }

    public static void setLightStatusForMiuiOrFlyMe(@NonNull Window window, boolean light) {
        try {
            setLightStatusForMiui(window, light);
        } catch (Exception e) {
            try {
                setLightStatusForFlyMe(window, light);
            } catch (Exception e2) {
                //
            }
        }
    }

    /**
     * 改变小米手机的状态栏字体颜色, 要求MIUI6及以上
     */
    private static void setLightStatusForMiui(Window window, boolean light) throws Exception {
        @SuppressLint("PrivateApi")
        Class<?> layoutParams = Class.forName("android.view.MiuiWindowManager$LayoutParams");
        final int darkModeFlag = layoutParams.getField("EXTRA_FLAG_STATUS_BAR_DARK_MODE")
                .getInt(layoutParams);

        Method setExtraFlags = window.getClass().getMethod("setExtraFlags", int.class, int.class);
        setExtraFlags.invoke(window, light ? darkModeFlag : 0, darkModeFlag);
    }

    /**
     * 改变魅族手机的状态栏字体颜色，要求FlyMe4及以上
     */
    @SuppressWarnings("JavaReflectionMemberAccess")
    private static void setLightStatusForFlyMe(Window window, boolean light) throws Exception {
        WindowManager.LayoutParams lp = window.getAttributes();

        Class<?> wmlpClass = WindowManager.LayoutParams.class;

        Field meizuFlags = wmlpClass.getDeclaredField("meizuFlags");
        meizuFlags.setAccessible(true);
        final int origin = meizuFlags.getInt(lp);

        Field darkFlag = wmlpClass.getDeclaredField("MEIZU_FLAG_DARK_STATUS_BAR_ICON");
        darkFlag.setAccessible(true);
        final int value = darkFlag.getInt(null);

        if (light) {
            meizuFlags.setInt(lp, origin | value);
        } else {
            meizuFlags.setInt(lp, origin & ~value);
        }
        window.setAttributes(lp);
    }

    /**
     * 设置状态栏的颜色
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static void setStatusBackgroundColor(@NonNull Window window, @ColorInt int color) {
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(color);
    }

    /**
     * 设置导航栏的颜色
     */
    @SuppressWarnings("WeakerAccess")
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static void setNavigationBackgroundColor(@NonNull Window window, @ColorInt int color) {
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setNavigationBarColor(color);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static void setStatusBackgroundColorRes(@NonNull Window window, @ColorRes int colorId) {
        Context context = window.getContext();
        setStatusBackgroundColor(window, ResourcesCompat.getColor(context.getResources(),
                colorId, context.getTheme()));
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static void setNavigationBackgroundColorRes(@NonNull Window window, @ColorRes int colorId) {
        Context context = window.getContext();
        setNavigationBackgroundColor(window, ResourcesCompat.getColor(context.getResources(),
                colorId, context.getTheme()));
    }
}