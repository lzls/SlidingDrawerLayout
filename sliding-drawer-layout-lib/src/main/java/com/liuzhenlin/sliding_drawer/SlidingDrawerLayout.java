/*
 * Created on 2018/05/16.
 * Copyright © 2018 刘振林. All rights reserved.
 */

package com.liuzhenlin.sliding_drawer;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.ColorInt;
import android.support.annotation.FloatRange;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.animation.LinearOutSlowInInterpolator;
import android.support.v4.widget.ViewDragHelper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewStub;
import android.view.animation.Interpolator;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.List;
import java.util.WeakHashMap;

/**
 * A layout shows better than {@link android.support.v4.widget.DrawerLayout}, which can also
 * scroll its contents as the user drags its drawer.
 *
 * @author <a href="mailto:2233788867@qq.com">刘振林</a>
 */
public class SlidingDrawerLayout extends ViewGroup {
    private static final String TAG = "SlidingDrawerLayout";

    /** The left child view covered by {@link #mContentView} */
    private View mLeftDrawer;

    /** The right child view covered by {@link #mContentView} */
    private View mRightDrawer;

    /** The content view in this layout that is always visible. */
    private View mContentView;

    /**
     * The drawer currently shown, maybe one of {@link #mLeftDrawer}, {@link #mRightDrawer}
     * or <code>null</code>.
     */
    private View mShownDrawer;

    /**
     * Caches the layer type of the shown drawer, which can be one of {@link #LAYER_TYPE_NONE},
     * {@link #LAYER_TYPE_SOFTWARE} or {@link #LAYER_TYPE_HARDWARE}.
     */
    private int mShownDrawerLayerType = LAYER_TYPE_NONE;

    /**
     * @see #getLeftDrawerWidthPercent()
     * @see #setLeftDrawerWidthPercent(float)
     */
    private float mLeftDrawerWidthPercent;

    /**
     * @see #getRightDrawerWidthPercent()
     * @see #setRightDrawerWidthPercent(float)
     */
    private float mRightDrawerWidthPercent;

    /** Caches the width percentage of the start drawer. */
    private float mStartDrawerWidthPercent;

    /** Caches the width percentage of the end drawer. */
    private float mEndDrawerWidthPercent;

    /** Used for the drawer of which the width percentage is not defined. */
    private static final int UNDEFINED_DRAWER_WIDTH_PERCENT = -1;

    /**
     * Used for the drawer of which the width percentage is not resolved before
     * the layout direction of this view resolved.
     */
    public static final int UNRESOLVED_DRAWER_WIDTH_PERCENT = UNDEFINED_DRAWER_WIDTH_PERCENT;

    /**
     * If set, the width of the relevant drawer will be measured as it is, within a percentage
     * ranging from {@value #MINIMUM_DRAWER_WIDTH_PERCENT} to {@value #MAXIMUM_DRAWER_WIDTH_PERCENT}.
     */
    public static final int UNSPECIFIED_DRAWER_WIDTH_PERCENT = 0;

    /** The minimum percentage of the width of the drawers relative to current view's. */
    public static final float MINIMUM_DRAWER_WIDTH_PERCENT = 0.1f;
    /** The maximum percentage of the width of the drawers relative to current view's. */
    public static final float MAXIMUM_DRAWER_WIDTH_PERCENT = 0.9f;

    private int mFlags;

    /** No drawer is currently scrolling. */
    public static final int SCROLL_STATE_IDLE = 0;

    /** There is a drawer currently scrolling and being dragged by user. */
    public static final int SCROLL_STATE_TOUCH_SCROLL = 1;

    /**
     * A drawer is currently scrolling but not under outside control.
     * For example, it is being translated by the animator.
     */
    public static final int SCROLL_STATE_AUTO_SCROLL = 1 << 1;

    /** Mask for use with {@link #mFlags} to get the drawer scroll state. */
    private static final int SCROLL_STATE_MASK = 0b0000_0011;

    @IntDef({
            SCROLL_STATE_IDLE,
            SCROLL_STATE_TOUCH_SCROLL,
            SCROLL_STATE_AUTO_SCROLL
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ScrollState {
    }

    /** Indicates that the left drawer {@link #mLeftDrawer} is enabled. */
    private static final int FLAG_LEFT_DRAWER_ENABLED = 1 << 2;

    /** Indicates that the right drawer {@link #mRightDrawer} is enabled. */
    private static final int FLAG_RIGHT_DRAWER_ENABLED = 1 << 3;

    private static final int FLAG_START_DRAWER_ENABLED = 1 << 4;
    private static final int FLAG_END_DRAWER_ENABLED = 1 << 5;

    private static final int FLAG_LEFT_DRAWER_ABILITY_DEFINED = 1 << 6;
    private static final int FLAG_RIGHT_DRAWER_ABILITY_DEFINED = 1 << 7;

    private static final int FLAG_START_DRAWER_ABILITY_DEFINED = 1 << 8;
    private static final int FLAG_END_DRAWER_ABILITY_DEFINED = 1 << 9;

    private static final int FLAG_START_DRAWER_ABILITY_RESOLVED = 1 << 10;
    private static final int FLAG_END_DRAWER_ABILITY_RESOLVED = 1 << 11;

    /**
     * Flag indicates that the ability of start/end drawer has been resolved to left/right
     * drawer ability for use in confirming which drawer in this layout is slidable. This is set by
     * {@link #resolveDrawerAbilities(int)} and checked by {@link #onMeasure(int, int)} to determine
     * if any drawer ability needs to be resolved during measurement.
     */
    private static final int FLAG_DRAWER_ABILITIES_RESOLVED = 1 << 12;

    /**
     * Flag indicates that the width percentage of start/end drawer has been resolved into
     * left/right one for use in measurement, layout, drawing, etc. This is set by
     * {@link #resolveDrawerWidthPercentages(int, boolean)} and checked by {@link #onMeasure(int, int)}
     * to determine if any drawer width percentage needs to be resolved during measurement.
     */
    private static final int FLAG_DRAWER_WIDTH_PERCENTAGES_RESOLVED = 1 << 13;

    private static final int FLAG_START_DRAWER_WIDTH_PERCENTAGE_RESOLVED = 1 << 14;
    private static final int FLAG_END_DRAWER_WIDTH_PERCENTAGE_RESOLVED = 1 << 15;

    /**
     * Bit for {@link #mFlags}: <code>true</code> when the application is willing to support RTL
     * (right to left). All activities will inherit this value.
     * Set from the {@link android.R.attr#supportsRtl} attribute in the activity's manifest.
     * Default value is false (no support for RTL).
     */
    private static final int FLAG_SUPPORTS_RTL = 1 << 16;

    /** Indicates that the left drawer is in this layout and needs to be laid out. */
    private static final int FLAG_LEFT_DRAWER_IN_LAYOUT = 1 << 17;

    /** Indicates that the right drawer is in this layout and needs to be laid out. */
    private static final int FLAG_RIGHT_DRAWER_IN_LAYOUT = 1 << 18;

    /**
     * Flag indicating whether the user's finger downs on the area of content view {@link #mContentView}
     * when this view with a drawer open receives {@link MotionEvent#ACTION_DOWN}.
     */
    private static final int FLAG_FINGER_DOWNS_ON_CONTENT_WHEN_DRAWER_IS_OPEN = 1 << 19;

    /**
     * Flag indicating whether we should close the drawer currently open when user presses
     * the back key. By default, this is <code>true</code>.
     */
    private static final int FLAG_CLOSE_OPEN_DRAWER_ON_BACK_PRESSED_ENABLED = 1 << 20;

    /** If set, the drawer is currently being or scheduled to be opened via the animator. */
    private static final int FLAG_ANIMATING_DRAWER_OPENING = 1 << 21;

    /** If set, the drawer is currently being or scheduled to be closed via the animator. */
    private static final int FLAG_ANIMATING_DRAWER_CLOSURE = 1 << 22;

    /**
     * @see #getContentSensitiveEdgeSize()
     * @see #setContentSensitiveEdgeSize(float)
     */
    private float mContentSensitiveEdgeSize; // px

    /**
     * Default size in pixels for the touch-sensitive edges of the content view.
     */
    public static final int DEFAULT_EDGE_SIZE = 50; //dp

    /** Device independent pixel (dip/dp) */
    protected final float mDp;
    /** Distance to travel before drag may begin */
    protected final float mTouchSlop;

    /** Last known pointer id for touch events */
    private int mActivePointerId = ViewDragHelper.INVALID_POINTER;

    private float mDownX;
    private float mDownY;

    private final float[] mTouchX = new float[2];
    private final float[] mTouchY = new float[2];

    private VelocityTracker mVelocityTracker;

    /**
     * Minimum gesture speed along the x axis to automatically scroll the drawers,
     * as measured in dips per second.
     */
    private final float mAutoScrollDrawerMinimumVelocityX; // 500 dp/s

    /**
     * The ratio of the distance to scroll content view {@link #mContentView} to the distance
     * to scroll the drawer currently being dragged {@link #mShownDrawer}.
     * <p>
     * While that drawer is scrolling, we simultaneously make the content scroll at a higher speed
     * than the drawer's.
     */
    private static final float SCROLL_RATIO_CONTENT_TO_DRAWER = 3f / 1f;

    /** @see #getScrollPercent() */
    @FloatRange(from = 0.0f, to = 1.0f)
    private float mScrollPercent;

    /**
     * Animator for scrolling the drawers ({@link #mLeftDrawer}, {@link #mRightDrawer}).
     *
     * @see ScrollDrawerAnimator
     */
    private ScrollDrawerAnimator mScrollDrawerAnimator;

    /** Time interpolator used for {@link #mScrollDrawerAnimator} */
    private static final Interpolator sInterpolator = new LinearOutSlowInInterpolator();

    /**
     * Time interval in milliseconds of automatically scrolling the drawers.
     *
     * @see #getDuration()
     * @see #setDuration(int)
     */
    private int mDuration;

    /**
     * Default duration of the animator used to open/close the drawers.
     * <p>
     * If no value for {@link #mDuration} is set, then this default one is used.
     */
    public static final int DEFAULT_DURATION = 256; // ms

    /**
     * Runnable to be run for opening the drawer represented by a ViewStub and not yet added
     * to this layout (even not being inflated).
     *
     * @see ViewStub
     * @see OpenStubDrawerRunnable
     */
    private OpenStubDrawerRunnable mOpenStubDrawerRunnable;

    /**
     * The set of Runnables to be executed for starting the drawer animator to open or close
     * the showing drawer normally, as scheduled during layout passes.
     * <p>
     * <strong>NOTE:</strong> There is not more than one action running after the others
     * were removed during the completed layout processes.
     */
    private final List<Runnable> mScheduledOpenDrawerRunnables = new LinkedList<>();

    /**
     * The fade color used for the content view {@link #mContentView}, default is 50% black.
     *
     * @see #getContentFadeColor()
     * @see #setContentFadeColor(int)
     */
    @ColorInt
    private int mContentFadeColor = DEFAULT_FADE_COLOR;

    /**
     * Default fade color for the content view if no custom value is provided
     */
    @ColorInt
    public static final int DEFAULT_FADE_COLOR = 0x7F000000;

    @SuppressLint("RtlHardcoded")
    @IntDef({Gravity.LEFT, Gravity.RIGHT, GravityCompat.START, GravityCompat.END})
    @Retention(RetentionPolicy.SOURCE)
    private @interface EdgeGravity {
    }

    private class ScrollDrawerAnimator extends ValueAnimator {
        final AnimatorListener listener = new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                dispatchDrawerScrollStateChangeIfNeeded(SCROLL_STATE_AUTO_SCROLL);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mFlags &= ~(FLAG_ANIMATING_DRAWER_OPENING | FLAG_ANIMATING_DRAWER_CLOSURE);
                // Only when the drawer currently shown is not being dragged by user, i.e., this
                // animation normally ends, is the idle scroll state dispatched to the listeners.
                if ((mFlags & SCROLL_STATE_MASK) == SCROLL_STATE_AUTO_SCROLL) {
                    dispatchDrawerScrollStateChangeIfNeeded(SCROLL_STATE_IDLE);
                }
            }
        };

        ScrollDrawerAnimator() {
            setInterpolator(sInterpolator);
            setDuration(mDuration);
            addListener(listener);
            addUpdateListener(new AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    scrollDrawerTo(mShownDrawer, (float) animation.getAnimatedValue());
                }
            });
        }

        /**
         * Cancels this running animation with no view layer cleanup or scroll state change
         * for the drawer this animation targets to.
         */
        void cancelWithNoListenerCalled() {
            removeListener(listener);
            super.cancel();
            addListener(listener);
        }
    }

    private class OpenStubDrawerRunnable implements Runnable {
        final View drawer;
        final boolean animate;

        OpenStubDrawerRunnable(View drawer, boolean animate) {
            this.drawer = drawer;
            this.animate = animate;
        }

        @Override
        public void run() {
            mOpenStubDrawerRunnable = null;
            openDrawer(drawer, animate);
        }

        void removeFromCallbacks() {
            mOpenStubDrawerRunnable = null;
            removeCallbacks(this);
        }
    }

    public SlidingDrawerLayout(Context context) {
        this(context, null);
    }

    public SlidingDrawerLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SlidingDrawerLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mDp = context.getResources().getDisplayMetrics().density;
        mTouchSlop = ViewConfiguration.getTouchSlop() * mDp;
        mAutoScrollDrawerMinimumVelocityX = mDp * 500f;

        ApplicationInfo ai = context.getApplicationInfo();
        if (ai.targetSdkVersion >= Build.VERSION_CODES.JELLY_BEAN_MR1
                && (ai.flags & ApplicationInfo.FLAG_SUPPORTS_RTL) != 0) {
            mFlags |= FLAG_SUPPORTS_RTL;
        }

        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.SlidingDrawerLayout, defStyleAttr, 0);
        mLeftDrawerWidthPercent = a.getFloat(R.styleable
                .SlidingDrawerLayout_widthPercent_leftDrawer, UNDEFINED_DRAWER_WIDTH_PERCENT);
        mRightDrawerWidthPercent = a.getFloat(R.styleable
                .SlidingDrawerLayout_widthPercent_rightDrawer, UNDEFINED_DRAWER_WIDTH_PERCENT);
        mStartDrawerWidthPercent = a.getFloat(R.styleable
                .SlidingDrawerLayout_widthPercent_startDrawer, UNDEFINED_DRAWER_WIDTH_PERCENT);
        mEndDrawerWidthPercent = a.getFloat(R.styleable
                .SlidingDrawerLayout_widthPercent_endDrawer, UNDEFINED_DRAWER_WIDTH_PERCENT);
        checkDrawerWidthPercent(mLeftDrawerWidthPercent, false);
        checkDrawerWidthPercent(mRightDrawerWidthPercent, false);
        checkDrawerWidthPercent(mStartDrawerWidthPercent, false);
        checkDrawerWidthPercent(mEndDrawerWidthPercent, false);
        if (a.hasValue(R.styleable.SlidingDrawerLayout_enabled_leftDrawer)) {
            mFlags |= FLAG_LEFT_DRAWER_ABILITY_DEFINED;
            if (a.getBoolean(R.styleable.SlidingDrawerLayout_enabled_leftDrawer, true)) {
                mFlags |= FLAG_LEFT_DRAWER_ENABLED;
            }
        }
        if (a.hasValue(R.styleable.SlidingDrawerLayout_enabled_rightDrawer)) {
            mFlags |= FLAG_RIGHT_DRAWER_ABILITY_DEFINED;
            if (a.getBoolean(R.styleable.SlidingDrawerLayout_enabled_rightDrawer, true)) {
                mFlags |= FLAG_RIGHT_DRAWER_ENABLED;
            }
        }
        if (a.hasValue(R.styleable.SlidingDrawerLayout_enabled_startDrawer)) {
            mFlags |= FLAG_START_DRAWER_ABILITY_DEFINED;
            if (a.getBoolean(R.styleable.SlidingDrawerLayout_enabled_startDrawer, true)) {
                mFlags |= FLAG_START_DRAWER_ENABLED;
            }
        }
        if (a.hasValue(R.styleable.SlidingDrawerLayout_enabled_endDrawer)) {
            mFlags |= FLAG_END_DRAWER_ABILITY_DEFINED;
            if (a.getBoolean(R.styleable.SlidingDrawerLayout_enabled_endDrawer, true)) {
                mFlags |= FLAG_END_DRAWER_ENABLED;
            }
        }
        setContentSensitiveEdgeSize(a.getDimensionPixelSize(R.styleable
                .SlidingDrawerLayout_contentSensitiveEdgeSize, (int) (DEFAULT_EDGE_SIZE * mDp + 0.5f)));
        setContentFadeColor(a.getColor(R.styleable
                .SlidingDrawerLayout_contentFadeColor, DEFAULT_FADE_COLOR));
        setDuration(a.getInteger(R.styleable.SlidingDrawerLayout_duration, DEFAULT_DURATION));
        setCloseOpenDrawerOnBackPressedEnabled(a.getBoolean(R.styleable
                .SlidingDrawerLayout_closeOpenDrawerOnBackPressedEnabled, true));
        a.recycle();

        // So that we can catch the back button
        setFocusableInTouchMode(true);
    }

    /**
     * @return the percentage of the width of the left drawer relative to current view's within
     * the range from {@value MINIMUM_DRAWER_WIDTH_PERCENT} to {@value #MAXIMUM_DRAWER_WIDTH_PERCENT}
     * or just {@link #UNSPECIFIED_DRAWER_WIDTH_PERCENT} if no specific percentage has been
     * applied to measuring its width or {@link #UNRESOLVED_DRAWER_WIDTH_PERCENT} if this cannot
     * be resolved before the layout direction resolved.
     */
    public float getLeftDrawerWidthPercent() {
        if ((mFlags & FLAG_DRAWER_WIDTH_PERCENTAGES_RESOLVED) == 0) {
            if ((mFlags & FLAG_SUPPORTS_RTL) == 0) {
                resolveDrawerWidthPercentages(ViewCompat.LAYOUT_DIRECTION_LTR, true);

            } else if ((mStartDrawerWidthPercent == UNDEFINED_DRAWER_WIDTH_PERCENT
                    && mEndDrawerWidthPercent == UNDEFINED_DRAWER_WIDTH_PERCENT)) {
                return mLeftDrawerWidthPercent == UNDEFINED_DRAWER_WIDTH_PERCENT ?
                        UNSPECIFIED_DRAWER_WIDTH_PERCENT : mLeftDrawerWidthPercent;

            } else if (!resolveDrawerWidthPercentagesIfDirectionResolved(true)) {
                return UNRESOLVED_DRAWER_WIDTH_PERCENT;
            }
        }
        return mLeftDrawerWidthPercent;
    }

    /**
     * Sets the percentage of the width of the left drawer relative to current view's within
     * the range from {@value MINIMUM_DRAWER_WIDTH_PERCENT} to {@value #MAXIMUM_DRAWER_WIDTH_PERCENT}
     * or pass in {@link #UNSPECIFIED_DRAWER_WIDTH_PERCENT} to ignore it to use the usual measurement
     * with a valid width defined for that drawer such as {@link ViewGroup.LayoutParams#WRAP_CONTENT},
     * {@link ViewGroup.LayoutParams#MATCH_PARENT}.
     *
     * @throws IllegalArgumentException if the provided argument <code>percent</code> is outside of
     *                                  the above mentioned.
     */
    public void setLeftDrawerWidthPercent(float percent) {
        checkDrawerWidthPercent(percent, true);
        if (mLeftDrawerWidthPercent != percent) {
            mLeftDrawerWidthPercent = percent;

            if (mLeftDrawer != null) {
                requestLayout();
            }
        }
    }

    /**
     * @return the percentage of the width of the right drawer relative to current view's within
     * the range from {@value MINIMUM_DRAWER_WIDTH_PERCENT} to {@value #MAXIMUM_DRAWER_WIDTH_PERCENT}
     * or just {@link #UNSPECIFIED_DRAWER_WIDTH_PERCENT} if no specific percentage has been
     * applied to measuring its width or {@link #UNRESOLVED_DRAWER_WIDTH_PERCENT} if this cannot
     * be resolved before the layout direction resolved.
     */
    public float getRightDrawerWidthPercent() {
        if ((mFlags & FLAG_DRAWER_WIDTH_PERCENTAGES_RESOLVED) == 0) {
            if ((mFlags & FLAG_SUPPORTS_RTL) == 0) {
                resolveDrawerWidthPercentages(ViewCompat.LAYOUT_DIRECTION_LTR, true);

            } else if ((mStartDrawerWidthPercent == UNDEFINED_DRAWER_WIDTH_PERCENT
                    && mEndDrawerWidthPercent == UNDEFINED_DRAWER_WIDTH_PERCENT)) {
                return mRightDrawerWidthPercent == UNDEFINED_DRAWER_WIDTH_PERCENT ?
                        UNSPECIFIED_DRAWER_WIDTH_PERCENT : mRightDrawerWidthPercent;

            } else if (!resolveDrawerWidthPercentagesIfDirectionResolved(true)) {
                return UNRESOLVED_DRAWER_WIDTH_PERCENT;
            }
        }
        return mRightDrawerWidthPercent;
    }

    /**
     * Sets the percentage of the width of the right drawer relative to current view's within
     * the range from {@value MINIMUM_DRAWER_WIDTH_PERCENT} to {@value #MAXIMUM_DRAWER_WIDTH_PERCENT}
     * or pass in {@link #UNSPECIFIED_DRAWER_WIDTH_PERCENT} to ignore it to use the usual measurement
     * with a valid width defined for that drawer such as {@link ViewGroup.LayoutParams#WRAP_CONTENT},
     * {@link ViewGroup.LayoutParams#MATCH_PARENT}.
     *
     * @throws IllegalArgumentException if the provided argument <code>percent</code> is outside of
     *                                  the above mentioned.
     */
    public void setRightDrawerWidthPercent(float percent) {
        checkDrawerWidthPercent(percent, true);
        if (mRightDrawerWidthPercent != percent) {
            mRightDrawerWidthPercent = percent;

            if (mRightDrawer != null) {
                requestLayout();
            }
        }
    }

    /**
     * @return the width percentage of the start drawer depending on this view's resolved
     * layout direction or just {@link #UNSPECIFIED_DRAWER_WIDTH_PERCENT} if no specific percentage
     * has been applied to measuring its width or {@link #UNRESOLVED_DRAWER_WIDTH_PERCENT} if
     * this cannot be resolved before the layout direction resolved.
     */
    public float getStartDrawerWidthPercent() {
        final int layoutDirection = ViewCompat.getLayoutDirection(this);

        if ((mFlags & FLAG_DRAWER_WIDTH_PERCENTAGES_RESOLVED) == 0) {
            if ((mFlags & FLAG_SUPPORTS_RTL) == 0 || Utils.isLayoutDirectionResolved(this)) {
                resolveDrawerWidthPercentages(layoutDirection, true);
            } else {
                return mStartDrawerWidthPercent == UNDEFINED_DRAWER_WIDTH_PERCENT ?
                        UNRESOLVED_DRAWER_WIDTH_PERCENT : mStartDrawerWidthPercent;
            }
        }
        return layoutDirection == LAYOUT_DIRECTION_LTR ?
                mLeftDrawerWidthPercent : mRightDrawerWidthPercent;
    }

    /**
     * Sets the width percentage (from {@link #MINIMUM_DRAWER_WIDTH_PERCENT} to
     * {@link #MAXIMUM_DRAWER_WIDTH_PERCENT}) for the start drawer depending on this view's resolved
     * layout direction or pass in {@link #UNSPECIFIED_DRAWER_WIDTH_PERCENT} to ignore it
     * to use the usual measurement with a valid width defined for that drawer such as
     * {@link ViewGroup.LayoutParams#WRAP_CONTENT}, {@link ViewGroup.LayoutParams#MATCH_PARENT}.
     *
     * @throws IllegalArgumentException if the provided argument <code>percent</code> is outside of
     *                                  the above mentioned.
     */
    public void setStartDrawerWidthPercent(float percent) {
        checkDrawerWidthPercent(percent, true);

        mStartDrawerWidthPercent = percent;
        mFlags &= ~(FLAG_DRAWER_WIDTH_PERCENTAGES_RESOLVED
                | FLAG_START_DRAWER_WIDTH_PERCENTAGE_RESOLVED);
        resolveDrawerWidthPercentagesIfDirectionResolved(false);
    }

    /**
     * @return the width percentage of the end drawer depending on this view's resolved
     * layout direction or just {@link #UNSPECIFIED_DRAWER_WIDTH_PERCENT} if no specific percentage
     * has been applied to measuring its width or {@link #UNRESOLVED_DRAWER_WIDTH_PERCENT} if
     * this cannot be resolved before the layout direction resolved.
     */
    public float getEndDrawerWidthPercent() {
        final int layoutDirection = ViewCompat.getLayoutDirection(this);

        if ((mFlags & FLAG_DRAWER_WIDTH_PERCENTAGES_RESOLVED) == 0) {
            if ((mFlags & FLAG_SUPPORTS_RTL) == 0 || Utils.isLayoutDirectionResolved(this)) {
                resolveDrawerWidthPercentages(layoutDirection, true);
            } else {
                return mEndDrawerWidthPercent == UNDEFINED_DRAWER_WIDTH_PERCENT ?
                        UNRESOLVED_DRAWER_WIDTH_PERCENT : mEndDrawerWidthPercent;
            }
        }
        return layoutDirection == LAYOUT_DIRECTION_LTR ?
                mRightDrawerWidthPercent : mLeftDrawerWidthPercent;
    }

    /**
     * Sets the width percentage (from {@link #MINIMUM_DRAWER_WIDTH_PERCENT} to
     * {@link #MAXIMUM_DRAWER_WIDTH_PERCENT}) for the end drawer depending on this view's resolved
     * layout direction or pass in {@link #UNSPECIFIED_DRAWER_WIDTH_PERCENT} to ignore it
     * to use the usual measurement with a valid width defined for that drawer such as
     * {@link ViewGroup.LayoutParams#WRAP_CONTENT}, {@link ViewGroup.LayoutParams#MATCH_PARENT}.
     *
     * @throws IllegalArgumentException if the provided argument <code>percent</code> is outside of
     *                                  the above mentioned.
     */
    public void setEndDrawerWidthPercent(float percent) {
        checkDrawerWidthPercent(percent, true);

        mEndDrawerWidthPercent = percent;
        mFlags &= ~(FLAG_DRAWER_WIDTH_PERCENTAGES_RESOLVED
                | FLAG_END_DRAWER_WIDTH_PERCENTAGE_RESOLVED);
        resolveDrawerWidthPercentagesIfDirectionResolved(false);
    }

    private void checkDrawerWidthPercent(float percent, boolean ignoreUndefined) {
        if (!ignoreUndefined && percent == UNDEFINED_DRAWER_WIDTH_PERCENT) {
            return;
        }
        if (percent != UNSPECIFIED_DRAWER_WIDTH_PERCENT
                && (percent < MINIMUM_DRAWER_WIDTH_PERCENT || percent > MAXIMUM_DRAWER_WIDTH_PERCENT)) {
            throw new IllegalArgumentException("Invalid percent for drawer's width. " +
                    "The value must be " + UNSPECIFIED_DRAWER_WIDTH_PERCENT + " or " +
                    "from " + MINIMUM_DRAWER_WIDTH_PERCENT + " to " + MAXIMUM_DRAWER_WIDTH_PERCENT +
                    ", but your is " + percent);
        }
    }

    /**
     * Enables the drawer on the specified side or not
     *
     * @see #setDrawerEnabled(View, boolean)
     */
    @SuppressLint("RtlHardcoded")
    public void setDrawerEnabled(@EdgeGravity int gravity, boolean enabled) {
        switch (gravity) {
            case Gravity.LEFT:
                if (enabled) {
                    mFlags |= FLAG_LEFT_DRAWER_ENABLED;
                } else {
                    mFlags &= ~FLAG_LEFT_DRAWER_ENABLED;
                }
                mFlags |= FLAG_LEFT_DRAWER_ABILITY_DEFINED;
                break;

            case Gravity.RIGHT:
                if (enabled) {
                    mFlags |= FLAG_RIGHT_DRAWER_ENABLED;
                } else {
                    mFlags &= ~FLAG_RIGHT_DRAWER_ENABLED;
                }
                mFlags |= FLAG_RIGHT_DRAWER_ABILITY_DEFINED;
                break;

            case GravityCompat.START:
                if (enabled) {
                    mFlags |= FLAG_START_DRAWER_ENABLED;
                } else {
                    mFlags &= ~FLAG_START_DRAWER_ENABLED;
                }
                //@formatter:off
                mFlags = (mFlags | FLAG_START_DRAWER_ABILITY_DEFINED)
                                 & ~(FLAG_DRAWER_ABILITIES_RESOLVED
                                        | FLAG_START_DRAWER_ABILITY_RESOLVED);
                //@formatter:on
                resolveDrawerAbilitiesIfDirectionResolved();
                break;

            case GravityCompat.END:
                if (enabled) {
                    mFlags |= FLAG_END_DRAWER_ENABLED;
                } else {
                    mFlags &= ~FLAG_END_DRAWER_ENABLED;
                }
                //@formatter:off
                mFlags = (mFlags | FLAG_END_DRAWER_ABILITY_DEFINED)
                                 & ~(FLAG_DRAWER_ABILITIES_RESOLVED
                                        | FLAG_END_DRAWER_ABILITY_RESOLVED);
                //@formatter:on
                resolveDrawerAbilitiesIfDirectionResolved();
                break;
        }
    }

    /**
     * Enables the given drawer or not
     *
     * @see #setDrawerEnabled(int, boolean)
     */
    @SuppressLint("RtlHardcoded")
    public void setDrawerEnabled(@Nullable View drawer, boolean enabled) {
        if (drawer == null) {
            return;
        }
        if (drawer == mLeftDrawer) {
            setDrawerEnabled(Gravity.LEFT, enabled);
        } else if (drawer == mRightDrawer) {
            setDrawerEnabled(Gravity.RIGHT, enabled);
        }
    }

    /**
     * @return whether the drawer on the specified side is enabled or not or the default
     * <code>true</code> if its ability cannot be resolved before the layout direction resolved.
     * @see #isDrawerEnabled(View)
     */
    @SuppressLint("RtlHardcoded")
    public boolean isDrawerEnabled(@EdgeGravity int gravity) {
        switch (gravity) {
            case Gravity.LEFT:
                return (mFlags & FLAG_LEFT_DRAWER_ENABLED) != 0;

            case Gravity.RIGHT:
                return (mFlags & FLAG_RIGHT_DRAWER_ENABLED) != 0;

            case GravityCompat.START: {
                final int layoutDirection = ViewCompat.getLayoutDirection(this);

                if ((mFlags & FLAG_DRAWER_ABILITIES_RESOLVED) == 0) {
                    if ((mFlags & FLAG_SUPPORTS_RTL) == 0 || Utils.isLayoutDirectionResolved(this)) {
                        resolveDrawerAbilities(layoutDirection);
                    } else {
                        return (mFlags & FLAG_START_DRAWER_ABILITY_DEFINED) == 0
                                || (mFlags & FLAG_START_DRAWER_ENABLED) != 0;
                    }
                }
                return isDrawerEnabled(layoutDirection == LAYOUT_DIRECTION_LTR ?
                        Gravity.LEFT : Gravity.RIGHT);
            }

            case GravityCompat.END: {
                final int layoutDirection = ViewCompat.getLayoutDirection(this);

                if ((mFlags & FLAG_DRAWER_ABILITIES_RESOLVED) == 0) {
                    if ((mFlags & FLAG_SUPPORTS_RTL) == 0 || Utils.isLayoutDirectionResolved(this)) {
                        resolveDrawerAbilities(layoutDirection);
                    } else {
                        return (mFlags & FLAG_END_DRAWER_ABILITY_DEFINED) == 0
                                || (mFlags & FLAG_END_DRAWER_ENABLED) != 0;
                    }
                }
                return isDrawerEnabled(layoutDirection == LAYOUT_DIRECTION_LTR ?
                        Gravity.RIGHT : Gravity.LEFT);
            }
        }
        return false;
    }

    /**
     * @return whether the given drawer is enabled or not
     * @see #isDrawerEnabled(int)
     */
    @SuppressLint("RtlHardcoded")
    public boolean isDrawerEnabled(@Nullable View drawer) {
        if (drawer != null) {
            if (drawer == mLeftDrawer) {
                return isDrawerEnabled(Gravity.LEFT);
            }
            if (drawer == mRightDrawer) {
                return isDrawerEnabled(Gravity.RIGHT);
            }
        }
        return false;
    }

    /**
     * Returns whether the drawer on the specified side can be dragged by user or not. If you call
     * this method before the first layout measurement, at which moment the drawer's slidability
     * has not been resolved, then the default <code>false</code> is returned.
     * <p>
     * This is determined by both the drawer's ability and whether it is contained and has been laid
     * by the current view.
     *
     * @see #isDrawerSlidable(View)
     */
    @SuppressLint("RtlHardcoded")
    public boolean isDrawerSlidable(@EdgeGravity int gravity) {
        switch (gravity) {
            case Gravity.LEFT:
                if (mLeftDrawer instanceof ViewStub) {
                    return (mFlags & FLAG_LEFT_DRAWER_ENABLED) != 0;
                }
                return (mFlags & FLAG_LEFT_DRAWER_ENABLED) != 0
                        && (mFlags & FLAG_LEFT_DRAWER_IN_LAYOUT) != 0;

            case Gravity.RIGHT:
                if (mRightDrawer instanceof ViewStub) {
                    return (mFlags & FLAG_RIGHT_DRAWER_ENABLED) != 0;
                }
                return (mFlags & FLAG_RIGHT_DRAWER_ENABLED) != 0
                        && (mFlags & FLAG_RIGHT_DRAWER_IN_LAYOUT) != 0;

            case GravityCompat.START:
            case GravityCompat.END:
                return isDrawerSlidable(Utils.getAbsoluteHorizontalGravity(this, gravity));
        }
        return false;
    }

    /**
     * @return whether the given drawer can be dragged by user or not.
     * @see #isDrawerSlidable(int)
     */
    @SuppressLint("RtlHardcoded")
    public boolean isDrawerSlidable(@Nullable View drawer) {
        if (drawer != null) {
            if (drawer == mLeftDrawer) {
                return isDrawerSlidable(Gravity.LEFT);
            }
            if (drawer == mRightDrawer) {
                return isDrawerSlidable(Gravity.RIGHT);
            }
        }
        return false;
    }

    /**
     * @return the current state of the dragged drawer's scrolling, maybe one of
     * {@link #SCROLL_STATE_IDLE},
     * {@link #SCROLL_STATE_TOUCH_SCROLL},
     * {@link #SCROLL_STATE_AUTO_SCROLL}
     */
    @SuppressLint("WrongConstant")
    @ScrollState
    public int getScrollState() {
        return mFlags & SCROLL_STATE_MASK;
    }

    /**
     * @return the current scroll percentage of the drawer being dragged
     */
    @FloatRange(from = 0.0f, to = 1.0f)
    public float getScrollPercent() {
        return mScrollPercent;
    }

    /**
     * @return <code>true</code> if some drawer is open
     */
    public boolean hasDrawerOpen() {
        return mScrollPercent > 0;
    }

    /**
     * @return whether all drawers have been fully closed or not
     */
    public boolean areDrawersClosed() {
        return mScrollPercent == 0;
    }

    /**
     * Gets the lasting time of the animator for opening/closing the drawers.
     * The default duration is {@value DEFAULT_DURATION} milliseconds.
     *
     * @return the duration of the animator
     */
    public int getDuration() {
        return mDuration;
    }

    /**
     * Sets the duration for the animator used to open/close the drawers.
     *
     * @throws IllegalArgumentException if a negative 'duration' is passed in
     */
    public void setDuration(int duration) {
        if (duration < 0) {
            throw new IllegalArgumentException("The animator for opening/closing the drawers " +
                    "cannot have negative duration: " + duration);
        }
        if (mDuration != duration) {
            if (mScrollDrawerAnimator != null) {
                mScrollDrawerAnimator.setDuration(mDuration);
            }
            mDuration = duration;
        }
    }

    /**
     * @return <code>true</code> if closing the drawer currently open is enabled.
     */
    public boolean isCloseOpenDrawerOnBackPressedEnabled() {
        return (mFlags & FLAG_CLOSE_OPEN_DRAWER_ON_BACK_PRESSED_ENABLED) != 0;
    }

    /**
     * Sets whether we should close the opened drawer as user presses the back key
     */
    public void setCloseOpenDrawerOnBackPressedEnabled(boolean enabled) {
        if (enabled) {
            mFlags |= FLAG_CLOSE_OPEN_DRAWER_ON_BACK_PRESSED_ENABLED;
        } else {
            mFlags &= ~FLAG_CLOSE_OPEN_DRAWER_ON_BACK_PRESSED_ENABLED;
        }
    }

    /**
     * @return the size of the touch-sensitive edges of the content view.
     * This is the range in pixels along the edges of content view for which edge tracking is
     * enabled to actively detect edge touches or drags.
     */
    public float getContentSensitiveEdgeSize() {
        return mContentSensitiveEdgeSize;
    }

    /**
     * Sets the size for the touch-sensitive edges of the content view.
     * This is the range in pixels along the edges of content view for which edge tracking is
     * enabled to actively detect edge touches or drags.
     *
     * @throws IllegalArgumentException if the provided argument <code>size</code> < 0
     */
    public void setContentSensitiveEdgeSize(float size) {
        if (size < 0) {
            throw new IllegalArgumentException("The size for the touch-sensitive edges " +
                    "of content view must >= 0, but your is " + size);
        }
        mContentSensitiveEdgeSize = size;
    }

    /**
     * @return the fade color used for the content view
     */
    @ColorInt
    public int getContentFadeColor() {
        return mContentFadeColor;
    }

    /**
     * Sets the fade color used for the content view to obscure primary content while
     * a drawer is open.
     */
    public void setContentFadeColor(@ColorInt int color) {
        if (mContentFadeColor != color) {
            mContentFadeColor = color;
            if (mScrollPercent > 0 &&
                    (mFlags & (FLAG_ANIMATING_DRAWER_OPENING | FLAG_ANIMATING_DRAWER_CLOSURE)) == 0) {
                invalidate();
            }
        }
    }

    private boolean resolveDrawerWidthPercentagesIfDirectionResolved(boolean preventLayout) {
        final boolean directionResolved = Utils.isLayoutDirectionResolved(this);
        if (directionResolved) {
            resolveDrawerWidthPercentages(ViewCompat.getLayoutDirection(this), preventLayout);
        }
        return directionResolved;
    }

    private void resolveDrawerWidthPercentages(int layoutDirection, boolean preventLayout) {
        if ((mFlags & FLAG_DRAWER_WIDTH_PERCENTAGES_RESOLVED) != 0) {
            return;
        }

        final float ldwp = mLeftDrawerWidthPercent;
        final float rdwp = mRightDrawerWidthPercent;

        if ((mFlags & FLAG_SUPPORTS_RTL) == 0) {
            // If left or right drawer width percentage is not defined and if we have the start
            // or end one defined then use the start or end percentage instead or else set it to
            // default 'UNSPECIFIED_DRAWER_WIDTH_PERCENT'.
            if (mLeftDrawerWidthPercent == UNDEFINED_DRAWER_WIDTH_PERCENT) {
                mLeftDrawerWidthPercent =
                        mStartDrawerWidthPercent == UNDEFINED_DRAWER_WIDTH_PERCENT ?
                                UNSPECIFIED_DRAWER_WIDTH_PERCENT : mStartDrawerWidthPercent;
            }
            if (mRightDrawerWidthPercent == UNDEFINED_DRAWER_WIDTH_PERCENT) {
                mRightDrawerWidthPercent =
                        mEndDrawerWidthPercent == UNDEFINED_DRAWER_WIDTH_PERCENT ?
                                UNSPECIFIED_DRAWER_WIDTH_PERCENT : mEndDrawerWidthPercent;
            }
        } else {
            final boolean ldrtl = layoutDirection == ViewCompat.LAYOUT_DIRECTION_RTL;

            if ((mFlags & FLAG_START_DRAWER_WIDTH_PERCENTAGE_RESOLVED) == 0) {
                if (mStartDrawerWidthPercent != UNDEFINED_DRAWER_WIDTH_PERCENT) {
                    if (ldrtl) {
                        mRightDrawerWidthPercent = mStartDrawerWidthPercent;
                    } else {
                        mLeftDrawerWidthPercent = mStartDrawerWidthPercent;
                    }
                } else {
                    if (ldrtl) {
                        if (mRightDrawerWidthPercent == UNDEFINED_DRAWER_WIDTH_PERCENT) {
                            mRightDrawerWidthPercent = UNSPECIFIED_DRAWER_WIDTH_PERCENT;
                        }
                    } else {
                        if (mLeftDrawerWidthPercent == UNDEFINED_DRAWER_WIDTH_PERCENT) {
                            mLeftDrawerWidthPercent = UNSPECIFIED_DRAWER_WIDTH_PERCENT;
                        }
                    }
                }
                mFlags |= FLAG_START_DRAWER_WIDTH_PERCENTAGE_RESOLVED;
            }
            if ((mFlags & FLAG_END_DRAWER_WIDTH_PERCENTAGE_RESOLVED) == 0) {
                if (mEndDrawerWidthPercent != UNDEFINED_DRAWER_WIDTH_PERCENT) {
                    if (ldrtl) {
                        mLeftDrawerWidthPercent = mEndDrawerWidthPercent;
                    } else {
                        mRightDrawerWidthPercent = mEndDrawerWidthPercent;
                    }
                } else {
                    if (ldrtl) {
                        if (mLeftDrawerWidthPercent == UNDEFINED_DRAWER_WIDTH_PERCENT) {
                            mLeftDrawerWidthPercent = UNSPECIFIED_DRAWER_WIDTH_PERCENT;
                        }
                    } else {
                        if (mRightDrawerWidthPercent == UNDEFINED_DRAWER_WIDTH_PERCENT) {
                            mRightDrawerWidthPercent = UNSPECIFIED_DRAWER_WIDTH_PERCENT;
                        }
                    }
                }
                mFlags |= FLAG_END_DRAWER_WIDTH_PERCENTAGE_RESOLVED;
            }
        }

        mFlags |= FLAG_DRAWER_WIDTH_PERCENTAGES_RESOLVED;

        if (!preventLayout) {
            boolean layoutRequestNeeded = false;
            if (mLeftDrawerWidthPercent != ldwp && mLeftDrawer != null) {
                layoutRequestNeeded = true;
            }
            if (mRightDrawerWidthPercent != rdwp && mRightDrawer != null) {
                layoutRequestNeeded = true;
            }
            if (layoutRequestNeeded) {
                requestLayout();
            }
        }
    }

    private void resolveDrawerAbilitiesIfDirectionResolved() {
        if (ViewCompat.isLayoutDirectionResolved(this)) {
            resolveDrawerAbilities(ViewCompat.getLayoutDirection(this));
        }
    }

    private void resolveDrawerAbilities(int layoutDirection) {
        if ((mFlags & FLAG_DRAWER_ABILITIES_RESOLVED) != 0) {
            return;
        }

        if ((mFlags & FLAG_SUPPORTS_RTL) == 0) {
            // If left or right drawer ability is not defined and if we have the start or end one
            // defined then use the start or end ability instead or else set it to default 'true'.
            if ((mFlags & FLAG_LEFT_DRAWER_ABILITY_DEFINED) == 0) {
                if ((mFlags & FLAG_START_DRAWER_ABILITY_DEFINED) != 0
                        && (mFlags & FLAG_START_DRAWER_ENABLED) == 0) {
                    mFlags &= ~FLAG_LEFT_DRAWER_ENABLED;
                } else {
                    mFlags |= FLAG_LEFT_DRAWER_ENABLED;
                }
            }
            if ((mFlags & FLAG_RIGHT_DRAWER_ABILITY_DEFINED) == 0) {
                if ((mFlags & FLAG_END_DRAWER_ABILITY_DEFINED) != 0
                        && (mFlags & FLAG_END_DRAWER_ENABLED) == 0) {
                    mFlags &= ~FLAG_RIGHT_DRAWER_ENABLED;
                } else {
                    mFlags |= FLAG_RIGHT_DRAWER_ENABLED;
                }
            }
        } else {
            final boolean ldrtl = layoutDirection == ViewCompat.LAYOUT_DIRECTION_RTL;

            if ((mFlags & FLAG_START_DRAWER_ABILITY_RESOLVED) == 0) {
                final int startDrawerEnabledFlag = ldrtl ?
                        FLAG_RIGHT_DRAWER_ENABLED : FLAG_LEFT_DRAWER_ENABLED;
                if ((mFlags & FLAG_START_DRAWER_ABILITY_DEFINED) != 0) {
                    if ((mFlags & FLAG_START_DRAWER_ENABLED) == 0) {
                        mFlags &= ~startDrawerEnabledFlag;
                    } else {
                        mFlags |= startDrawerEnabledFlag;
                    }
                } else {
                    if ((mFlags & (
                            ldrtl ? FLAG_RIGHT_DRAWER_ABILITY_DEFINED
                                    : FLAG_LEFT_DRAWER_ABILITY_DEFINED)) != 0
                            && (mFlags & startDrawerEnabledFlag) == 0) {
                        mFlags &= ~startDrawerEnabledFlag;
                    } else {
                        mFlags |= startDrawerEnabledFlag;
                    }
                }
                mFlags |= FLAG_START_DRAWER_ABILITY_RESOLVED;
            }
            if ((mFlags & FLAG_END_DRAWER_ABILITY_RESOLVED) == 0) {
                final int endDrawerEnabledFlag = ldrtl ?
                        FLAG_LEFT_DRAWER_ENABLED : FLAG_RIGHT_DRAWER_ENABLED;
                if ((mFlags & FLAG_END_DRAWER_ABILITY_DEFINED) != 0) {
                    if ((mFlags & FLAG_END_DRAWER_ENABLED) == 0) {
                        mFlags &= ~endDrawerEnabledFlag;
                    } else {
                        mFlags |= endDrawerEnabledFlag;
                    }
                } else {
                    if ((mFlags & (
                            ldrtl ? FLAG_LEFT_DRAWER_ABILITY_DEFINED
                                    : FLAG_RIGHT_DRAWER_ABILITY_DEFINED)) != 0
                            && (mFlags & endDrawerEnabledFlag) == 0) {
                        mFlags &= ~endDrawerEnabledFlag;
                    } else {
                        mFlags |= endDrawerEnabledFlag;
                    }
                }
                mFlags |= FLAG_END_DRAWER_ABILITY_RESOLVED;
            }
        }

        mFlags |= FLAG_DRAWER_ABILITIES_RESOLVED;
    }

    private void traverseAllChildren(int childCount, int layoutDirection) {
        if (childCount > 3) {
            throw new IllegalStateException("SlidingDrawerLayout can host only three direct children.");
        }

        mContentView = mLeftDrawer = mRightDrawer = null;
        mFlags &= ~(FLAG_LEFT_DRAWER_IN_LAYOUT | FLAG_RIGHT_DRAWER_IN_LAYOUT);
        switch (childCount) {
            case 1:
                mContentView = getChildAt(0);
                break;
            case 2:
                traverseAllChildren2(childCount, layoutDirection);
                if (mContentView == null) {
                    if (layoutDirection == LAYOUT_DIRECTION_LTR) {
                        mContentView = mRightDrawer;
                        mRightDrawer = null;
                    } else {
                        mContentView = mLeftDrawer;
                        mLeftDrawer = null;
                    }
                }

                if (mLeftDrawer == null && mRightDrawer == null) {
                    throw new IllegalStateException("Edge gravity in values Gravity#LEFT, " +
                            "Gravity#RIGHT, Gravity#START and Gravity#END must be set " +
                            "for the Drawer's LayoutParams to finalize the Drawer's placement.");
                }

                View drawer = null;
                if (mLeftDrawer != null) {
                    if (mLeftDrawer.getVisibility() != GONE) {
                        mFlags |= FLAG_LEFT_DRAWER_IN_LAYOUT;
                    }
                    drawer = mLeftDrawer;
                } else if (mRightDrawer != null) {
                    if (mRightDrawer.getVisibility() != GONE) {
                        mFlags |= FLAG_RIGHT_DRAWER_IN_LAYOUT;
                    }
                    drawer = mRightDrawer;
                }
                if (drawer != null && getChildAt(0) != drawer) {
                    detachViewFromParent(1);
                    attachViewToParent(drawer, 0, drawer.getLayoutParams());
                }
                break;
            case 3:
                traverseAllChildren2(childCount, layoutDirection);

                if (mLeftDrawer == null || mRightDrawer == null) {
                    throw new IllegalStateException("Different edge gravity in values Gravity#LEFT, " +
                            "Gravity#RIGHT, Gravity#START and Gravity#END must be set " +
                            "for Drawers' LayoutParams to finalize the Drawers' placements.");
                }

                if (mLeftDrawer.getVisibility() != GONE) {
                    mFlags |= FLAG_LEFT_DRAWER_IN_LAYOUT;
                }
                if (getChildAt(0) != mLeftDrawer) {
                    detachViewFromParent(mLeftDrawer);
                    attachViewToParent(mLeftDrawer, 0, mLeftDrawer.getLayoutParams());
                }

                if (mRightDrawer.getVisibility() != GONE) {
                    mFlags |= FLAG_RIGHT_DRAWER_IN_LAYOUT;
                }
                if (getChildAt(1) != mRightDrawer) {
                    detachViewFromParent(mRightDrawer);
                    attachViewToParent(mRightDrawer, 1, mRightDrawer.getLayoutParams());
                }
                break;
        }
        if (mShownDrawer != null && mShownDrawer != mLeftDrawer && mShownDrawer != mRightDrawer) {
            dispatchDrawerScrollPercentageChangeIfNeeded(0);
            switch (mFlags & SCROLL_STATE_MASK) {
                case SCROLL_STATE_TOUCH_SCROLL:
                    dispatchDrawerScrollStateChangeIfNeeded(SCROLL_STATE_IDLE);
                    resetTouch();
                    break;
                case SCROLL_STATE_AUTO_SCROLL:
                    mScrollDrawerAnimator.cancel();
                    break;
            }
        }
    }

    @SuppressLint("RtlHardcoded")
    private void traverseAllChildren2(int childCount, int layoutDirection) {
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            final int gravity = ((LayoutParams) child.getLayoutParams()).gravity;
            final int absGravity = GravityCompat.getAbsoluteGravity(gravity, layoutDirection);
            if ((absGravity & Gravity.LEFT) == Gravity.LEFT) {
                if (mLeftDrawer == null) {
                    mLeftDrawer = child;
                    continue;
                }
            } else if ((absGravity & Gravity.RIGHT) == Gravity.RIGHT) {
                if (mRightDrawer == null) {
                    mRightDrawer = child;
                    continue;
                }
            }
            mContentView = child;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int childCount = getChildCount();
        final int layoutDirection = ViewCompat.getLayoutDirection(this);

        resolveDrawerAbilities(layoutDirection);
        resolveDrawerWidthPercentages(layoutDirection, true);
        traverseAllChildren(childCount, layoutDirection);

        int maxHeight = 0;
        int maxWidth = 0;
        int childrenState = 0;

        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                measureChild(child, widthMeasureSpec, heightMeasureSpec);
                maxWidth = Math.max(maxWidth, child.getMeasuredWidth());
                maxHeight = Math.max(maxHeight, child.getMeasuredHeight());
                childrenState = combineMeasuredStates(childrenState, child.getMeasuredState());
            }
        }

        // Account for padding too
        maxWidth += getPaddingLeft() + getPaddingRight();
        maxHeight += getPaddingTop() + getPaddingBottom();

        // Check against our minimum height and width
        maxHeight = Math.max(maxHeight, getSuggestedMinimumHeight());
        maxWidth = Math.max(maxWidth, getSuggestedMinimumWidth());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Check against our foreground's minimum height and width
            Drawable drawable = getForeground();
            if (drawable != null) {
                maxHeight = Math.max(maxHeight, drawable.getMinimumHeight());
                maxWidth = Math.max(maxWidth, drawable.getMinimumWidth());
            }
        }

        setMeasuredDimension(resolveSizeAndState(maxWidth, widthMeasureSpec, childrenState),
                resolveSizeAndState(maxHeight, heightMeasureSpec,
                        childrenState << MEASURED_HEIGHT_STATE_SHIFT));
    }

    @Override
    protected void measureChild(View child, int parentWidthMeasureSpec,
                                int parentHeightMeasureSpec) {
        measureChildWithMargins(child,
                parentWidthMeasureSpec, 0,
                parentHeightMeasureSpec, 0);
    }

    @Override
    protected void measureChildWithMargins(View child, int parentWidthMeasureSpec,
                                           int widthUsed,
                                           int parentHeightMeasureSpec, int heightUsed) {
        // Child does not have any margin
        final int horizontalPaddings = getPaddingLeft() + getPaddingRight() + widthUsed;
        final int verticalPaddings = getPaddingTop() + getPaddingBottom() + heightUsed;

        int childWidthMeasureSpec = getChildMeasureSpec(parentWidthMeasureSpec,
                horizontalPaddings, child.getLayoutParams().width);
        final int childHeightMeasureSpec = getChildMeasureSpec(parentHeightMeasureSpec,
                verticalPaddings, child.getLayoutParams().height);

        if (child != mContentView) {
            final int availableWidth = MeasureSpec.getSize(parentWidthMeasureSpec) - horizontalPaddings;

            float drawerWidthPercent = child == mLeftDrawer ?
                    mLeftDrawerWidthPercent : mRightDrawerWidthPercent;
            if (drawerWidthPercent == UNSPECIFIED_DRAWER_WIDTH_PERCENT) {
                final int minChildWidth = (int) (availableWidth * MINIMUM_DRAWER_WIDTH_PERCENT + 0.5f);
                final int maxChildWidth = (int) (availableWidth * MAXIMUM_DRAWER_WIDTH_PERCENT + 0.5f);

                final int childMeasuredWidth = MeasureSpec.getSize(childWidthMeasureSpec);
                final int newChildMeasuredWidth = Math.min(
                        Math.max(childMeasuredWidth, minChildWidth), maxChildWidth);

                if (newChildMeasuredWidth != childMeasuredWidth) {
                    childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(
                            newChildMeasuredWidth, MeasureSpec.EXACTLY);
                }
            } else {
                childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(
                        (int) (availableWidth * drawerWidthPercent + 0.5f), MeasureSpec.EXACTLY);
            }
        }

        child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
    }

    @SuppressLint("RtlHardcoded")
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        // Delays the running animation to ensure the active drawer will open or close normally
        if (mScrollDrawerAnimator != null && mScrollDrawerAnimator.isRunning()) {
            final int flags = mFlags;
            final View activeDrawer = mShownDrawer;
            mScrollDrawerAnimator.cancel();
            mShownDrawer = activeDrawer;
            mFlags = flags;

            for (Runnable r : mScheduledOpenDrawerRunnables) {
                removeCallbacks(r);
            }
            mScheduledOpenDrawerRunnables.clear();

            Runnable r = new Runnable() {
                @Override
                public void run() {
                    mScheduledOpenDrawerRunnables.remove(this);

                    if ((mFlags & FLAG_ANIMATING_DRAWER_OPENING) != 0) {
                        openDrawer(activeDrawer, true);

                    } else if (activeDrawer == mShownDrawer) {
                        closeDrawer(true);
                    }
                }
            };
            mScheduledOpenDrawerRunnables.add(r);
            post(r);
        }

        final int parentLeft = getPaddingLeft();
        final int parentRight = right - left - getPaddingRight();
        final int parentTop = getPaddingTop();
        final int parentBottom = bottom - top - getPaddingBottom();

        final int parentWidth = parentRight - parentLeft;
        final int parentHeight = parentBottom - parentTop;

        final int layoutDirection = ViewCompat.getLayoutDirection(this);

        final int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }

            LayoutParams lp = (LayoutParams) child.getLayoutParams();

            final int childWidth = child.getMeasuredWidth();
            final int childHeight = child.getMeasuredHeight();

            int childLeft;
            int childTop;

            final int horizontalGravity = GravityCompat.getAbsoluteGravity(
                    lp.gravity, layoutDirection) & Gravity.HORIZONTAL_GRAVITY_MASK;
            if (child == mContentView) {
                switch (horizontalGravity) {
                    case Gravity.LEFT:
                        lp.startLeft = parentLeft;
                        break;
                    case Gravity.RIGHT:
                        lp.startLeft = parentRight - childWidth;
                        break;
                    case Gravity.CENTER_HORIZONTAL:
                    default:
                        lp.startLeft = parentLeft + (parentWidth - childWidth) / 2f;
                        break;
                }
                if (mShownDrawer == null) {
                    lp.left = lp.startLeft;
                } else {
                    // Its finalLeft may need to be recalculated if the shown drawer's width changes.
                    lp.finalLeft = lp.startLeft + (mShownDrawer == mLeftDrawer ?
                            mShownDrawer.getMeasuredWidth() : -mShownDrawer.getMeasuredWidth());

                    lp.left = lp.startLeft + (lp.finalLeft - lp.startLeft) * mScrollPercent;

                    // Also its horizontal scrolled position should be invalidated if the
                    // shown drawer's width changes or else this call to updateChildTranslationX()
                    // will do nothing.
                    updateChildTranslationX(child, lp.left - lp.startLeft);
                }

                childLeft = Math.round(lp.startLeft);
            } else {
                final float offset = childWidth / SCROLL_RATIO_CONTENT_TO_DRAWER;

                switch (horizontalGravity) {
                    case Gravity.LEFT:
                        lp.finalLeft = parentLeft;
                        lp.startLeft = lp.finalLeft - offset;
                        break;
                    case Gravity.RIGHT:
                        lp.finalLeft = parentRight - childWidth;
                        lp.startLeft = lp.finalLeft + offset;
                        break;
                }
                if (child == mShownDrawer) {
                    lp.left = lp.startLeft + (lp.finalLeft - lp.startLeft) * mScrollPercent;
                    // Changes its horizontal scrolled position if its width changes.
                    updateChildTranslationX(child, lp.left - lp.startLeft);
                } else {
                    lp.left = lp.startLeft;
                }

                childLeft = Math.round(lp.startLeft);
            }

            final int verticalGravity = lp.gravity == Gravity.NO_GRAVITY ?
                    Gravity.CENTER_VERTICAL : lp.gravity & Gravity.VERTICAL_GRAVITY_MASK;
            switch (verticalGravity) {
                case Gravity.TOP:
                    childTop = parentTop;
                    break;
                case Gravity.BOTTOM:
                    childTop = parentBottom - childHeight;
                    break;
                case Gravity.CENTER_VERTICAL:
                default:
                    childTop = (int) (parentTop + (parentHeight - childHeight) / 2f);
                    break;
            }

            child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight);
        }
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (child != mContentView && child != mShownDrawer) {
            return false;
        }

        final int left = getPaddingLeft();
        final int right = getWidth() - getPaddingRight();
        final int top = getPaddingTop();
        final int bottom = getHeight() - getPaddingBottom();

        LayoutParams lp = (LayoutParams) mContentView.getLayoutParams();
        final float horizontalOffset = Math.round(lp.left - lp.startLeft);

        if (child == mShownDrawer) {
            final int save = canvas.save();

            if (child == mLeftDrawer) {
                canvas.clipRect(left, top, left + horizontalOffset, bottom);
            } else {
                canvas.clipRect(right + horizontalOffset, top, right, bottom);
            }

            try {
                return super.drawChild(canvas, child, drawingTime);
            } finally {
                canvas.restoreToCount(save);
            }

        } else {
            final boolean issued = super.drawChild(canvas, child, drawingTime);

            // Draw the content view's fading
            if (mScrollPercent > 0) {
                final int baseAlpha = (mContentFadeColor & 0xFF000000) >>> 24;
                final int alpha = (int) ((float) baseAlpha * mScrollPercent + 0.5f);
                final int color = alpha << 24 | (mContentFadeColor & 0x00FFFFFF);

                canvas.save();
                if (mShownDrawer == mLeftDrawer) {
                    canvas.clipRect(left + horizontalOffset, top, right, bottom);
                } else {
                    canvas.clipRect(left, top, right + horizontalOffset, bottom);
                }
                canvas.drawColor(color);
                canvas.restore();
            }

            return issued;
        }
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams lp) {
        return lp instanceof LayoutParams;
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    @Override
    protected LayoutParams generateLayoutParams(ViewGroup.LayoutParams lp) {
        if (lp instanceof LayoutParams) {
            return new LayoutParams((LayoutParams) lp);
        }
        return new LayoutParams(lp);
    }

    public static class LayoutParams extends ViewGroup.LayoutParams {

        /**
         * The gravity to apply with the View to which these layout parameters are associated.
         * The default value is {@link Gravity#NO_GRAVITY}.
         */
        public int gravity = Gravity.NO_GRAVITY;

        /**
         * The initial position of the left of the View to which these layout parameters belong,
         * as computed in this view's {@link #onLayout(boolean, int, int, int, int)} method.
         */
        private float startLeft;

        /**
         * To a drawer: The position for its left to reach when it is completely opened.
         * To content view: The position for its left to reach when some drawer is completely opened.
         */
        private float finalLeft;

        /** The current left of the View these layout parameters associated to. */
        private float left;

        public LayoutParams(@NonNull Context c, @Nullable AttributeSet attrs) {
            super(c, attrs);
            TypedArray a = c.obtainStyledAttributes(attrs, R.styleable.SlidingDrawerLayout_Layout);
            gravity = a.getInt(R.styleable.SlidingDrawerLayout_Layout_android_layout_gravity,
                    Gravity.NO_GRAVITY);
            a.recycle();
        }

        public LayoutParams(int width, int height) {
            super(width, height);
        }

        /**
         * Creates a new set of layout parameters with the specified width, height and weight.
         *
         * @param width   the width, either {@link #MATCH_PARENT},
         *                {@link #WRAP_CONTENT} or a fixed size in pixels
         * @param height  the height, either {@link #MATCH_PARENT},
         *                {@link #WRAP_CONTENT} or a fixed size in pixels
         * @param gravity the gravity
         */
        public LayoutParams(int width, int height, int gravity) {
            super(width, height);
            this.gravity = gravity;
        }

        public LayoutParams(@NonNull ViewGroup.LayoutParams source) {
            super(source);
        }

        /**
         * Copy constructor. Clone the width, height, and gravity of the source.
         *
         * @param source The layout params to copy from
         */
        public LayoutParams(@NonNull LayoutParams source) {
            super(source);
            this.gravity = source.gravity;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        cancelRunningAnimatorAndPendingActions();
    }

    private void cancelRunningAnimatorAndPendingActions() {
        if (mOpenStubDrawerRunnable != null) {
            mOpenStubDrawerRunnable.removeFromCallbacks();
        }
        if (mScheduledOpenDrawerRunnables.size() > 0) {
            for (Runnable r : mScheduledOpenDrawerRunnables) {
                removeCallbacks(r);
            }
            mScheduledOpenDrawerRunnables.clear();
        }
        if (mScrollDrawerAnimator != null && mScrollDrawerAnimator.isRunning()) {
            mScrollDrawerAnimator.cancel();
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!checkDrawerSlidability()) {
            return super.onInterceptTouchEvent(ev);
        }

        switch (ev.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                onPointerDown(ev);
                mFlags &= ~FLAG_FINGER_DOWNS_ON_CONTENT_WHEN_DRAWER_IS_OPEN;

                if (mScrollPercent > 0) {
                    final int left = getPaddingLeft();
                    final int right = getWidth() - getPaddingRight();

                    LayoutParams lp = (LayoutParams) mContentView.getLayoutParams();
                    final float horizontalOffset = lp.left - lp.startLeft;

                    if (mShownDrawer == mLeftDrawer) {
                        if (mDownX > left + horizontalOffset) {
                            if (mDownX <= right) {
                                mFlags |= FLAG_FINGER_DOWNS_ON_CONTENT_WHEN_DRAWER_IS_OPEN;
                            }
                            return true;
                        }
                    } else if (mShownDrawer == mRightDrawer) {
                        if (mDownX < right + horizontalOffset) {
                            if (mDownX >= left) {
                                mFlags |= FLAG_FINGER_DOWNS_ON_CONTENT_WHEN_DRAWER_IS_OPEN;
                            }
                            return true;
                        }
                    }
                    return mScrollPercent != 1;
                }
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                onPointerDown(ev);
                break;

            case MotionEvent.ACTION_MOVE:
                if (!onPointerMove(ev)) {
                    break;
                }
                return tryHandleSlidingEvent();

            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                resetTouch();
                break;
        }
        return false;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!checkDrawerSlidability()) {
            return super.onTouchEvent(event);
        }

        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_POINTER_DOWN:
                onPointerDown(event);
                break;

            case MotionEvent.ACTION_MOVE:
                if (!onPointerMove(event)) {
                    return false;
                }

                if ((mFlags & SCROLL_STATE_MASK) == SCROLL_STATE_TOUCH_SCROLL) {
                    cancelRunningAnimatorAndPendingActions();

                    if (mVelocityTracker == null)
                        mVelocityTracker = VelocityTracker.obtain();
                    mVelocityTracker.addMovement(event);

                    LayoutParams lp = (LayoutParams) mShownDrawer.getLayoutParams();

                    float dx = (mTouchX[mTouchX.length - 1] - mTouchX[mTouchX.length - 2])
                            / SCROLL_RATIO_CONTENT_TO_DRAWER;
                    // To drawer at the start edge:
                    // the maximum distance the content view can scroll towards the end
                    // of horizontal cannot be greater than the width of the drawer.
                    if (mShownDrawer == mLeftDrawer && lp.left + dx > lp.finalLeft
                            || mShownDrawer == mRightDrawer && lp.left + dx < lp.finalLeft) {

                        dx = lp.finalLeft - lp.left;
                        // To drawer at the start edge:
                        // The drawer can't continue scrolling towards the beginning
                        // of horizontal after returning to its original position.
                    } else if (mShownDrawer == mLeftDrawer && lp.left + dx < lp.startLeft
                            || mShownDrawer == mRightDrawer && lp.left + dx > lp.startLeft) {

                        dx = lp.startLeft - lp.left;
                    }
                    scrollDrawerBy(mShownDrawer, dx);
                    break;
                }

                // Check whether we should handle the subsequent touch events after requiring
                // to intercept them on down event as the user slides the drawer.
                tryHandleSlidingEvent();
                break;

            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(event);
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                try {
                    if ((mFlags & SCROLL_STATE_MASK) == SCROLL_STATE_TOUCH_SCROLL) {
                        if (mScrollPercent == 1 || mScrollPercent == 0) {
                            dispatchDrawerScrollStateChangeIfNeeded(SCROLL_STATE_IDLE);
                            break;
                        }

                        if (mVelocityTracker != null) {
                            mVelocityTracker.computeCurrentVelocity(1000);
                            final float xVel = mVelocityTracker.getXVelocity(mActivePointerId);
                            final float maxVelX = mAutoScrollDrawerMinimumVelocityX;
                            if (mShownDrawer == mLeftDrawer && xVel >= maxVelX
                                    || mShownDrawer == mRightDrawer && xVel <= -maxVelX) {
                                openDrawerInternal(mShownDrawer, true);
                                break;
                            } else if (mShownDrawer == mLeftDrawer && xVel <= -maxVelX
                                    || mShownDrawer == mRightDrawer && xVel >= maxVelX) {
                                closeDrawer(true);
                                break;
                            }
                        }

                        if (mScrollPercent >= 0.5f) {
                            openDrawerInternal(mShownDrawer, true);
                        } else {
                            closeDrawer(true);
                        }

                        // Close the shown drawer even if it is being animated as user clicks
                        // the content area
                    } else if ((mFlags & FLAG_FINGER_DOWNS_ON_CONTENT_WHEN_DRAWER_IS_OPEN) != 0) {
                        closeDrawer(true);
                    }
                } finally {
                    resetTouch();
                }
                break;
        }
        return true;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean checkDrawerSlidability() {
        if (mShownDrawer == null) {
            if (!isDrawerSlidable(mLeftDrawer) && !isDrawerSlidable(mRightDrawer)) {
                resetTouch();
                return false;
            }
        } else if (!isDrawerSlidable(mShownDrawer)) {
            closeDrawer(true);
            resetTouch();
            return false;
        }
        return true;
    }

    private boolean tryHandleSlidingEvent() {
        boolean handle = false;

        final float dx = mTouchX[mTouchX.length - 1] - mDownX;
        final float absDX = Math.abs(dx);
        final float absDY = Math.abs(mTouchY[mTouchY.length - 1] - mDownY);

        if (mLeftDrawer != null && (mShownDrawer == null || mShownDrawer == mLeftDrawer)) {
            final int left = getPaddingLeft();
            if (mScrollPercent == 0) {
                if (mDownX >= left && mDownX <= left + mContentSensitiveEdgeSize) {
                    handle = dx > absDY && dx > mTouchSlop;
                }
            } else if (mScrollPercent == 1 &&
                    mDownX <= ((LayoutParams) mContentView.getLayoutParams()).finalLeft) {
                handle = dx < -absDY && dx < -mTouchSlop;
            } else {
                handle = absDX > absDY && absDX > mTouchSlop;
            }
            if (handle) {
                if (mLeftDrawer instanceof ViewStub) {
                    mLeftDrawer = inflateStubDrawer((ViewStub) mLeftDrawer);
                }
                mShownDrawer = mLeftDrawer;
            }
        }
        if (mRightDrawer != null && (mShownDrawer == null || mShownDrawer == mRightDrawer)) {
            final int right = getWidth() - getPaddingRight();
            if (mScrollPercent == 0) {
                if (mDownX >= right - mContentSensitiveEdgeSize && mDownX <= right) {
                    handle = dx < -absDY && dx < -mTouchSlop;
                }
            } else if (mScrollPercent == 1 &&
                    mDownX >= ((LayoutParams) mContentView.getLayoutParams()).finalLeft) {
                handle = dx > absDY && dx > mTouchSlop;
            } else {
                handle = absDX > absDY && absDX > mTouchSlop;
            }
            if (handle) {
                if (mRightDrawer instanceof ViewStub) {
                    mRightDrawer = inflateStubDrawer((ViewStub) mRightDrawer);
                }
                mShownDrawer = mRightDrawer;
            }
        }

        if (handle) {
            ViewParent parent = getParent();
            if (parent != null) {
                parent.requestDisallowInterceptTouchEvent(true);
            }

            dispatchDrawerScrollStateChangeIfNeeded(SCROLL_STATE_TOUCH_SCROLL);
        }
        return handle;
    }

    private View inflateStubDrawer(ViewStub stub) {
        final int layoutResource = stub.getLayoutResource();
        if (layoutResource != 0) {
            LayoutInflater inflater = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                inflater = stub.getLayoutInflater();
            }
            if (inflater == null) {
                inflater = LayoutInflater.from(getContext());
            }
            final View view = inflater.inflate(layoutResource, this, false);
            final int inflatedId = stub.getInflatedId();
            if (inflatedId != NO_ID) {
                view.setId(inflatedId);
            }

            final int index = indexOfChild(stub);
            detachViewFromParent(index);
            addView(view, index, stub.getLayoutParams());

            return view;
        } else {
            throw new IllegalStateException("ViewStub " + stub + " must have a valid layoutResource");
        }
    }

    private void onPointerDown(MotionEvent e) {
        final int actionIndex = e.getActionIndex();
        mActivePointerId = e.getPointerId(actionIndex);
        mDownX = e.getX(actionIndex);
        mDownY = e.getY(actionIndex);
        markCurrTouchPoint(mDownX, mDownY);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean onPointerMove(MotionEvent e) {
        final int pointerIndex = e.findPointerIndex(mActivePointerId);
        if (pointerIndex < 0) {
            Log.e(TAG, "Error processing scroll; pointer index for id "
                    + mActivePointerId + " not found. Did any MotionEvents get skipped?");
            return false;
        }
        markCurrTouchPoint(e.getX(pointerIndex), e.getY(pointerIndex));
        return true;
    }

    private void onSecondaryPointerUp(MotionEvent e) {
        final int pointerIndex = e.getActionIndex();
        final int pointerId = e.getPointerId(pointerIndex);
        if (pointerId == mActivePointerId) {
            // This was our active pointer going up.
            // Choose a new active pointer and adjust accordingly.
            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            mActivePointerId = e.getPointerId(newPointerIndex);
            mDownX = e.getX(newPointerIndex);
            mDownY = e.getY(newPointerIndex);
            markCurrTouchPoint(mDownX, mDownY);
        }
    }

    private void markCurrTouchPoint(float x, float y) {
        System.arraycopy(mTouchX, 1, mTouchX, 0, mTouchX.length - 1);
        mTouchX[mTouchX.length - 1] = x;
        System.arraycopy(mTouchY, 1, mTouchY, 0, mTouchY.length - 1);
        mTouchY[mTouchY.length - 1] = y;
    }

    private void resetTouch() {
        mActivePointerId = ViewDragHelper.INVALID_POINTER;
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && mShownDrawer != null) {
            event.startTracking();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK
                && (mFlags & FLAG_CLOSE_OPEN_DRAWER_ON_BACK_PRESSED_ENABLED) != 0) {
            closeDrawer(true);
            return mShownDrawer != null;
        }
        return super.onKeyUp(keyCode, event);
    }

    /**
     * Automatically open the drawer on the specified side.
     *
     * @param animate smoothly open it through animator or not
     * @see #openDrawer(View, boolean)
     * @see #closeDrawer(boolean)
     */
    @SuppressLint("RtlHardcoded")
    public void openDrawer(@EdgeGravity int gravity, boolean animate) {
        final int absoluteGravity = GravityCompat.getAbsoluteGravity(gravity,
                ViewCompat.getLayoutDirection(this));
        switch (absoluteGravity) {
            case Gravity.LEFT:
                openDrawer(mLeftDrawer, animate);
                break;
            case Gravity.RIGHT:
                openDrawer(mRightDrawer, animate);
                break;
        }
    }

    /**
     * Automatically open the given drawer.
     * <p>
     * <strong>NOTE:</strong> This will only work if there is no drawer open or the drawer
     * is the one currently being dragged.
     *
     * @param animate smoothly open it through animator or not
     * @see #openDrawer(int, boolean)
     * @see #closeDrawer(boolean)
     */
    public void openDrawer(@Nullable View drawer, boolean animate) {
        if (drawer == null) return;

        if (mShownDrawer == null) {
            if (drawer == mLeftDrawer || drawer == mRightDrawer) {
                if (drawer instanceof ViewStub) {
                    drawer = inflateStubDrawer((ViewStub) drawer);

                    if (mOpenStubDrawerRunnable != null) {
                        mOpenStubDrawerRunnable.removeFromCallbacks();
                    }
                    mOpenStubDrawerRunnable = new OpenStubDrawerRunnable(drawer, animate);
                    post(mOpenStubDrawerRunnable);

                } else {
                    if (mOpenStubDrawerRunnable != null) {
                        if (mOpenStubDrawerRunnable.drawer == drawer) {
                            return;
                        }
                        mOpenStubDrawerRunnable.removeFromCallbacks();
                    }
                    openDrawerInternal(drawer, animate);
                }
            }
        } else if (mShownDrawer == drawer) {
            openDrawerInternal(drawer, animate);

        } else if (drawer == mLeftDrawer) {
            Log.w(TAG, "Can't open the left drawer while the right is open.");
        } else if (drawer == mRightDrawer) {
            Log.w(TAG, "Can't open the right drawer while the left is open.");
        }
    }

    private void openDrawerInternal(View drawer, boolean animate) {
        if (drawer == mLeftDrawer && (mFlags & FLAG_LEFT_DRAWER_IN_LAYOUT) != 0
                || drawer == mRightDrawer && (mFlags & FLAG_RIGHT_DRAWER_IN_LAYOUT) != 0) {
            mShownDrawer = drawer;
            final float finalLeft = ((LayoutParams) drawer.getLayoutParams()).finalLeft;
            if (animate) {
                if (smoothScrollDrawerTo(drawer, finalLeft)) {
                    mFlags |= FLAG_ANIMATING_DRAWER_OPENING;
                    mFlags &= ~FLAG_ANIMATING_DRAWER_CLOSURE;
                }
            } else {
                scrollDrawerTo(drawer, finalLeft);
            }
        }
    }

    /**
     * Automatically close the opened drawer.
     *
     * @param animate to do that through animator or not
     * @see #openDrawer(int, boolean)
     * @see #openDrawer(View, boolean)
     */
    public void closeDrawer(boolean animate) {
        if (mShownDrawer != null) {
            final float startLeft = ((LayoutParams) mShownDrawer.getLayoutParams()).startLeft;
            if (animate) {
                if (smoothScrollDrawerTo(mShownDrawer, startLeft)) {
                    mFlags |= FLAG_ANIMATING_DRAWER_CLOSURE;
                    mFlags &= ~FLAG_ANIMATING_DRAWER_OPENING;
                }
            } else {
                scrollDrawerTo(mShownDrawer, startLeft);
            }
        }
    }

    /**
     * Like {@link #scrollDrawerBy(View, float)}, but scroll smoothly instead of immediately.
     *
     * @param drawer the drawer to scroll
     * @param x      the position on the X axis for the drawer to reach
     * @return <code>true</code> if the given drawer is slidable and the scroll is
     * successfully started
     */
    private boolean smoothScrollDrawerTo(View drawer, float x) {
        LayoutParams lp = (LayoutParams) drawer.getLayoutParams();
        if (lp.left == x) {
            return false;
        }

        if (mScrollDrawerAnimator == null) {
            mScrollDrawerAnimator = new ScrollDrawerAnimator();

        } else if (mScrollDrawerAnimator.isRunning()) {
            mScrollDrawerAnimator.cancelWithNoListenerCalled();
        }
        mScrollDrawerAnimator.setFloatValues(lp.left, x);
        mScrollDrawerAnimator.start();
        return true;
    }

    /**
     * Scrolls the given drawer to a horizontal position relative to current view.
     *
     * @param drawer the drawer to scroll
     * @param x      the position on the X axis for the drawer to scroll to
     */
    private void scrollDrawerTo(View drawer, float x) {
        scrollDrawerBy(drawer, x - ((LayoutParams) drawer.getLayoutParams()).left);
    }

    /**
     * Moves the scrolled position of the given drawer. This will cause a call to
     * {@link OnDrawerScrollListener#onScrollPercentChange(SlidingDrawerLayout, View, float)}
     * and this view will be invalidated to redraw the content view's fading and to re-clip
     * the drawer's display area.
     *
     * <strong>NOTE:</strong> The content view will be simultaneously scrolled at
     * {@value #SCROLL_RATIO_CONTENT_TO_DRAWER} times the drawer speed.
     *
     * @param drawer the drawer to scroll
     * @param dx     the amount of pixels for the drawer to scroll by horizontally
     */
    private void scrollDrawerBy(View drawer, float dx) {
        if (drawer == null || dx == 0) return;

        LayoutParams drawerLP = (LayoutParams) drawer.getLayoutParams();
        LayoutParams contentLP = (LayoutParams) mContentView.getLayoutParams();

        drawerLP.left += dx;
        contentLP.left += dx * SCROLL_RATIO_CONTENT_TO_DRAWER;

        updateChildTranslationX(drawer, drawerLP.left - drawerLP.startLeft);
        updateChildTranslationX(mContentView, contentLP.left - contentLP.startLeft);
        invalidate();

        dispatchDrawerScrollPercentageChangeIfNeeded(
                (float) Math.round((drawerLP.left - drawerLP.startLeft)
                        / (drawerLP.finalLeft - drawerLP.startLeft) * 100f) / 100f
        /* Rounds the scroll percentages for which this leaves up to 2 decimal places to filter out
           the incorrect ones caused by floating-point arithmetic, such as -6.393347E-8. */);
    }

    private Field mRenderNode;
    private Method mSetTranslationX;
    private WeakHashMap<View, /* RenderNode */ Object> mRenderNodes;

    @SuppressLint("PrivateApi")
    private void updateChildTranslationX(View child, float translationX) {
        if (translationX == child.getTranslationX()) {
            return;
        }
        try {
            Object renderNode;
            if (mRenderNode == null) {
                //noinspection JavaReflectionMemberAccess
                mRenderNode = View.class.getDeclaredField("mRenderNode");
                mRenderNode.setAccessible(true);

                renderNode = mRenderNode.get(child);
                mRenderNodes = new WeakHashMap<>(1);
                mRenderNodes.put(child, renderNode);

                mSetTranslationX = renderNode.getClass()
                        .getMethod("setTranslationX", float.class);
            } else {
                renderNode = mRenderNodes.get(child);
                if (renderNode == null) {
                    renderNode = mRenderNode.get(child);
                    mRenderNodes.put(child, renderNode);
                }
            }

            mSetTranslationX.invoke(renderNode, translationX);
        } catch (Exception e) {
            e.printStackTrace();

            // Failed to invoke RenderNode#setTranslationX(float) for the child,
            // directly call its setTranslationX(float) instead.
            child.setTranslationX(translationX);
        }
    }

    private List<OnDrawerScrollListener> mOnDrawerScrollListeners;

    public void addOnDrawerScrollListener(@NonNull OnDrawerScrollListener listener) {
        if (mOnDrawerScrollListeners == null) {
            mOnDrawerScrollListeners = new LinkedList<>();

        } else if (mOnDrawerScrollListeners.contains(listener)) {
            return;
        }
        mOnDrawerScrollListeners.add(listener);
    }

    public void removeOnDrawerScrollListener(@NonNull OnDrawerScrollListener listener) {
        if (mOnDrawerScrollListeners != null)
            mOnDrawerScrollListeners.remove(listener);
    }

    public void clearOnDrawerScrollListeners() {
        if (mOnDrawerScrollListeners != null)
            mOnDrawerScrollListeners.clear();
    }

    private void dispatchDrawerScrollPercentageChangeIfNeeded(float percent) {
        if (percent == mScrollPercent) return;
        mScrollPercent = percent;

        if (mOnDrawerScrollListeners != null) {
            OnDrawerScrollListener[] listeners = mOnDrawerScrollListeners
                    .toArray(new OnDrawerScrollListener[0]);
            // After each loop, the count of OnDrawerScrollListener associated to this view
            // might have changed as addOnDrawerScrollListener, removeOnDrawerScrollListener or
            // clearOnDrawerScrollListeners method can be called during a callback to any listener,
            // in which case, a subsequent loop will throw an Exception.
            // For fear of that, here the above copied OnDrawerScrollListener set is used.
            for (OnDrawerScrollListener listener : listeners) {
                listener.onScrollPercentChange(this, mShownDrawer, percent);
                if (percent == 1) listener.onDrawerOpened(this, mShownDrawer);
                else if (percent == 0) listener.onDrawerClosed(this, mShownDrawer);
            }
        }
    }

    private void dispatchDrawerScrollStateChangeIfNeeded(@ScrollState int state) {
        final int old = mFlags & SCROLL_STATE_MASK;
        if (state == old) return;
        mFlags = (mFlags & ~SCROLL_STATE_MASK) | state;

        if (mOnDrawerScrollListeners != null) {
            OnDrawerScrollListener[] listeners = mOnDrawerScrollListeners
                    .toArray(new OnDrawerScrollListener[0]);
            for (OnDrawerScrollListener listener : listeners)
                listener.onScrollStateChange(this, mShownDrawer, state);
        }

        switch (state) {
            case SCROLL_STATE_TOUCH_SCROLL:
            case SCROLL_STATE_AUTO_SCROLL:
                if (old == SCROLL_STATE_IDLE) {
                    mShownDrawerLayerType = mShownDrawer.getLayerType();
                    mShownDrawer.setLayerType(LAYER_TYPE_HARDWARE, null);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1
                            && ViewCompat.isAttachedToWindow(mShownDrawer)) {
                        mShownDrawer.buildLayer();
                    }

                    if (mScrollPercent == 0) {
                        LayoutParams lp = (LayoutParams) mContentView.getLayoutParams();
                        lp.finalLeft = lp.startLeft + (mShownDrawer == mLeftDrawer ?
                                mShownDrawer.getWidth() : -mShownDrawer.getWidth());
                    }
                }
                break;

            case SCROLL_STATE_IDLE:
                mShownDrawer.setLayerType(mShownDrawerLayerType, null);

                if (mScrollPercent == 0) {
                    mShownDrawer = null;
                }
                break;
        }
    }

    /**
     * Classes that wish to monitor the events of the drawers' scrolling should implement
     * this interface.
     */
    public interface OnDrawerScrollListener {
        /**
         * Callback that will be called on the dragged drawer opened.
         *
         * @param parent the layout holds that drawer
         * @param drawer the drawer currently being dragged
         */
        void onDrawerOpened(@NonNull SlidingDrawerLayout parent, @NonNull View drawer);

        /**
         * Callback that will be called on the dragged drawer closed.
         *
         * @param parent the layout that drawer belongs to
         * @param drawer the drawer currently being dragged
         */
        void onDrawerClosed(@NonNull SlidingDrawerLayout parent, @NonNull View drawer);

        /**
         * Callback to be called when the scroll percentage of the dragged drawer changes.
         *
         * @param parent  the current layout
         * @param drawer  the drawer currently being dragged
         * @param percent the scroll percentage of the dragged drawer
         */
        void onScrollPercentChange(@NonNull SlidingDrawerLayout parent, @NonNull View drawer,
                                   @FloatRange(from = 0.0f, to = 1.0f) float percent);

        /**
         * Callback to be called when the scroll state ({@code mFlags & SCROLL_STATE_MASK})
         * of the dragged drawer changes.
         *
         * @param parent the current layout
         * @param drawer the drawer currently being dragged
         * @param state  the scroll state of the dragged drawer
         */
        void onScrollStateChange(@NonNull SlidingDrawerLayout parent, @NonNull View drawer,
                                 @ScrollState int state);
    }

    /**
     * Stub/No-op implementations of all methods of {@link OnDrawerScrollListener}.
     * Override this if you only care about a few of the available callback methods.
     */
    public static abstract class SimpleOnDrawerScrollListener implements OnDrawerScrollListener {
        @Override
        public void onDrawerOpened(@NonNull SlidingDrawerLayout parent, @NonNull View drawer) {
        }

        @Override
        public void onDrawerClosed(@NonNull SlidingDrawerLayout parent, @NonNull View drawer) {
        }

        @Override
        public void onScrollPercentChange(@NonNull SlidingDrawerLayout parent,
                                          @NonNull View drawer, float percent) {
        }

        @Override
        public void onScrollStateChange(@NonNull SlidingDrawerLayout parent,
                                        @NonNull View drawer, int state) {
        }
    }
}
