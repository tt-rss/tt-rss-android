package org.fox.ttrss.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import androidx.preference.PreferenceManager;

import org.fox.ttrss.CommonActivity;
import org.fox.ttrss.OnlineActivity;
import org.fox.ttrss.R;

public class SmallWidgetProvider extends AppWidgetProvider {
    private static final String TAG = SmallWidgetProvider.class.getSimpleName();

    public static final String ACTION_REQUEST_UPDATE = "org.fox.ttrss.WIDGET_FORCE_UPDATE";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Log.d(TAG, "onUpdate");

        Intent intent = new Intent(context, OnlineActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_small);
        views.setOnClickPendingIntent(R.id.widget_main, pendingIntent);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String widgetBackground = prefs.getString("widget_background", "WB_LIGHT");

        Log.d(TAG, "widget bg: " + widgetBackground);

        if ("WB_LIGHT".equals(widgetBackground)) {
            views.setViewVisibility(R.id.widget_dark, View.INVISIBLE);
            views.setViewVisibility(R.id.widget_light, View.VISIBLE);
        } else if ("WB_DARK".equals(widgetBackground)) {
            views.setViewVisibility(R.id.widget_dark, View.VISIBLE);
            views.setViewVisibility(R.id.widget_light, View.INVISIBLE);
        } else {
            views.setViewVisibility(R.id.widget_dark, View.INVISIBLE);
            views.setViewVisibility(R.id.widget_light, View.INVISIBLE);
        }

        appWidgetManager.updateAppWidget(appWidgetIds, views);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive: " + intent);

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        ComponentName thisAppWidget = new ComponentName(context.getPackageName(), SmallWidgetProvider.class.getName());
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget);

        onUpdate(context, appWidgetManager, appWidgetIds);

        if (AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(intent.getAction()) ||
                ACTION_REQUEST_UPDATE.equals(intent.getAction())) {

            Log.d(TAG, "sheduling widget update...");

            CommonActivity.requestWidgetUpdate(context);
        }
    }

}
