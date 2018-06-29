/*
 * Created on 2018/06/16.
 * Copyright © 2018 刘振林. All rights reserved.
 */

package com.liuzhenlin.sliding_drawer;

import android.support.annotation.NonNull;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.ViewCompat;
import android.view.Gravity;
import android.view.View;

/**
 * @author <a href="mailto:2233788867@qq.com">刘振林</a>
 */
public class GravityUtils {
    public static int getAbsoluteHorizontalGravity(@NonNull View parent, int gravity) {
        final int layoutDirection = ViewCompat.getLayoutDirection(parent);
        return GravityCompat.getAbsoluteGravity(gravity, layoutDirection)
                & Gravity.HORIZONTAL_GRAVITY_MASK;
    }
}
