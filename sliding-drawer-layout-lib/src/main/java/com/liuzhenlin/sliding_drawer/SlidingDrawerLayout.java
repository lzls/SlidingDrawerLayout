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
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.ColorInt;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.util.ArraySet;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.ViewDragHelper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.Set;

/**
 * A layout shows better than {@link android.support.v4.widget.DrawerLayout}, which can
 * also scroll its contents as the user drags its drawer.
 *
 * @author <a href="mailto:2233788867@qq.com">刘振林</a>
 */
public class SlidingDrawerLayout extends ViewGroup {
    private static final String TAG = "SlidingDrawerLayout";
    private static final boolean DEBUG = false;

    /**
     * The left child view covered by {@link #mContentView}
     */
    @Nullable
    private View mLeftDrawer;
    /**
     * The right child view covered by {@link #mContentView}
     */
    @Nullable
    private View mRightDrawer;
    /**
     * The content view in this layout that is always visible
     */
    @Nullable
    private View mContentView;

    /**
     * The drawer currently shown, maybe one of {@link #mLeftDrawer} and {@link #mRightDrawer}
     * or <code>null</code>.
     */
    @Nullable
    private View mShownDrawer;

    /**
     * @see #getLeftDrawerWidthPercent()
     * @see #setLeftDrawerWidthPercent(float)
     */
    private float mLeftDrawerWidthPercent = UNSPECIFIED_DRAWER_WIDTH_PERCENT;

    /**
     * @see #getRightDrawerWidthPercent()
     * @see #setRightDrawerWidthPercent(float)
     */
    private float mRightDrawerWidthPercent = UNSPECIFIED_DRAWER_WIDTH_PERCENT;

    /**
     * If set, the drawer's width will be measured as usual.
     */
    public static final int UNSPECIFIED_DRAWER_WIDTH_PERCENT = -1;

    /**
     * The minimum percentage of the width of the drawers relative to current view's.
     */
    public static final float MINIMUM_DRAWER_WIDTH_PERCENT = 0.1f;
    /**
     * The maximum percentage of the width of the drawers relative to current view's.
     */
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

    private static final int SCROLL_STATE_MASK = 0x3;

    @SuppressWarnings("WeakerAccess")
    @IntDef({
            SCROLL_STATE_IDLE,
            SCROLL_STATE_TOUCH_SCROLL,
            SCROLL_STATE_AUTO_SCROLL
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ScrollState {
    }

    /**
     * A flag indicates that the left drawer {@link #mLeftDrawer} is slidable
     * with the current measurements.
     */
    private static final int FLAG_LEFT_DRAWER_SLIDABLE = 1 << 2;

    /**
     * A flag indicates that the right drawer {@link #mRightDrawer} is slidable
     * with the current measurements.
     */
    private static final int FLAG_RIGHT_DRAWER_SLIDABLE = 1 << 3;

    /**
     * A flag indicates that the current layout is undergoing a layout pass.
     */
    private static final int FLAG_IN_LAYOUT = 1 << 4;

    /**
     * Indicates that on this view receiving {@link MotionEvent#ACTION_DOWN}, the user's finger
     * downs on the area of content view {@link #mContentView} while a drawer is open.
     */
    private static final int FLAG_FINGER_DOWNS_ON_CONTENT_WHEN_DRAWER_IS_OPEN = 1 << 5;

    /**
     * @see #getSensibleContentEdgeSize()
     * @see #setSensibleContentEdgeSize(float)
     */
    private float mSensibleContentEdgeSize; // px
    private static final int DEFAULT_EDGE_SIZE = 50; //dp

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
     * to scroll the drawer currently being dragged.
     * <p>
     * While that drawer is scrolling, we simultaneously make the content scroll
     * at a higher speed than the drawer's.
     */
    private static final float SCROLL_RATIO_CONTENT_TO_DRAWER = 3f / 1f;

    /** @see #getScrollPercent() */
    private float mScrollPercent;

    /** Animator for scrolling the drawers {@link #mLeftDrawer}, {@link #mRightDrawer}. */
    private ValueAnimator mScrollDrawerAnimator;

    /**
     * Minimum time interval in milliseconds of automatically scrolling the drawers.
     */
    private static final int BASE_DURATION_AUTOSCROLL_DRAWERS = 125; // ms

    /**
     * The fade color used for the content view {@link #mContentView}, default is 50% black.
     *
     * @see #getContentFadeColor()
     * @see #setContentFadeColor(int)
     */
    @ColorInt
    private int mContentFadeColor = DEFAULT_FADE_COLOR;
    @ColorInt
    private static final int DEFAULT_FADE_COLOR = 0x7F000000;

    @SuppressLint("RtlHardcoded")
    @IntDef({Gravity.LEFT, Gravity.RIGHT, GravityCompat.START, GravityCompat.END})
    @Retention(RetentionPolicy.SOURCE)
    private @interface EdgeGravity {
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

        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.SlidingDrawerLayout, defStyleAttr, 0);
        setStartDrawerWidthPercent(a.getFloat(R.styleable.SlidingDrawerLayout_startDrawerWidthPercent,
                UNSPECIFIED_DRAWER_WIDTH_PERCENT));
        setEndDrawerWidthPercent(a.getFloat(R.styleable.SlidingDrawerLayout_endDrawerWidthPercent,
                UNSPECIFIED_DRAWER_WIDTH_PERCENT));
        setSensibleContentEdgeSize(a.getDimensionPixelSize(
                R.styleable.SlidingDrawerLayout_sensibleContentEdgeSize,
                (int) (DEFAULT_EDGE_SIZE * mDp + 0.5f)));
        setContentFadeColor(a.getColor(R.styleable.SlidingDrawerLayout_contentFadeColor,
                DEFAULT_FADE_COLOR));
        a.recycle();
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        checkChildCount();
        super.addView(child, index, params);
    }

    @Override
    protected boolean addViewInLayout(View child, int index, ViewGroup.LayoutParams params,
                                      boolean preventRequestLayout) {
        checkChildCount();
        return super.addViewInLayout(child, index, params);
    }

    private void checkChildCount() {
        if (getChildCount() > 2) {
            throw new IllegalStateException("SlidingDrawerLayout can host only three direct children.");
        }
    }

    /**
     * @return the percentage of the width of the left drawer relative to current view's
     * within the range from {@value MINIMUM_DRAWER_WIDTH_PERCENT} to {@value #MAXIMUM_DRAWER_WIDTH_PERCENT}
     * or just {@link #UNSPECIFIED_DRAWER_WIDTH_PERCENT} if no percentage has been applied to
     * measuring its width.
     * @see #setLeftDrawerWidthPercent(float)
     */
    public float getLeftDrawerWidthPercent() {
        return mLeftDrawerWidthPercent;
    }

    /**
     * Sets the percentage of the width of the left drawer relative to current view's
     * within the range from {@value MINIMUM_DRAWER_WIDTH_PERCENT} to {@value #MAXIMUM_DRAWER_WIDTH_PERCENT}
     * or pass in {@link #UNSPECIFIED_DRAWER_WIDTH_PERCENT} to ignore it to use the usual measurement
     * by providing a valid width (such as {@link ViewGroup.LayoutParams#WRAP_CONTENT}、
     * {@link ViewGroup.LayoutParams#MATCH_PARENT} for the left drawer.
     *
     * @throws IllegalArgumentException if the provided argument <code>percent</code> is outside
     *                                  the above mentioned.
     * @see #getLeftDrawerWidthPercent()
     */
    private void setLeftDrawerWidthPercent(float percent) {
        checkDrawerWidthPercent(percent);
        if (mLeftDrawerWidthPercent != percent) {
            mLeftDrawerWidthPercent = percent;
            if (mLeftDrawer != null) {
                requestLayout();
            }
        }
    }

    /**
     * @return the percentage of the width of the right drawer relative to current view's
     * within the range from {@value MINIMUM_DRAWER_WIDTH_PERCENT} to {@value #MAXIMUM_DRAWER_WIDTH_PERCENT}
     * or just {@link #UNSPECIFIED_DRAWER_WIDTH_PERCENT} if no percentage has been applied to
     * measuring its width.
     * @see #setLeftDrawerWidthPercent(float)
     */
    public float getRightDrawerWidthPercent() {
        return mRightDrawerWidthPercent;
    }

    /**
     * Sets the percentage of the width of the right drawer relative to current view's
     * within the range from {@value MINIMUM_DRAWER_WIDTH_PERCENT} to {@value #MAXIMUM_DRAWER_WIDTH_PERCENT}
     * or pass in {@link #UNSPECIFIED_DRAWER_WIDTH_PERCENT} to ignore it to use the usual measurement
     * by providing a valid width (such as {@link ViewGroup.LayoutParams#WRAP_CONTENT}、
     * {@link ViewGroup.LayoutParams#MATCH_PARENT} for the right drawer.
     *
     * @throws IllegalArgumentException if the provided argument <code>percent</code> is outside
     *                                  the above mentioned.
     * @see #getLeftDrawerWidthPercent()
     */
    private void setRightDrawerWidthPercent(float percent) {
        checkDrawerWidthPercent(percent);
        if (mRightDrawerWidthPercent != percent) {
            mRightDrawerWidthPercent = percent;
            if (mRightDrawer != null) {
                requestLayout();
            }
        }
    }

    /**
     * @return the width percentage of the start drawer depending on this view's
     * resolved layout direction.
     */
    public float getStartDrawerWidthPercent() {
        return isLayoutRtl() ? getRightDrawerWidthPercent() : getLeftDrawerWidthPercent();
    }

    /**
     * Sets the width percentage of the start drawer depending on this view's
     * resolved layout direction.
     */
    public void setStartDrawerWidthPercent(final float percent) {
        Runnable runnable = new SetDrawerWidthPercentRunnable(this,
                GravityCompat.START, percent);
        if (ViewCompat.isAttachedToWindow(this)) {
            runnable.run();
        } else {
            post(runnable);
        }
    }

    /**
     * @return the width percentage of the end drawer depending on this view's
     * resolved layout direction.
     */
    public float getEndDrawerWidthPercent() {
        return isLayoutRtl() ? getLeftDrawerWidthPercent() : getRightDrawerWidthPercent();
    }

    /**
     * Sets the width percentage of the end drawer depending on this view's
     * resolved layout direction.
     */
    public void setEndDrawerWidthPercent(float percent) {
        Runnable runnable = new SetDrawerWidthPercentRunnable(this,
                GravityCompat.END, percent);
        if (ViewCompat.isAttachedToWindow(this)) {
            runnable.run();
        } else {
            post(runnable);
        }
    }

    private void checkDrawerWidthPercent(float percent) {
        if (percent != UNSPECIFIED_DRAWER_WIDTH_PERCENT
                && (percent < MINIMUM_DRAWER_WIDTH_PERCENT || percent > MAXIMUM_DRAWER_WIDTH_PERCENT)) {
            throw new IllegalArgumentException("Invalid percent for drawer's width. " +
                    "The value must be " + UNSPECIFIED_DRAWER_WIDTH_PERCENT + " or " +
                    "from " + MINIMUM_DRAWER_WIDTH_PERCENT + " to " + MAXIMUM_DRAWER_WIDTH_PERCENT);
        }
    }

    private static class SetDrawerWidthPercentRunnable implements Runnable {
        private final WeakReference<SlidingDrawerLayout> mLayout;
        private final int mEdgeGravity;
        private final float mPercent;

        SetDrawerWidthPercentRunnable(SlidingDrawerLayout layout, @EdgeGravity int gravity, float percent) {
            mLayout = new WeakReference<>(layout);
            mEdgeGravity = gravity;
            mPercent = percent;
        }

        @SuppressLint("RtlHardcoded")
        @Override
        public void run() {
            SlidingDrawerLayout layout = mLayout.get();
            if (layout == null) return;

            final int absEdgeGravity = GravityUtils.getAbsoluteHorizontalGravity(
                    layout, mEdgeGravity);
            if (absEdgeGravity == Gravity.LEFT) {
                layout.setLeftDrawerWidthPercent(mPercent);
            } else if (absEdgeGravity == Gravity.RIGHT) {
                layout.setRightDrawerWidthPercent(mPercent);
            }
        }
    }

    /**
     * @return the sensible size of the draggable edges of the content view.
     * This is the range in pixels along the edges of content view that will actively
     * detect edge touches or drags if edge tracking is enabled.
     * @see #setSensibleContentEdgeSize(float)
     */
    public float getSensibleContentEdgeSize() {
        return mSensibleContentEdgeSize;
    }

    /**
     * Sets the sensible size for the draggable edges of content view.
     * This is the range in pixels along the edges of content view that will actively
     * detect edge touches or drags if edge tracking is enabled.
     *
     * @throws IllegalArgumentException if the provided argument <code>size</code> < 0
     * @see #getSensibleContentEdgeSize()
     */
    public void setSensibleContentEdgeSize(float size) {
        if (size < 0) {
            throw new IllegalArgumentException("the sensible size for the draggable edges " +
                    "of content view must >= 0.");
        }
        mSensibleContentEdgeSize = size;
    }

    /**
     * @return whether the drawer on the specified side is slidable or not
     * @see #isDrawerSlidable(View)
     */
    @SuppressLint("RtlHardcoded")
    public boolean isDrawerSlidable(@EdgeGravity int gravity) {
        final int absoluteGravity = GravityCompat.getAbsoluteGravity(gravity,
                ViewCompat.getLayoutDirection(this));
        switch (absoluteGravity) {
            case Gravity.LEFT:
                isDrawerSlidable(mLeftDrawer);
                break;
            case Gravity.RIGHT:
                isDrawerSlidable(mRightDrawer);
                break;
        }
        return false;
    }

    /**
     * @return whether the specified drawer is slidable or not
     * @see #isDrawerSlidable(int)
     */
    public boolean isDrawerSlidable(View drawer) {
        return drawer == mLeftDrawer && (mFlags & FLAG_LEFT_DRAWER_SLIDABLE) != 0
                || drawer == mRightDrawer && (mFlags & FLAG_RIGHT_DRAWER_SLIDABLE) != 0;
    }

    /**
     * @return the current state of the dragged drawer' scrolling, maybe one of
     * {@link #SCROLL_STATE_IDLE},
     * {@link #SCROLL_STATE_TOUCH_SCROLL} and
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
    public float getScrollPercent() {
        return mScrollPercent;
    }

    /**
     * @return whether a drawer has been fully slid to be visible
     * @see #isDrawerClosed()
     */
    public boolean isDrawerOpen() {
        return mScrollPercent == 1;
    }

    /**
     * @return whether all drawers have been fully hidden or not
     * @see #isDrawerOpen()
     */
    public boolean isDrawerClosed() {
        return mScrollPercent == 0;
    }

    /**
     * @return the fade color used for the content view
     * @see #setContentFadeColor(int)
     */
    @ColorInt
    public int getContentFadeColor() {
        return mContentFadeColor;
    }

    /**
     * Sets the fade color used for the content view to obscure primary content while
     * a drawer is open.
     *
     * @see #getContentFadeColor()
     */
    public void setContentFadeColor(@ColorInt int color) {
        if (mContentFadeColor != color) {
            mContentFadeColor = color;
            if (mScrollPercent > 0 && (mScrollDrawerAnimator == null ||
                    !mScrollDrawerAnimator.isRunning())) {
                invalidate();
            }
        }
    }

    /**
     * Indicates whether or not this view's layout direction is right-to-left.
     * This is resolved from layout attribute and/or the inherited value from the parent
     *
     * @return <code>true</code> if the layout direction is right-to-left
     */
    protected boolean isLayoutRtl() {
        return ViewCompat.getLayoutDirection(this) == ViewCompat.LAYOUT_DIRECTION_RTL;
    }

    private void traverseAllChildren(int childCount) {
        mContentView = mLeftDrawer = mRightDrawer = null;
        mFlags &= ~(FLAG_LEFT_DRAWER_SLIDABLE | FLAG_RIGHT_DRAWER_SLIDABLE);

        switch (childCount) {
            case 1:
                mContentView = getChildAt(0);
                break;
            case 2:
                traverseAllChildren2(childCount);
                if (mContentView == null) {
                    mContentView = isLayoutRtl() ? mLeftDrawer : mRightDrawer;
                    mRightDrawer = null;
                }

                if (mLeftDrawer == null && mRightDrawer == null) {
                    throw new IllegalStateException("Edge gravity in values Gravity#LEFT, " +
                            "Gravity#RIGHT, Gravity#START and Gravity#END must be set " +
                            "for the Drawer's LayoutParams to finalize the Drawer's placement.");
                }
                if (mLeftDrawer != null && mLeftDrawer.getVisibility() != GONE) {
                    mFlags |= FLAG_LEFT_DRAWER_SLIDABLE;
                } else if (mRightDrawer != null && mRightDrawer.getVisibility() != GONE) {
                    mFlags |= FLAG_RIGHT_DRAWER_SLIDABLE;
                }
                break;
            case 3:
                traverseAllChildren2(childCount);

                if (mLeftDrawer == null || mRightDrawer == null) {
                    throw new IllegalStateException("Different edge gravity in values Gravity#LEFT, " +
                            "Gravity#RIGHT, Gravity#START and Gravity#END must be set " +
                            "for Drawers' LayoutParams to finalize the Drawers' placements.");
                }
                if (mLeftDrawer.getVisibility() != GONE) {
                    mFlags |= FLAG_LEFT_DRAWER_SLIDABLE;
                }
                if (mRightDrawer.getVisibility() != GONE) {
                    mFlags |= FLAG_RIGHT_DRAWER_SLIDABLE;
                }
                break;
        }

        if ((mFlags & (FLAG_LEFT_DRAWER_SLIDABLE | FLAG_RIGHT_DRAWER_SLIDABLE)) != 0
                && mInternalOnDrawerScrollListener == null) {
            mInternalOnDrawerScrollListener = new InternalOnDrawerScrollListener();
            addOnDrawerScrollListener(mInternalOnDrawerScrollListener);
        }
    }

    @SuppressLint("RtlHardcoded")
    private void traverseAllChildren2(int childCount) {
        final int layoutDirection = ViewCompat.getLayoutDirection(this);
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
        traverseAllChildren(childCount);

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
    protected void measureChild(View child, int parentWidthMeasureSpec, int parentHeightMeasureSpec) {
        final int horizontalMargins = getPaddingLeft() + getPaddingRight();
        final int verticalMargins = getPaddingTop() + getPaddingBottom();

        int childWidthMeasureSpec, childHeightMeasureSpec;
        if (child == mContentView) {
            childWidthMeasureSpec = getChildMeasureSpec(parentWidthMeasureSpec, horizontalMargins,
                    child.getLayoutParams().width);
        } else {
            final int availableWidth = MeasureSpec.getSize(parentWidthMeasureSpec) - horizontalMargins;

            float drawerWidthPercent = child == mLeftDrawer ?
                    mLeftDrawerWidthPercent : mRightDrawerWidthPercent;
            if (drawerWidthPercent == UNSPECIFIED_DRAWER_WIDTH_PERCENT) {
                final int minChildWidth = (int) (availableWidth * MINIMUM_DRAWER_WIDTH_PERCENT + 0.5f);
                final int maxChildWidth = (int) (availableWidth * MAXIMUM_DRAWER_WIDTH_PERCENT + 0.5f);

                childWidthMeasureSpec = getChildMeasureSpec(
                        parentWidthMeasureSpec, horizontalMargins, child.getLayoutParams().width);

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
        childHeightMeasureSpec = getChildMeasureSpec(parentHeightMeasureSpec, verticalMargins,
                child.getLayoutParams().height);

        child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
    }

    @Override
    protected void measureChildWithMargins(View child, int parentWidthMeasureSpec, int widthUsed,
                                           int parentHeightMeasureSpec, int heightUsed) {
        // ignore child's margins
        measureChild(child, parentWidthMeasureSpec, parentHeightMeasureSpec);
    }

    @SuppressLint("RtlHardcoded")
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        mFlags |= FLAG_IN_LAYOUT;
        final int parentLeft = getPaddingLeft();
        final int parentRight = right - left - getPaddingRight();
        final int parentTop = getPaddingTop();
        final int parentBottom = bottom - top - getPaddingBottom();

        final int parentWidth = parentRight - parentLeft;
        final int parentHeight = parentBottom - parentTop;

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

            if (child == mContentView) {
                final int horizontalGravity = GravityUtils.getAbsoluteHorizontalGravity(
                        this, lp.gravity);
                switch (horizontalGravity) {
                    case Gravity.LEFT:
                        lp.startLeft = parentLeft;
                        break;
                    case Gravity.RIGHT:
                        lp.startLeft = parentRight - childWidth;
                        break;
                    default:
                    case Gravity.CENTER_HORIZONTAL:
                        lp.startLeft = parentLeft + (parentWidth - childWidth) / 2f;
                        break;
                }
                if (mScrollPercent == 0) {
                    lp.left = lp.startLeft;
                } else {
                    // Its finalLeft might have changed after the shown drawer's width changed.
                    lp.finalLeft = lp.startLeft + (mShownDrawer == mLeftDrawer ?
                            mShownDrawer.getMeasuredWidth() : -mShownDrawer.getMeasuredWidth());
                    lp.left = lp.startLeft + (lp.finalLeft - lp.startLeft) * mScrollPercent;
                }
                childLeft = Math.round(lp.left);
            } else {
                final float offset = childWidth / SCROLL_RATIO_CONTENT_TO_DRAWER;

                final int horizontalGravity = GravityUtils.getAbsoluteHorizontalGravity(
                        this, lp.gravity);
                if ((horizontalGravity & Gravity.LEFT) == Gravity.LEFT) {
                    lp.finalLeft = parentLeft;
                    lp.startLeft = lp.finalLeft - offset;

                } else if ((horizontalGravity & Gravity.RIGHT) == Gravity.RIGHT) {
                    lp.finalLeft = parentRight - childWidth;
                    lp.startLeft = lp.finalLeft + offset;
                }
                lp.left = lp.startLeft + (child == mShownDrawer ?
                        (lp.finalLeft - lp.startLeft) * mScrollPercent : 0);
                childLeft = Math.round(lp.left);
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
                default:
                case Gravity.CENTER_VERTICAL:
                    childTop = (int) (parentTop + (parentHeight - childHeight) / 2f);
                    break;
            }

            child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight);
        }
        mFlags &= ~FLAG_IN_LAYOUT;
    }

    @Override
    public void requestLayout() {
        if ((mFlags & FLAG_IN_LAYOUT) == 0) {
            super.requestLayout();
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

            if (mShownDrawer == mLeftDrawer) {
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

    @SuppressWarnings("WeakerAccess")
    public static class LayoutParams extends ViewGroup.LayoutParams {
        /**
         * The gravity to apply with the View to which these layout parameters are associated.
         * <p>
         * The default value is {@link Gravity#NO_GRAVITY}.
         *
         * @see Gravity
         */
        public int gravity = Gravity.NO_GRAVITY;

        /**
         * The left of the View to which these layout parameters are associated
         * when the drawers are completely hidden.
         */
        private float startLeft;
        /** The final left position for the View to which these layout parameters belong to reach. */
        private float finalLeft;

        /** The current left of the View these layout parameters belong to. */
        private float left;

        public LayoutParams(@NonNull Context c, @Nullable AttributeSet attrs) {
            super(c, attrs);

            TypedArray a = c.obtainStyledAttributes(attrs, R.styleable.SlidingDrawerLayout_Layout);
            gravity = a.getInt(R.styleable.SlidingDrawerLayout_Layout_layout_gravity, Gravity.NO_GRAVITY);
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
         * @see Gravity
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
    public CharSequence getAccessibilityClassName() {
        return SlidingDrawerLayout.class.getName();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if ((mFlags & (FLAG_LEFT_DRAWER_SLIDABLE | FLAG_RIGHT_DRAWER_SLIDABLE)) == 0) {
            return super.onInterceptTouchEvent(ev);
        }
        switch (ev.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                onPointerDown(ev);
                mFlags &= ~FLAG_FINGER_DOWNS_ON_CONTENT_WHEN_DRAWER_IS_OPEN;

                if (mScrollPercent == 1) {
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
                    // mScrollPercent in (0,1)
                } else if (mScrollPercent != 0) {
                    return true;
                }
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                onPointerDown(ev);
                break;
            case MotionEvent.ACTION_MOVE:
                if (!onPointerMove(ev)) {
                    break;
                }
                if (tryHandleSlidingEvent()) {
                    dispatchDrawerScrollStateChangeIfNeeded(SCROLL_STATE_TOUCH_SCROLL);
                    return true;
                }
                break;
            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mActivePointerId = ViewDragHelper.INVALID_POINTER;
                break;
        }
        return false;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if ((mFlags & (FLAG_LEFT_DRAWER_SLIDABLE | FLAG_RIGHT_DRAWER_SLIDABLE)) == 0) {
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
                if (tryHandleSlidingEvent()) {
                    dispatchDrawerScrollStateChangeIfNeeded(SCROLL_STATE_TOUCH_SCROLL);
                }
                break;
            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(event);
                break;
            case MotionEvent.ACTION_UP:
                mActivePointerId = ViewDragHelper.INVALID_POINTER;
                if ((mFlags & SCROLL_STATE_MASK) == SCROLL_STATE_TOUCH_SCROLL) {
                    if (mScrollPercent == 1 || mScrollPercent == 0) {
                        recycleVelocityTracker();
                        dispatchDrawerScrollStateChangeIfNeeded(SCROLL_STATE_IDLE);
                        break;
                    }

                    if (mVelocityTracker != null) {
                        mVelocityTracker.computeCurrentVelocity(1000);
                        final float xVel = mVelocityTracker.getXVelocity(mActivePointerId);
                        final float maximumVel = mAutoScrollDrawerMinimumVelocityX;
                        if (mShownDrawer == mLeftDrawer && xVel >= maximumVel
                                || mShownDrawer == mRightDrawer && xVel <= -maximumVel) {
                            recycleVelocityTracker();
                            openDrawer(mShownDrawer, true);
                            break;
                        } else if (mShownDrawer == mLeftDrawer && xVel <= -maximumVel
                                || mShownDrawer == mRightDrawer && xVel >= maximumVel) {
                            recycleVelocityTracker();
                            closeDrawer(true);
                            break;
                        }
                    }

                    if (mScrollPercent >= 0.5f) {
                        openDrawer(mShownDrawer, true);
                    } else {
                        closeDrawer(true);
                    }
                } else if ((mFlags & FLAG_FINGER_DOWNS_ON_CONTENT_WHEN_DRAWER_IS_OPEN) != 0) {
                    closeDrawer(true);
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                mActivePointerId = ViewDragHelper.INVALID_POINTER;
                recycleVelocityTracker();
                closeDrawer(true);
                break;
        }
        return true;
    }

    private boolean tryHandleSlidingEvent() {
        boolean handle = false;

        final float dx = mTouchX[mTouchX.length - 1] - mDownX;
        final float absDX = Math.abs(dx);
        final float absDY = Math.abs(mTouchY[mTouchY.length - 1] - mDownY);

        if ((mShownDrawer == null || mShownDrawer == mLeftDrawer) && isDrawerSlidable(mLeftDrawer)) {
            final int left = getPaddingLeft();
            if (mScrollPercent == 0) {
                if (mDownX > left + mSensibleContentEdgeSize || mDownX < left) {
                    return false;
                } else {
                    handle = dx > absDY && dx > mTouchSlop;
                }
            } else if (mScrollPercent == 1 &&
                    mDownX <= ((LayoutParams) mContentView.getLayoutParams()).finalLeft) {
                handle = dx < -absDY && dx < -mTouchSlop;
            } else {
                handle = absDX > absDY && absDX > mTouchSlop;
            }
            if (handle) {
                mShownDrawer = mLeftDrawer;
            }
        }
        if ((mShownDrawer == null || mShownDrawer == mRightDrawer) && isDrawerSlidable(mRightDrawer)) {
            final int right = getWidth() - getPaddingRight();
            if (mScrollPercent == 0) {
                if (mDownX < right - mSensibleContentEdgeSize || mDownX > right) {
                    return false;
                } else {
                    handle = dx < -absDY && dx < -mTouchSlop;
                }
            } else if (mScrollPercent == 1 &&
                    mDownX >= ((LayoutParams) mContentView.getLayoutParams()).finalLeft) {
                handle = dx > absDY && dx > mTouchSlop;
            } else {
                handle = absDX > absDY && absDX > mTouchSlop;
            }
            if (handle) {
                mShownDrawer = mRightDrawer;
            }
        }

        if (handle) {
            ViewParent parent = getParent();
            if (parent != null) {
                parent.requestDisallowInterceptTouchEvent(true);
            }
        }
        return handle;
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

    private void recycleVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
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
     * Automatically open the specified drawer. <br>
     * <b>Note that this will only work if there is no drawer opened or the specified drawer is
     * the one currently being dragged.</b>
     *
     * @param animate smoothly open it through animator or not
     * @see #openDrawer(int, boolean)
     * @see #closeDrawer(boolean)
     */
    public void openDrawer(View drawer, boolean animate) {
        if (drawer != null && (drawer == mLeftDrawer || drawer == mRightDrawer)) {
            if (mShownDrawer == null || drawer == mShownDrawer) {
                mShownDrawer = drawer;
                final float finalLeft = ((LayoutParams) drawer.getLayoutParams()).finalLeft;
                if (animate) {
                    smoothScrollDrawerTo(drawer, finalLeft);
                } else {
                    scrollDrawerTo(drawer, finalLeft);
                }
            } else {
                Log.w(TAG, "Can't open this drawer while the other is open.");
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
                smoothScrollDrawerTo(mShownDrawer, startLeft);
            } else {
                scrollDrawerTo(mShownDrawer, startLeft);
            }
        }
    }

    /**
     * Smoothly scroll the specified drawer to a horizontal position (relative to current view)
     * and simultaneously scroll the content view {@link #mContentView} at a higher speed
     * than the drawer's.
     *
     * @param finalLeft the final position of the drawer's left
     */
    private void smoothScrollDrawerTo(View drawer, float finalLeft) {
        if (!isDrawerSlidable(drawer)) {
            return;
        }

        LayoutParams drawerLP = (LayoutParams) drawer.getLayoutParams();
        if (drawerLP.left != finalLeft) {
            if (mScrollDrawerAnimator == null) {
                initAnimator();
            }

            mScrollDrawerAnimator.setFloatValues(drawerLP.left, finalLeft);
            final float range = Math.abs((finalLeft - drawerLP.left)
                    / (drawerLP.finalLeft - drawerLP.startLeft));
            mScrollDrawerAnimator.setDuration((int) ((1f + range)
                    * BASE_DURATION_AUTOSCROLL_DRAWERS + 0.5f))
                    .start();
        }
    }

    private void initAnimator() {
        mScrollDrawerAnimator = new ValueAnimator();
        mScrollDrawerAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                dispatchDrawerScrollStateChangeIfNeeded(SCROLL_STATE_AUTO_SCROLL);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                dispatchDrawerScrollStateChangeIfNeeded(SCROLL_STATE_IDLE);
            }
        });
        mScrollDrawerAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                scrollDrawerTo(mShownDrawer, (float) animation.getAnimatedValue());
            }
        });
    }

    private void scrollDrawerBy(View drawer, float dx) {
        if (dx == 0 || !isDrawerSlidable(drawer)) {
            return;
        }

        LayoutParams drawerLP = (LayoutParams) drawer.getLayoutParams();
        LayoutParams contentLP = (LayoutParams) mContentView.getLayoutParams();

        drawerLP.left += dx;
        contentLP.left += dx * SCROLL_RATIO_CONTENT_TO_DRAWER;
        // Rounds the scroll percentages for which this leaves up to 2 decimal places to filter out
        // the incorrect ones caused by floating-point arithmetic, such as -6.393347E-8.
        mScrollPercent = (float) Math.round((drawerLP.left - drawerLP.startLeft)
                / (drawerLP.finalLeft - drawerLP.startLeft) * 100f) / 100f;

        final int drawerLeft = Math.round(drawerLP.left);
        final int contentLeft = Math.round(contentLP.left);
        drawer.layout(drawerLeft, drawer.getTop(),
                drawerLeft + drawer.getWidth(), drawer.getBottom());
        mContentView.layout(contentLeft, mContentView.getTop(),
                contentLeft + mContentView.getWidth(), mContentView.getBottom());

        if (mOnDrawerScrollListeners != null) {
            OnDrawerScrollListener[] listeners = mOnDrawerScrollListeners
                    .toArray(new OnDrawerScrollListener[mOnDrawerScrollListeners.size()]);
            // After each loop, the count of OnDrawerScrollListeners associated to this view
            // might have changed as addOnDrawerScrollListener, removeOnDrawerScrollListener or
            // clearOnDrawerScrollListeners method can be called during callbacks to any listener,
            // in which case, a subsequent loop will throw an Exception.
            // For fear of that, there the above copied one is used.
            for (OnDrawerScrollListener listener : listeners) {
                listener.onScrollPercentChange(this, drawer, mScrollPercent);
                if (mScrollPercent == 1) listener.onDrawerOpened(this, drawer);
                else if (mScrollPercent == 0) listener.onDrawerClosed(this, drawer);
            }
        }
    }

    private void scrollDrawerTo(View drawer, float finalLeft) {
        if (drawer != null) {
            scrollDrawerBy(drawer, finalLeft - ((LayoutParams) drawer.getLayoutParams()).left);
        }
    }

    private void dispatchDrawerScrollStateChangeIfNeeded(@ScrollState int state) {
        if ((mFlags & SCROLL_STATE_MASK) != state) {
            mFlags = (mFlags & ~SCROLL_STATE_MASK) | state;
            if (mOnDrawerScrollListeners != null) {
                OnDrawerScrollListener[] listeners = mOnDrawerScrollListeners
                        .toArray(new OnDrawerScrollListener[mOnDrawerScrollListeners.size()]);
                for (OnDrawerScrollListener listener : listeners)
                    listener.onScrollStateChange(this, mShownDrawer, state);
            }
        }
    }

    private Set<OnDrawerScrollListener> mOnDrawerScrollListeners;
    private OnDrawerScrollListener mInternalOnDrawerScrollListener;

    public void addOnDrawerScrollListener(OnDrawerScrollListener listener) {
        if (mOnDrawerScrollListeners == null)
            mOnDrawerScrollListeners = new ArraySet<>(1);
        mOnDrawerScrollListeners.add(listener);
    }

    public void removeOnDrawerScrollListener(OnDrawerScrollListener listener) {
        if (mOnDrawerScrollListeners != null)
            mOnDrawerScrollListeners.remove(listener);
    }

    public void clearOnDrawerScrollListeners() {
        // Remove all OnDrawerScrollListeners associated to this view except for our internal one.
        if (mOnDrawerScrollListeners != null && mOnDrawerScrollListeners.size() > 1) {
            Iterator<OnDrawerScrollListener> it = mOnDrawerScrollListeners.iterator();
            while (it.hasNext()) {
                OnDrawerScrollListener listener = it.next();
                if (listener != mInternalOnDrawerScrollListener) {
                    it.remove();
                }
            }
        }
    }

    private class InternalOnDrawerScrollListener implements OnDrawerScrollListener {
        @Override
        public void onDrawerOpened(SlidingDrawerLayout parent, View drawer) {
            if (DEBUG) {
                Log.d(TAG, (drawer == mLeftDrawer ? "left drawer" : "right drawer")
                        + " is open");
            }
        }

        @Override
        public void onDrawerClosed(SlidingDrawerLayout parent, View drawer) {
            if (DEBUG) {
                Log.d(TAG, (drawer == mLeftDrawer ? "left drawer" : "right drawer")
                        + " is closed");
            }
        }

        @Override
        public void onScrollPercentChange(SlidingDrawerLayout parent, View drawer, float percent) {
            if (DEBUG) {
                Log.d(TAG, (drawer == mLeftDrawer ? "left drawer" : "right drawer")
                        + " scroll percentage changes: percentage= " + percent);
            }
        }

        @Override
        public void onScrollStateChange(SlidingDrawerLayout parent, View drawer, int state) {
            if (DEBUG) {
                Log.d(TAG, (drawer == mLeftDrawer ? "left drawer" : "right drawer")
                        + " scroll state changes: state= " + state);
            }
            switch (state) {
                case SCROLL_STATE_TOUCH_SCROLL:
                case SCROLL_STATE_AUTO_SCROLL:
                    if (mScrollPercent == 0) {
                        LayoutParams lp = (LayoutParams) mContentView.getLayoutParams();
                        lp.finalLeft = lp.startLeft + (drawer == mLeftDrawer ?
                                drawer.getWidth() : -drawer.getWidth());
                    }
                    break;
                case SCROLL_STATE_IDLE:
                    if (mScrollPercent == 0) {
                        mShownDrawer = null;
                    }
                    break;
            }
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
        void onDrawerOpened(SlidingDrawerLayout parent, View drawer);

        /**
         * Callback that will be called on the dragged drawer closed.
         *
         * @param parent the layout that drawer belongs to
         * @param drawer the drawer currently being dragged
         */
        void onDrawerClosed(SlidingDrawerLayout parent, View drawer);

        /**
         * Callback to be called when the scroll percentage of the dragged drawer changes.
         *
         * @param parent  the current layout
         * @param drawer  the drawer currently being dragged
         * @param percent the scroll percentage of the dragged drawer
         */
        void onScrollPercentChange(SlidingDrawerLayout parent, View drawer, float percent);

        /**
         * Callback to be called when the scroll state{@code mFlags & SCROLL_STATE_MASK}
         * of the dragged drawer changes.
         *
         * @param parent the current layout
         * @param drawer the drawer currently being dragged
         * @param state  the scroll state of the dragged drawer
         */
        void onScrollStateChange(SlidingDrawerLayout parent, View drawer, @ScrollState int state);
    }
}
