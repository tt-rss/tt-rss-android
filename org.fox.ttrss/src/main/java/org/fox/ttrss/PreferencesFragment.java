package org.fox.ttrss;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.color.DynamicColors;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PreferencesFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());

        findPreference("ttrss_url").setSummary(prefs.getString("ttrss_url", getString(R.string.ttrss_url_summary)));
        findPreference("login").setSummary(prefs.getString("login", getString(R.string.login_summary)));

        findPreference("show_logcat").setOnPreferenceClickListener(preference -> {
            Intent intent = new Intent(getActivity(), LogcatActivity.class);
            startActivity(intent);
            return false;
        });

        findPreference("enable_dynamic_colors").setEnabled(DynamicColors.isDynamicColorAvailable());

        findPreference("network_settings").setOnPreferenceClickListener(preference -> {
            getActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.preferences_container, new NetworkPreferencesFragment())
                    .addToBackStack(NetworkPreferencesFragment.class.getSimpleName())
                    .commit();

            return false;
        });

        CommonActivity activity = (CommonActivity) getActivity();

        findPreference("force_phone_layout").setEnabled(activity.isTablet());

        setupPreferredBrowserPreference(activity, prefs);

        try {
            String version;
            int versionCode;
            String buildTimestamp;

            PackageInfo packageInfo = activity.getPackageManager().
                    getPackageInfo(activity.getPackageName(), 0);

            version = packageInfo.versionName;
            versionCode = packageInfo.versionCode;

            findPreference("version").setSummary(getString(R.string.prefs_version, version, versionCode));

            buildTimestamp = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.US).format(new Date(BuildConfig.TIMESTAMP));

            findPreference("build_timestamp").setSummary(getString(R.string.prefs_build_timestamp, buildTimestamp));

        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
    }

    private void setupPreferredBrowserPreference(CommonActivity activity, SharedPreferences prefs) {
        ListPreference preferredBrowser = findPreference("preferred_browser");

        if (preferredBrowser == null) return;

        List<CharSequence> entries = new ArrayList<>();
        List<String> values = new ArrayList<>();

        entries.add(getString(R.string.prefs_preferred_browser_default));
        values.add("");

        PackageManager pm = activity.getPackageManager();

        // ACTION_VIEW alone does not reliably return every browser on all devices,
        // so also ask for apps declaring the APP_BROWSER category.
        Intent browsersIntent = new Intent(Intent.ACTION_MAIN);
        browsersIntent.addCategory(Intent.CATEGORY_APP_BROWSER);

        Intent viewIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.example.com"));

        List<ResolveInfo> resolved = new ArrayList<>(pm.queryIntentActivities(browsersIntent, 0));
        resolved.addAll(pm.queryIntentActivities(viewIntent, 0));

        Map<String, String> labels = new HashMap<>();

        for (ResolveInfo info : resolved) {
            if (info.activityInfo == null) continue;

            String packageName = info.activityInfo.packageName;

            if (activity.getPackageName().equals(packageName) || labels.containsKey(packageName))
                continue;

            labels.put(packageName, info.loadLabel(pm).toString());
        }

        List<String> sortedPackages = new ArrayList<>(labels.keySet());
        sortedPackages.sort((a, b) -> labels.get(a).compareToIgnoreCase(labels.get(b)));

        for (String packageName : sortedPackages) {
            entries.add(labels.get(packageName));
            values.add(packageName);
        }

        preferredBrowser.setEntries(entries.toArray(new CharSequence[0]));
        preferredBrowser.setEntryValues(values.toArray(new CharSequence[0]));

        String selectedPackage = prefs.getString("preferred_browser", "");

        if (!selectedPackage.isEmpty() && !values.contains(selectedPackage)) {
            prefs.edit().putString("preferred_browser", "").apply();
            selectedPackage = "";
        }

        updatePreferredBrowserSummary(preferredBrowser, labels, selectedPackage);

        preferredBrowser.setOnPreferenceChangeListener((preference, newValue) ->
                updatePreferredBrowserSummary(preferredBrowser, labels, (String) newValue));
    }

    private boolean updatePreferredBrowserSummary(ListPreference preference, Map<String, String> labels, String selectedPackage) {
        if (selectedPackage == null || selectedPackage.isEmpty()) {
            preference.setSummary(getString(R.string.prefs_preferred_browser_default));
        } else {
            String label = labels.get(selectedPackage);

            preference.setSummary(label != null ? label : selectedPackage);
        }

        return true;
    }
}
