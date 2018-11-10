# SlidingDrawerLayout

A layout derived from ViewGroup, not any other indirect container, such as FrameLayout, SlidingPaneLayout or DrawerLayout.

<div align="center">
    <img src="https://github.com/ApksHolder/SlidingDrawerLayout/blob/master/preview0.gif" width="300" hspace="0">
    <img src="https://github.com/ApksHolder/SlidingDrawerLayout/blob/master/preview1.gif" width="300" hspace="5">
</div>


## Layout File Samples:
```xml
<?xml version="1.0" encoding="utf-8"?>
<com.liuzhenlin.sliding_drawer.SlidingDrawerLayout xmlns:android="http://schemas.android.com/apk/res/android"
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
        android:layout="@layout/image_drawer"
        android:layout_gravity="start" />
    <!-- layout_gravity must be explicitly set, which will determine the drawer's placement -->

    <!-- below is your content view -->
    <RelativeLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent">

        <android.support.v7.widget.Toolbar
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
</com.liuzhenlin.sliding_drawer.SlidingDrawerLayout>
```

## Usages:
```Java
/**
 * @author 刘振林
 */
public class MainActivity extends AppCompatActivity implements SlidingDrawerLayout.OnDrawerScrollListener {
    private DrawerArrowDrawable mHomeAsUpIndicator;
    private Toolbar mToolbar;
    @ColorInt
    private int mColorPrimary = INVALID_COLOR;
    private static final int INVALID_COLOR = -1;

    private SlidingDrawerLayout mSlidingDrawerLayout;
    private ListView mListView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        final int screenWidth = metrics.widthPixels;
        Drawable icon = ContextCompat.getDrawable(this, R.mipmap.ic_launcher_round);
        assert icon != null;
        final float width_dif = (float) icon.getIntrinsicWidth() + 20f * metrics.density;
        mSlidingDrawerLayout = findViewById(R.id.sliding_drawer_layout);
        mSlidingDrawerLayout.setContentSensitiveEdgeSize(screenWidth);
        mSlidingDrawerLayout.setStartDrawerWidthPercent(1f - width_dif / (float) screenWidth);
        mSlidingDrawerLayout.addOnDrawerScrollListener(this);
        // At this activity starting, none of the drawers of SlidingDrawerLayout are available
        // as in most cases their measurements are not yet started.
        mSlidingDrawerLayout.post(new Runnable() {
            @Override
            public void run() {
                mSlidingDrawerLayout.openDrawer(GravityCompat.START, true);
            }
        });

        mToolbar = findViewById(R.id.toolbar);
        mToolbar.post(new Runnable() {
            @Override
            public void run() {
                // This is a bit of a hack. Due to obsessive-compulsive disorder,
                // insert proper margin before the action bar's title.
                try {
                    Field field = mToolbar.getClass().getDeclaredField("mNavButtonView");
                    field.setAccessible(true);
                    View button = (View) field.get(mToolbar); // normally an ImageButton

                    mToolbar.setTitleMarginStart((int) (width_dif - button.getWidth() + 0.5f));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        mHomeAsUpIndicator = new DrawerArrowDrawable(this);
        mHomeAsUpIndicator.setGapSize(12f);
        mHomeAsUpIndicator.setColor(Color.WHITE);

        setSupportActionBar(mToolbar);
        ActionBar actionBar = getSupportActionBar();
        assert actionBar != null;
        actionBar.setHomeAsUpIndicator(mHomeAsUpIndicator);
        actionBar.setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_TITLE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                SystemBarUtils.setTransparentStatus(getWindow());
            } else {
                SystemBarUtils.setTranslucentStatus(getWindow(), true);
            }

            final int statusHeight = SystemBarUtils.getStatusHeight(this);
            mToolbar.getLayoutParams().height += statusHeight;
            mToolbar.setPadding(mToolbar.getPaddingLeft(),
                    mToolbar.getPaddingTop() + statusHeight,
                    mToolbar.getPaddingRight(),
                    mToolbar.getPaddingBottom());
        }

        mListView = findViewById(R.id.listview);
        mListView.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() {
                return 20;
            }

            @Override
            public Object getItem(int position) {
                return "itemView " + position;
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @SuppressLint("SetTextI18n")
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = View.inflate(parent.getContext(), R.layout.item_list, null);
                }
                convertView.setTag(position);

                ((TextView) convertView).setText((String) getItem(position));
                return convertView;
            }
        });
        mListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                Toast.makeText(view.getContext(), "itemView " + position
                        + " long clicked", Toast.LENGTH_SHORT).show();
                return true;
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_see_github, menu);
        return true;
    }

    @SuppressLint("RtlHardcoded")
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                if (mSlidingDrawerLayout.hasDrawerOpen())
                    mSlidingDrawerLayout.closeDrawer(true);
                else
                    mSlidingDrawerLayout.openDrawer(GravityCompat.START, true);
                return true;
            case R.id.option_see_github:
                startActivity(new Intent(Intent.ACTION_VIEW)
                        .setData(Uri.parse("https://github.com/freeze-frame/SlidingDrawerLayout")));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onDrawerOpened(@NonNull SlidingDrawerLayout parent, @NonNull View drawer) {

    }

    @Override
    public void onDrawerClosed(@NonNull SlidingDrawerLayout parent, @NonNull View drawer) {

    }

    @Override
    public void onScrollPercentChange(@NonNull SlidingDrawerLayout parent, @NonNull View drawer, float percent) {
        mHomeAsUpIndicator.setProgress(percent);

        final boolean light = percent >= 0.5f;
        final int alpha = (int) (0x7F + 0xFF * Math.abs(0.5f - percent) + 0.5f) << 24;

        if (mColorPrimary == INVALID_COLOR)
            mColorPrimary = ContextCompat.getColor(this, R.color.colorPrimary);
        final int background = (light ? Color.WHITE : mColorPrimary) & 0X00FFFFFF | alpha;
        mToolbar.setBackgroundColor(background);

        final int foreground = (light ? Color.BLACK : Color.WHITE) & 0X00FFFFFF | alpha;
        mHomeAsUpIndicator.setColor(foreground);
        mToolbar.setTitleTextColor(foreground);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            SystemBarUtils.setLightStatus(getWindow(), light);
            // MIUI6...
        } else if (OSHelper.getMiuiVersion() >= 6) {
            SystemBarUtils.setLightStatusForMIUI(getWindow(), light);
            // FlyMe4...
        } else if (OSHelper.isFlyme4OrLater()) {
            SystemBarUtils.setLightStatusForFlyme(getWindow(), light);

        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            SystemBarUtils.setTranslucentStatus(getWindow(), light);
        }
    }

    @Override
    public void onScrollStateChange(@NonNull SlidingDrawerLayout parent, @NonNull View drawer,
                                    @SlidingDrawerLayout.ScrollState int state) {
        switch (state) {
            case SlidingDrawerLayout.SCROLL_STATE_TOUCH_SCROLL:
            case SlidingDrawerLayout.SCROLL_STATE_AUTO_SCROLL:
                mListView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                break;
            case SlidingDrawerLayout.SCROLL_STATE_IDLE:
                mListView.setLayerType(View.LAYER_TYPE_NONE, null);
                break;
        }
    }
}
```

## Download
Download via jitpack:

To get a Git project into your build:

Step 1. Add the JitPack repository in your root build.gradle at the end of repositories:
```gradle
	allprojects {
		repositories {
			...
			maven { url 'https://jitpack.io' }
		}
	}
```
Step 2. Add the dependency
```gradle
	dependencies {
	        compile 'com.github.freeze-frame:SlidingDrawerLayout:v1.2'
	}
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