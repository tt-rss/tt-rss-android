package org.fox.ttrss;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.core.content.pm.PackageInfoCompat;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.color.DynamicColors;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class PreferencesFragment extends PreferenceFragmentCompat {
    private static final int SETTINGS_EXPORT_VERSION = 1;

    private final ActivityResultLauncher<String> m_exportLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/json"), this::exportSettings);

    private final ActivityResultLauncher<String[]> m_importLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::importSettings);

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

        findPreference("export_settings").setOnPreferenceClickListener(preference -> {
            String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
            m_exportLauncher.launch("tt-rss-settings-" + timestamp + ".json");
            return false;
        });

        findPreference("import_settings").setOnPreferenceClickListener(preference -> {
            m_importLauncher.launch(new String[] { "application/json", "text/plain", "application/octet-stream" });
            return false;
        });

        CommonActivity activity = (CommonActivity) getActivity();

        findPreference("force_phone_layout").setEnabled(activity.isTablet());

        setupPreferredBrowserPreference(activity, prefs);

        try {
            String version;
            long versionCode;
            String buildTimestamp;

            PackageInfo packageInfo = activity.getPackageManager().
                    getPackageInfo(activity.getPackageName(), 0);

            version = packageInfo.versionName;
            versionCode = PackageInfoCompat.getLongVersionCode(packageInfo);

            findPreference("version").setSummary(getString(R.string.prefs_version, version, versionCode));

            buildTimestamp = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.getDefault(Locale.Category.FORMAT)).format(new Date(BuildConfig.TIMESTAMP));

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

    private void exportSettings(@Nullable Uri uri) {
        if (uri == null) return;

        CommonActivity activity = (CommonActivity) requireActivity();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity.getApplicationContext());

        try {
            JSONObject values = new JSONObject();

            for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
                Object value = entry.getValue();
                JSONObject item = new JSONObject();

                if (value instanceof Boolean) {
                    item.put("type", "boolean");
                } else if (value instanceof Integer) {
                    item.put("type", "int");
                } else if (value instanceof Long) {
                    item.put("type", "long");
                } else if (value instanceof Float) {
                    item.put("type", "float");
                    value = value.toString();
                } else if (value instanceof String) {
                    item.put("type", "string");
                } else if (value instanceof Set) {
                    item.put("type", "string_set");
                    value = new JSONArray((Set<?>) value);
                } else {
                    continue;
                }

                item.put("value", value);
                values.put(entry.getKey(), item);
            }

            JSONObject root = new JSONObject();
            root.put("version", SETTINGS_EXPORT_VERSION);
            root.put("package", activity.getPackageName());
            root.put("prefs", values);

            try (OutputStream out = activity.getContentResolver().openOutputStream(uri, "wt")) {
                if (out == null) throw new IllegalStateException("unable to open output stream");

                out.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
            }

            activity.toast(R.string.settings_exported);
        } catch (Exception e) {
            e.printStackTrace();
            activity.toast(getString(R.string.settings_export_failed, e.getMessage()));
        }
    }

    private void importSettings(@Nullable Uri uri) {
        if (uri == null) return;

        CommonActivity activity = (CommonActivity) requireActivity();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity.getApplicationContext());

        try {
            StringBuilder json = new StringBuilder();

            try (InputStream in = activity.getContentResolver().openInputStream(uri)) {
                if (in == null) throw new IllegalStateException("unable to open input stream");

                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String line;

                while ((line = reader.readLine()) != null)
                    json.append(line).append('\n');
            }

            JSONObject root = new JSONObject(json.toString());

            if (root.optInt("version", 0) != SETTINGS_EXPORT_VERSION)
                throw new IllegalArgumentException("unsupported file format");

            JSONObject values = root.getJSONObject("prefs");
            // replace current settings completely so the result matches the exported state
            SharedPreferences.Editor editor = prefs.edit().clear();

            for (Iterator<String> keys = values.keys(); keys.hasNext(); ) {
                String key = keys.next();

                JSONObject item = values.getJSONObject(key);

                switch (item.getString("type")) {
                    case "boolean":
                        editor.putBoolean(key, item.getBoolean("value"));
                        break;
                    case "int":
                        editor.putInt(key, item.getInt("value"));
                        break;
                    case "long":
                        editor.putLong(key, item.getLong("value"));
                        break;
                    case "float":
                        editor.putFloat(key, Float.parseFloat(item.getString("value")));
                        break;
                    case "string":
                        editor.putString(key, item.getString("value"));
                        break;
                    case "string_set":
                        JSONArray array = item.getJSONArray("value");
                        Set<String> set = new HashSet<>();

                        for (int i = 0; i < array.length(); i++)
                            set.add(array.getString(i));

                        editor.putStringSet(key, set);
                        break;
                }
            }

            editor.commit();

            activity.toast(R.string.settings_imported);

            // theme, layout and other settings are applied on activity creation
            activity.recreate();
        } catch (Exception e) {
            e.printStackTrace();
            activity.toast(getString(R.string.settings_import_failed, e.getMessage()));
        }
    }
}
