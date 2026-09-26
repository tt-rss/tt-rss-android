package org.fox.ttrss.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.util.Log;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.fox.ttrss.ApiRequest;
import org.fox.ttrss.OnlineActivity;
import org.fox.ttrss.R;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

public class WidgetUpdateWorker extends Worker {
    private static final String TAG = WidgetUpdateWorker.class.getSimpleName();
    private SharedPreferences m_prefs;

    public static final int UPDATE_RESULT_OK = 0;
    public static final int UPDATE_RESULT_ERROR_LOGIN = 1;
    public static final int UPDATE_RESULT_ERROR_OTHER = 2;
    public static final int UPDATE_RESULT_ERROR_NEED_CONF = 3;
    public static final int UPDATE_IN_PROGRESS = 4;

    private static final String KEY_RETRY_COUNT = "retryCount";
    private static final int MAX_RETRY_COUNT = 10;
    private static final long RETRY_DELAY_SECONDS = 3;

    public WidgetUpdateWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    public static void enqueue(Context context) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(WidgetUpdateWorker.class)
                .build();

        WorkManager.getInstance(context.getApplicationContext()).enqueue(request);
    }

    @NonNull
    @Override
    public Result doWork() {

        Log.d(TAG, "doWork");

        if (getWidgetCount(getApplicationContext()) == 0) {
            Log.d(TAG, "no widgets to work on, bailing out");

            return Result.success();
        }

        try {
            updateWidgets(-1, UPDATE_IN_PROGRESS);

            if (!isNetworkAvailable()) {
                final int retryCount = getInputData().getInt(KEY_RETRY_COUNT, 0);

                Log.d(TAG, "worker update requested but network is not available, try: " + retryCount);

                if (retryCount < MAX_RETRY_COUNT) {
                    Data data = new Data.Builder()
                            .putInt(KEY_RETRY_COUNT, retryCount + 1)
                            .build();

                    OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(WidgetUpdateWorker.class)
                            .setInitialDelay(RETRY_DELAY_SECONDS, TimeUnit.SECONDS)
                            .setInputData(data)
                            .build();

                    WorkManager.getInstance(getApplicationContext()).enqueue(request);
                } else {
                    updateWidgets(-1, UPDATE_RESULT_ERROR_OTHER);
                }

                return Result.success();
            }

            m_prefs = PreferenceManager
                    .getDefaultSharedPreferences(getApplicationContext());

            if (m_prefs.getString("ttrss_url", "").trim().isEmpty()) {

                updateWidgets(-1, UPDATE_RESULT_ERROR_NEED_CONF);

            } else {

                final int feedId = m_prefs.getBoolean("widget_show_fresh", true) ? -3 : 0;

                ApiRequest loginRequest = new ApiRequest(getApplicationContext());

                HashMap<String, String> lmap = new HashMap<>();
                lmap.put("op", "login");
                lmap.put("user", m_prefs.getString("login", "").trim());
                lmap.put("password", m_prefs.getString("password", "").trim());

                JsonElement loginResult = loginRequest.executeSync(lmap);

                String sessionId = null;

                if (loginResult != null) {
                    try {
                        JsonObject content = loginResult.getAsJsonObject();

                        if (content != null && content.has("session_id")) {
                            sessionId = content.get("session_id").getAsString();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                if (sessionId == null) {
                    Log.d(TAG, "login failed: " + getApplicationContext().getString(loginRequest.getErrorMessage()));

                    updateWidgets(-1, UPDATE_RESULT_ERROR_LOGIN);

                    return Result.success();
                }

                ApiRequest unreadRequest = new ApiRequest(getApplicationContext());

                HashMap<String, String> umap = new HashMap<>();
                umap.put("op", "getUnread");
                umap.put("feed_id", String.valueOf(feedId));
                umap.put("sid", sessionId);

                JsonElement result = unreadRequest.executeSync(umap);

                if (result != null) {
                    try {
                        int unread = result.getAsJsonObject().get("unread").getAsInt();
                        updateWidgets(unread, UPDATE_RESULT_OK);

                        return Result.success();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    Log.d(TAG, "request failed: " + getApplicationContext().getString(unreadRequest.getErrorMessage()));
                }

                updateWidgets(-1, UPDATE_RESULT_ERROR_OTHER);

            }
        } catch (Exception e) {
            e.printStackTrace();

            updateWidgets(-1, UPDATE_RESULT_ERROR_OTHER);
        }

        return Result.success();
    }

    private int getWidgetCount(Context context) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        ComponentName thisAppWidget = new ComponentName(context.getPackageName(), SmallWidgetProvider.class.getName());

        return appWidgetManager.getAppWidgetIds(thisAppWidget).length;
    }

    protected boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager)
                getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkCapabilities capabilities = cm.getNetworkCapabilities(cm.getActiveNetwork());

        // if no network is available capabilities will be null
        // otherwise check if it can reach the internet
        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    public void updateWidgets(int unread, int resultCode) {
        Log.d(TAG, "updateWidgets:" + unread + " " + resultCode);

        Context context = getApplicationContext();

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        ComponentName thisAppWidget = new ComponentName(context.getPackageName(), SmallWidgetProvider.class.getName());
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget);

        updateWidgetsText(context, appWidgetManager, appWidgetIds, unread, resultCode);
    }

    private void updateWidgetsText(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds, int unread, int resultCode) {

        Intent intent = new Intent(context, OnlineActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_small);
        views.setOnClickPendingIntent(R.id.widget_main, pendingIntent);

        String viewText;

        switch (resultCode) {
            case UPDATE_RESULT_OK:
                viewText = String.valueOf(unread);
                break;
            case UPDATE_IN_PROGRESS:
                viewText = "...";
                break;
            default:
                viewText = "?";
        }

        views.setTextViewText(R.id.widget_unread_counter, viewText);

        appWidgetManager.updateAppWidget(appWidgetIds, views);
    }

}
