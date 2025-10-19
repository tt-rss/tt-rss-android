package org.fox.ttrss.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;
import androidx.core.app.JobIntentService;
import androidx.preference.PreferenceManager;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.fox.ttrss.ApiRequest;
import org.fox.ttrss.OnlineActivity;
import org.fox.ttrss.R;
import org.fox.ttrss.util.SimpleLoginManager;

import java.util.HashMap;

public class WidgetUpdateService extends JobIntentService {
    private static final String TAG = WidgetUpdateService.class.getSimpleName();
    private SharedPreferences m_prefs;

    public static final int UPDATE_RESULT_OK = 0;
    public static final int UPDATE_RESULT_ERROR_LOGIN = 1;
    public static final int UPDATE_RESULT_ERROR_OTHER = 2;
    public static final int UPDATE_RESULT_ERROR_NEED_CONF = 3;
    public static final int UPDATE_IN_PROGRESS = 4;

    @Override
    protected void onHandleWork(@NonNull Intent intent) {

        Log.d(TAG, "onHandleWork: " + intent);

        if (getWidgetCount(getApplicationContext()) == 0) {
            Log.d(TAG, "no widgets to work on, bailing out");

            stopSelf();
            return;
        }

        try {
            updateWidgets(-1, UPDATE_IN_PROGRESS);

            if (!isNetworkAvailable()) {
                final int retryCount = intent.getIntExtra("retryCount", 0);

                Log.d(TAG, "service update requested but network is not available, try: " + retryCount);

                if (retryCount < 10) {
                    new Handler().postDelayed(() -> {
                        Intent serviceIntent = new Intent(getApplicationContext(), WidgetUpdateService.class);
                        serviceIntent.putExtra("retryCount", retryCount + 1);

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(serviceIntent);
                        } else {
                            startService(serviceIntent);
                        }

                    }, 3 * 1000);
                } else {
                    updateWidgets(-1, UPDATE_RESULT_ERROR_OTHER);
                }

                stopSelf();
                return;
            }

            m_prefs = PreferenceManager
                    .getDefaultSharedPreferences(getApplicationContext());

            if (m_prefs.getString("ttrss_url", "").trim().isEmpty()) {

                updateWidgets(-1, UPDATE_RESULT_ERROR_NEED_CONF);

            } else {

                final int feedId = m_prefs.getBoolean("widget_show_fresh", true) ? -3 : 0;

                final SimpleLoginManager loginManager = new SimpleLoginManager() {

                    @Override
                    protected void onLoginSuccess(int requestId, String sessionId, int apiLevel) {

                        ApiRequest aru = new ApiRequest(getApplicationContext()) {
                            @Override
                            protected void onPostExecute(JsonElement result) {

                                if (result != null) {
                                    try {
                                        JsonObject content = result.getAsJsonObject();

                                        if (content != null) {

                                            int unread = content.get("unread").getAsInt();
                                            updateWidgets(unread, UPDATE_RESULT_OK);

                                            return;
                                        }
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                } else {
                                    Log.d(TAG, "request failed: " + getErrorMessage());
                                }

                                updateWidgets(-1, UPDATE_RESULT_ERROR_OTHER);
                            }
                        };

                        final String fSessionId = sessionId;

                        HashMap<String, String> umap = new HashMap<>();
                        umap.put("op", "getUnread");
                        umap.put("feed_id", String.valueOf(feedId));
                        umap.put("sid", fSessionId);

                        aru.execute(umap);
                    }

                    @Override
                    protected void onLoginFailed(int requestId, ApiRequest ar) {
                        Log.d(TAG, "login failed: " + getString(ar.getErrorMessage()));

                        updateWidgets(-1, UPDATE_RESULT_ERROR_LOGIN);
                    }

                    @Override
                    protected void onLoggingIn(int requestId) {


                    }
                };

                String login = m_prefs.getString("login", "").trim();
                String password = m_prefs.getString("password", "").trim();

                loginManager.logIn(getApplicationContext(), 1, login, password);

            }
        } catch (Exception e) {
            e.printStackTrace();

            updateWidgets(-1, UPDATE_RESULT_ERROR_OTHER);
        }

        stopSelf();

    }

    private int getWidgetCount(Context context) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        ComponentName thisAppWidget = new ComponentName(context.getPackageName(), SmallWidgetProvider.class.getName());

        return appWidgetManager.getAppWidgetIds(thisAppWidget).length;
    }

    protected boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager)
                getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();

        // if no network is available networkInfo will be null
        // otherwise check if we are connected
        return networkInfo != null && networkInfo.isConnected();
    }

    public void updateWidgets(int unread, int resultCode) {
        Log.d(TAG, "updateWidgets:" + unread + " " + resultCode);

        Context context = getApplicationContext();

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        ComponentName thisAppWidget = new ComponentName(context.getPackageName(), SmallWidgetProvider.class.getName());
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget);

        updateWidgetsText(context, appWidgetManager, appWidgetIds, unread, resultCode);

        if (resultCode != UPDATE_IN_PROGRESS) stopSelf();
    }

    private void updateWidgetsText(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds, int unread, int resultCode) {

        Intent intent = new Intent(context, OnlineActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_small);
        views.setOnClickPendingIntent(R.id.widget_main, pendingIntent);

        String viewText;

        switch (resultCode) {
            case WidgetUpdateService.UPDATE_RESULT_OK:
                viewText = String.valueOf(unread);
                break;
            case WidgetUpdateService.UPDATE_IN_PROGRESS:
                viewText = "...";
                break;
            default:
                viewText = "?";
        }

        views.setTextViewText(R.id.widget_unread_counter, viewText);

        appWidgetManager.updateAppWidget(appWidgetIds, views);
    }

}
