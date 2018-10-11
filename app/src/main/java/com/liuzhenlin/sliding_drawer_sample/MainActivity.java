package com.liuzhenlin.sliding_drawer_sample;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.ColorInt;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.graphics.drawable.DrawerArrowDrawable;
import android.support.v7.widget.Toolbar;
import android.util.DisplayMetrics;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.liuzhenlin.sliding_drawer.SlidingDrawerLayout;
import com.liuzhenlin.sliding_drawer_sample.utils.OSHelper;
import com.liuzhenlin.sliding_drawer_sample.utils.SystemBarUtils;

import java.lang.reflect.Field;

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
        mSlidingDrawerLayout.setSensibleContentEdgeSize(screenWidth);
        mSlidingDrawerLayout.setStartDrawerWidthPercent(1f - width_dif / (float) screenWidth);
        mSlidingDrawerLayout.addOnDrawerScrollListener(this);
        // During this activity starting, its content view will be measured at least twice, and
        // the width of SlidingDrawerLayout will not be correct till the second measurement is done.
        // For that reason, we need to post twice to execute our action — to open its drawer
        // immediately after the second measurement.
        mSlidingDrawerLayout.post(new Runnable() {
            @Override
            public void run() {
                mSlidingDrawerLayout.post(new Runnable() {
                    @Override
                    public void run() {
                        mSlidingDrawerLayout.openDrawer(GravityCompat.START, true);
                    }
                });
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

        ListView listView = findViewById(R.id.listview);
        listView.setAdapter(new BaseAdapter() {
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
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                Toast.makeText(view.getContext(), "itemView " + position
                        + " long clicked", Toast.LENGTH_SHORT).show();
                return true;
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (mSlidingDrawerLayout.isDrawerOpen()) {
            mSlidingDrawerLayout.closeDrawer(true);
            return;
        }
        super.onBackPressed();
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
                if (mSlidingDrawerLayout.isDrawerOpen())
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
    public void onDrawerOpened(SlidingDrawerLayout parent, View drawer) {

    }

    @Override
    public void onDrawerClosed(SlidingDrawerLayout parent, View drawer) {

    }

    @Override
    public void onScrollPercentChange(SlidingDrawerLayout parent, View drawer, float percent) {
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
    public void onScrollStateChange(SlidingDrawerLayout parent, View drawer,
                                    @SlidingDrawerLayout.ScrollState int state) {

    }
}
