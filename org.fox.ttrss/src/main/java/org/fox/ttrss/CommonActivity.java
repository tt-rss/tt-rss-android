package org.fox.ttrss;


import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Dialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.browser.customtabs.CustomTabsCallback;
import androidx.browser.customtabs.CustomTabsClient;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.browser.customtabs.CustomTabsServiceConnection;
import androidx.browser.customtabs.CustomTabsSession;
import androidx.core.app.JobIntentService;
import androidx.core.content.FileProvider;
import androidx.preference.PreferenceManager;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import org.fox.ttrss.widget.SmallWidgetProvider;
import org.fox.ttrss.widget.WidgetUpdateService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CommonActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = CommonActivity.class.getSimpleName();

    public final static String FRAG_HEADLINES = "headlines";
    public final static String FRAG_ARTICLE = "article";
    public final static String FRAG_FEEDS = "feeds";
    public final static String FRAG_CATS = "cats";

    public final static String THEME_DEFAULT = "THEME_FOLLOW_DEVICE";

    public final static String NOTIFICATION_CHANNEL_NORMAL = "channel_normal";
    public final static String NOTIFICATION_CHANNEL_PRIORITY = "channel_priority";

    public static final int EXCERPT_MAX_LENGTH = 256;
    public static final int LABEL_BASE_INDEX = -1024;

    public static final int PENDING_INTENT_CHROME_SHARE = 1;

    private boolean m_smallScreenMode = true;
    protected String m_theme;
    private boolean m_needRestart;

    private static String s_customTabPackageName;

    static final String STABLE_PACKAGE = "com.android.chrome";
    static final String BETA_PACKAGE = "com.chrome.beta";
    static final String DEV_PACKAGE = "com.chrome.dev";
    static final String LOCAL_PACKAGE = "com.google.android.apps.chrome";
    private static final String ACTION_CUSTOM_TABS_CONNECTION =
            "android.support.customtabs.action.CustomTabsService";

    private static String getCustomTabPackageName(Context context) {
        if (s_customTabPackageName != null) return s_customTabPackageName;

        PackageManager pm = context.getPackageManager();
        // Get default VIEW intent handler.
        Intent activityIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.example.com"));
        ResolveInfo defaultViewHandlerInfo = pm.resolveActivity(activityIntent, 0);
        String defaultViewHandlerPackageName = null;
        if (defaultViewHandlerInfo != null) {
            defaultViewHandlerPackageName = defaultViewHandlerInfo.activityInfo.packageName;
        }

        // Get all apps that can handle VIEW intents.
        List<ResolveInfo> resolvedActivityList = pm.queryIntentActivities(activityIntent, 0);
        List<String> packagesSupportingCustomTabs = new ArrayList<>();
        for (ResolveInfo info : resolvedActivityList) {
            Intent serviceIntent = new Intent();
            serviceIntent.setAction(ACTION_CUSTOM_TABS_CONNECTION);
            serviceIntent.setPackage(info.activityInfo.packageName);
            if (pm.resolveService(serviceIntent, 0) != null) {
                packagesSupportingCustomTabs.add(info.activityInfo.packageName);
            }
        }

        // Now packagesSupportingCustomTabs contains all apps that can handle both VIEW intents
        // and service calls.
        if (packagesSupportingCustomTabs.isEmpty()) {
            s_customTabPackageName = null;
        } else if (packagesSupportingCustomTabs.size() == 1) {
            s_customTabPackageName = packagesSupportingCustomTabs.get(0);
        } else if (!TextUtils.isEmpty(defaultViewHandlerPackageName)
                && packagesSupportingCustomTabs.contains(defaultViewHandlerPackageName)) {
            s_customTabPackageName = defaultViewHandlerPackageName;
        } else if (packagesSupportingCustomTabs.contains(STABLE_PACKAGE)) {
            s_customTabPackageName = STABLE_PACKAGE;
        } else if (packagesSupportingCustomTabs.contains(BETA_PACKAGE)) {
            s_customTabPackageName = BETA_PACKAGE;
        } else if (packagesSupportingCustomTabs.contains(DEV_PACKAGE)) {
            s_customTabPackageName = DEV_PACKAGE;
        } else if (packagesSupportingCustomTabs.contains(LOCAL_PACKAGE)) {
            s_customTabPackageName = LOCAL_PACKAGE;
        }

        return s_customTabPackageName;
    }

    protected CustomTabsClient m_customTabClient;
    protected CustomTabsServiceConnection m_customTabServiceConnection = new CustomTabsServiceConnection() {
        @Override
        public void onCustomTabsServiceConnected(ComponentName componentName, CustomTabsClient customTabsClient) {
            m_customTabClient = customTabsClient;

            m_customTabClient.warmup(0);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            m_customTabClient = null;
        }
    };

    protected SharedPreferences m_prefs;

    protected void setSmallScreen(boolean smallScreen) {
        Log.d(TAG, "m_smallScreenMode=" + smallScreen);
        m_smallScreenMode = smallScreen;
    }

    public boolean getUnreadOnly() {
        return m_prefs.getBoolean("show_unread_only", true);
    }

    // not the same as isSmallScreen() which is mostly about layout being loaded
    public boolean isTablet() {
        return getResources().getConfiguration().smallestScreenWidthDp >= 600;
    }

    public void setUnreadOnly(boolean unread) {
        SharedPreferences.Editor editor = m_prefs.edit();
        editor.putBoolean("show_unread_only", unread);
        editor.apply();
    }

    public void toast(int msgId) {
        toast(getString(msgId));
    }

    public void toast(String msg) {
        Snackbar.make(findViewById(android.R.id.content), msg, Snackbar.LENGTH_LONG)
                .setAction(R.string.dialog_close, v -> {

                })
                .show();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (m_needRestart) {
            Log.d(TAG, "restart requested");

            finish();
            startActivity(getIntent());
        }
    }

    @Override
    public void onDestroy() {

        if (m_customTabServiceConnection != null) {
            unbindService(m_customTabServiceConnection);
        }

        super.onDestroy();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nmgr = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

            // todo: human readable names

            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_PRIORITY,
                    NOTIFICATION_CHANNEL_PRIORITY,
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setShowBadge(false);
            channel.setSound(null, null);
            nmgr.createNotificationChannel(channel);

            channel = new NotificationChannel(NOTIFICATION_CHANNEL_NORMAL,
                    NOTIFICATION_CHANNEL_NORMAL,
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setShowBadge(false);
            channel.setSound(null, null);
            nmgr.createNotificationChannel(channel);
        }

        m_prefs = PreferenceManager
                .getDefaultSharedPreferences(getApplicationContext());

        m_prefs.registerOnSharedPreferenceChangeListener(this);

        if (m_prefs.getBoolean("window_secure_mode", false))
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        setupWidgetUpdates(this);

        if (savedInstanceState == null) {
            m_theme = m_prefs.getString("theme", CommonActivity.THEME_DEFAULT);
        } else {
            m_theme = savedInstanceState.getString("m_theme");
        }

        String customTabPackageName = getCustomTabPackageName(this);

        boolean customTabServiceBound = CustomTabsClient.bindCustomTabsService(this, customTabPackageName != null ?
                customTabPackageName : "com.android.chrome", m_customTabServiceConnection);

        Log.d(TAG, "customTabServiceBound=" + customTabServiceBound + "; package name=" + customTabPackageName);

        super.onCreate(savedInstanceState);
    }

    @Override
    public void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);

        out.putString("m_theme", m_theme);
    }

    public boolean isSmallScreen() {
        return m_smallScreenMode;
    }

    @SuppressWarnings("deprecation")
    public boolean isPortrait() {
        Display display = getWindowManager().getDefaultDisplay();

        int width = display.getWidth();
        int height = display.getHeight();

        return width < height;
    }

    @SuppressLint({"NewApi", "ServiceCast"})
    @SuppressWarnings("deprecation")
    public void copyToClipboard(String str) {
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setText(str);

        Snackbar.make(findViewById(android.R.id.content), R.string.text_copied_to_clipboard, Snackbar.LENGTH_SHORT)
                .setAction(R.string.dialog_close, v -> {

                })
                .show();
    }

    protected void setAppTheme(SharedPreferences prefs) {
        String theme = prefs.getString("theme", CommonActivity.THEME_DEFAULT);

        Log.d(TAG, "setting theme to: " + theme);

        if ("THEME_DARK".equals(theme)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else if ("THEME_LIGHT".equals(theme)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }

        if (prefs.getBoolean("enable_dynamic_colors", false))
            setTheme(R.style.AppTheme_Dynamic);
        else
            setTheme(R.style.AppTheme);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Log.d(TAG, "onSharedPreferenceChanged:" + key);

        if ("theme".equals(key)) {
            setAppTheme(sharedPreferences);
        }

        String[] filter = new String[]{"enable_dynamic_colors", "enable_cats", "widget_update_interval",
                "headlines_swipe_to_dismiss", "headlines_mark_read_scroll", "headlines_request_size",
                "force_phone_layout", "open_on_startup", "window_secure_mode", "enable_icon_tinting"};

        m_needRestart = Arrays.asList(filter).contains(key);
    }

    private CustomTabsSession getCustomTabSession() {
        return m_customTabClient.newSession(new CustomTabsCallback() {
            @Override
            public void onNavigationEvent(int navigationEvent, Bundle extras) {
                super.onNavigationEvent(navigationEvent, extras);
            }
        });
    }

    protected Intent getShareIntent(String text, String subject) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, text);

        if (subject != null) {
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
        }

        return shareIntent;
    }

    protected void shareText(String text) {
        startActivity(Intent.createChooser(getShareIntent(text, null), text));
    }

    protected void shareText(String text, String subject) {
        startActivity(Intent.createChooser(getShareIntent(text, subject), text));
    }

    protected void shareImageFromUri(String url) {
        Glide.with(this)
                .asBitmap()
                .load(url)
                .skipMemoryCache(false)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .into(new SimpleTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                        Log.d(TAG, "image resource ready: " + resource);

                        if (resource != null) {
                            File shareFolder = new File(getCacheDir(), "shared");

                            try {
                                shareFolder.mkdirs();

                                File file = new File(shareFolder, "shared.png");

                                FileOutputStream stream = new FileOutputStream(file);
                                resource.compress(Bitmap.CompressFormat.PNG, 90, stream);
                                stream.flush();
                                stream.close();

                                Uri shareUri = FileProvider.getUriForFile(CommonActivity.this,
                                        "org.fox.ttrss.SharedFileProvider", file);

                                Intent intent = new Intent(android.content.Intent.ACTION_SEND);
                                intent.putExtra(Intent.EXTRA_STREAM, shareUri);
                                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                intent.setType("image/png");

                                startActivity(intent);

                            } catch (Exception e) {
                                e.printStackTrace();
                                toast(e.getMessage());
                            }

                        } else {
                            toast(getString(R.string.img_share_failed_to_load));
                        }
                    }
                });
    }

    private void openUriWithCustomTab(Uri uri) {
        if (m_customTabClient != null) {
            CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder(getCustomTabSession());

            builder.setStartAnimations(this, R.anim.slide_in_right, R.anim.slide_out_left);
            builder.setExitAnimations(this, R.anim.slide_in_left, R.anim.slide_out_right);

            builder.setShowTitle(true);

            Intent shareIntent = getShareIntent(uri.toString(), null);

            PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(),
                    CommonActivity.PENDING_INTENT_CHROME_SHARE, shareIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            builder.setActionButton(BitmapFactory.decodeResource(getResources(), R.drawable.baseline_share_24),
                    getString(R.string.share_article), pendingIntent);

            CustomTabsIntent intent = builder.build();

            try {
                intent.launchUrl(this, uri);
            } catch (Exception e) {
                e.printStackTrace();
                toast(e.getMessage());
            }
        }
    }

    // uses chrome custom tabs when available
    public void openUri(Uri uri) {
        boolean enableCustomTabs = m_prefs.getBoolean("enable_custom_tabs", true);
        final boolean askEveryTime = m_prefs.getBoolean("custom_tabs_ask_always", true);

        if (uri.getScheme() == null) {
            try {
                uri = Uri.parse("https:" + uri);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        final Uri finalUri = uri;

        Log.d(TAG, "openUri=" + uri + "; enableCustomTabs=" + enableCustomTabs + "; customTabClient=" + m_customTabClient);

        if (enableCustomTabs && m_customTabClient != null) {

            if (askEveryTime) {

                View dialogView = View.inflate(this, R.layout.dialog_open_link_askcb, null);
                final CheckBox askEveryTimeCB = dialogView.findViewById(R.id.open_link_ask_checkbox);

                MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                        .setView(dialogView)
                        .setMessage(uri.toString())
                        .setPositiveButton(R.string.quick_preview,
                                (dialog, which) -> {

                                    if (!askEveryTimeCB.isChecked()) {
                                        SharedPreferences.Editor editor = m_prefs.edit();
                                        editor.putBoolean("custom_tabs_ask_always", false);
                                        editor.apply();
                                    }

                                    openUriWithCustomTab(finalUri);

                                })
                        .setNegativeButton(R.string.open_with,
                                (dialog, which) -> {

                                    if (!askEveryTimeCB.isChecked()) {
                                        SharedPreferences.Editor editor = m_prefs.edit();
                                        editor.putBoolean("custom_tabs_ask_always", false);
                                        editor.putBoolean("enable_custom_tabs", false);
                                        editor.apply();
                                    }

                                    Intent intent = new Intent(Intent.ACTION_VIEW, finalUri);

                                    try {
                                        startActivity(intent);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                        toast(e.getMessage());
                                    }

                                });
						/*.setNegativeButton(R.string.cancel,
							new Dialog.OnClickListener() {
								public void onClick(DialogInterface dialog,
													int which) {

									if (!askEveryTimeCB.isChecked()) {
										SharedPreferences.Editor editor = m_prefs.edit();
										editor.putBoolean("custom_tabs_ask_always", false);
										editor.apply();
									}

								}
							});*/

                Dialog dlg = builder.create();
                dlg.show();

            } else {
                openUriWithCustomTab(uri);
            }

        } else {
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);

            try {
                startActivity(intent);
            } catch (Exception e) {
                toast(e.getMessage());
            }
        }
    }

    public static void setupWidgetUpdates(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        int updateInterval = Integer.parseInt(prefs.getString("widget_update_interval", "15")) * 60 * 1000;

        Log.d("setupWidgetUpdates", "interval= " + updateInterval);

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(ALARM_SERVICE);

        Intent intentUpdate = new Intent(context, SmallWidgetProvider.class);
        intentUpdate.setAction(SmallWidgetProvider.ACTION_REQUEST_UPDATE);

        PendingIntent pendingIntentAlarm = PendingIntent.getBroadcast(context,
                0, intentUpdate, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        alarmManager.cancel(pendingIntentAlarm);

        alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + updateInterval,
                updateInterval,
                pendingIntentAlarm);

    }

    public void displayImageCaption(String url, String htmlContent) {
        // Android doesn't give us an easy way to access title tags;
        // we'll use Jsoup on the body text to grab the title text
        // from the first image tag with this url. This will show
        // the wrong text if an image is used multiple times.
        Document doc = Jsoup.parse(htmlContent);
        Elements es = doc.getElementsByAttributeValue("src", url);
        if (!es.isEmpty()) {
            if (es.get(0).hasAttr("title")) {

                MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                        .setCancelable(true)
                        .setMessage(es.get(0).attr("title"))
                        .setPositiveButton(R.string.dialog_close, (dialog, which) -> dialog.cancel()
                        );

                Dialog dialog = builder.create();
                dialog.show();

            } else {
                toast(R.string.no_caption_to_display);
            }
        } else {
            toast(R.string.no_caption_to_display);
        }
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        Log.d(TAG, "onTrimMemory called");
        Glide.get(this).trimMemory(level);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        Log.d(TAG, "onLowMemory called");
        Glide.get(this).clearMemory();
    }

    public static void requestWidgetUpdate(Context context) {
        JobIntentService.enqueueWork(context.getApplicationContext(), WidgetUpdateService.class, 0, new Intent());
    }

    static public int dpToPx(Context context, int dp) {
        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        return Math.round(dp * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT));
    }

}

