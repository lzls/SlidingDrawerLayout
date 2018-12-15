# SlidingDrawerLayout [![](https://jitpack.io/v/freeze-frames/SlidingDrawerLayout.svg)](https://jitpack.io/#freeze-frames/SlidingDrawerLayout)

A layout derived from ViewGroup, not any other indirect container, such as FrameLayout, SlidingPaneLayout or DrawerLayout.

<div align="center">
    <img src="https://github.com/ApksHolder/SlidingDrawerLayout/blob/master/SlidingDrawerLayout.gif" width="300">
</div>


## Layout File Sample:
```xml
<com.liuzhenlin.slidingdrawerlayout.SlidingDrawerLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:id="@+id/sliding_drawer_layout"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    tools:context=".MainActivity">

    <!--
      ~ Use a ViewStub to lazily inflate the drawer View for the purpose of avoiding unnecessary
      ~ performance overhead before that View shown to the user.
      -->
    <ViewStub
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:layout="@layout/image_start_drawer"
        android:layout_gravity="start" />
    <!-- layout_gravity must be explicitly set, which will determine the drawer's placement -->

    <ViewStub
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:layout="@layout/image_end_drawer"
        android:layout_gravity="end" />

    <!-- below is your content view -->
    <RelativeLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent">

        <androidx.appcompat.widget.Toolbar
            android:id="@+id/toolbar"
            android:layout_width="match_parent"
            android:layout_height="?android:attr/actionBarSize"
            app:contentInsetStartWithNavigation="0dp"
            android:background="@color/colorPrimary"
            app:title="@string/app_name"
            app:titleTextAppearance="@style/ActionBarTitleAppearance" />

        <ListView
            android:id="@+id/listview"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:layout_below="@id/toolbar"
            android:scrollbars="vertical" />

        <View
            android:layout_width="match_parent"
            android:layout_height="4dp"
            android:layout_below="@id/toolbar"
            android:background="@drawable/shadow_actionbar" />
    </RelativeLayout>
</com.liuzhenlin.slidingdrawerlayout.SlidingDrawerLayout>
```


## Usages:
### Public Methods:
- Getters & Setters:
    ```Java
    mSlidingDrawerLayout.setLeftDrawerWidthPercent(
            SlidingDrawerLayout.UNSPECIFIED_DRAWER_WIDTH_PERCENT /* default value */);
    mSlidingDrawerLayout.setRightDrawerWidthPercent(
            SlidingDrawerLayout.UNSPECIFIED_DRAWER_WIDTH_PERCENT);
    mSlidingDrawerLayout.setStartDrawerWidthPercent(0.8f);
    mSlidingDrawerLayout.setEndDrawerWidthPercent(0.9f);

    mSlidingDrawerLayout.setDrawerEnabledInTouch(Gravity.START, true /* default value */);
    mSlidingDrawerLayout.setDrawerEnabledInTouch(mEndDrawer, true);

    mSlidingDrawerLayout.setDuration(256 /* in milliseconds */);
    mSlidingDrawerLayout.setContentSensitiveEdgeSize(
            (int) (50f * getResources().getDisplayMetrics().density + 0.5f) /* in pixels */);
    mSlidingDrawerLayout.setContentFadeColor(/* ColorInt */ 0xFF_FF4081);
    ```

- Open/Close a Drawer:
    ```Java
    mSlidingDrawerLayout.openDrawer(Gravity.START, /* animate */ true);
    mSlidingDrawerLayout.openDrawer(mStartDrawer, /* animate */ true);
    mSlidingDrawerLayout.closeDrawer(/* animate */ true);
    ```

- Listener Related:
    ```Java
    final SlidingDrawerLayout.OnDrawerScrollListener listener = new SlidingDrawerLayout.OnDrawerScrollListener() {
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
    };
    mSlidingDrawerLayout.addOnDrawerScrollListener(listener);
    mSlidingDrawerLayout.removeOnDrawerScrollListener(listener);
    mSlidingDrawerLayout.clearOnDrawerScrollListeners();
    ```

### Attributes:
```xml
app:widthPercent_leftDrawer="unspecified"
app:widthPercent_rightDrawer="unspecified"
app:widthPercent_startDrawer="0.8"
app:widthPercent_endDrawer="0.9"
app:enabledInTouch_leftDrawer="true"
app:enabledInTouch_rightDrawer="true"
app:enabledInTouch_startDrawer="true"
app:enabledInTouch_endDrawer="true"
app:duration="256"
app:contentSensitiveEdgeSize="50dp"
app:contentFadeColor="@color/colorAccent"
```


## Pull Requests
I will gladly accept pull requests for bug fixes and feature enhancements but please do them
in the developers branch.


## License
Copyright 2018 刘振林

Licensed under the Apache License, Version 2.0 (the "License"); <br>
you may not use this file except in compliance with the License. You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License
is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
or implied. See the License for the specific language governing permissions and limitations
under the License.
