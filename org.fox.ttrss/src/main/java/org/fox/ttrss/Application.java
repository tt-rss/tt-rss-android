package org.fox.ttrss;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Bundle;

import androidx.core.os.BundleCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.color.DynamicColors;

import org.acra.ACRA;
import org.acra.ReportField;
import org.acra.config.CoreConfigurationBuilder;
import org.acra.config.DialogConfigurationBuilder;
import org.acra.config.MailSenderConfigurationBuilder;
import org.acra.data.StringFormat;
import org.fox.ttrss.types.Article;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Application extends android.app.Application {

    private static final String PREF_SESSION_ID = "gs:sessionId";
    private static final String PREF_API_LEVEL = "gs:apiLevel";

    private static Application m_singleton;

    private String m_sessionId;
    private int m_apiLevel;

    // True only after a successful op:login in *this* process. A sessionId
    // rehydrated from SharedPreferences in onCreate() is left unverified
    // (false), because the server may have expired it while the process
    // was dead. onResume() uses this to decide whether to skip login.
    // volatile: read/written from background ApiRequest threads.
    private volatile boolean m_sessionValid = false;

    public LinkedHashMap<String, String> m_customSortModes = new LinkedHashMap<>();
    ConnectivityManager m_cmgr;
    ArticleModel m_articleModel;

    public static Application getInstance() {
        return m_singleton;
    }

    public static List<Article> getArticles() {
        return getInstance().m_articleModel.getArticles().getValue();
    }

    public static ArticleModel getArticlesModel() {
        return getInstance().m_articleModel;
    }

    @Override
    public final void onCreate() {
        super.onCreate();

        DynamicColors.applyToActivitiesIfAvailable(this);

        m_singleton = this;
        m_cmgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        m_articleModel = new ArticleModel(this);

        // Rehydrate the persisted sessionId so a restored activity *could* reuse
        // it without a forced re-login — but do NOT mark the session valid.
        // The server may have expired it while the process was dead, so
        // onResume() must re-validate (login) before trusting it. See
        // 5e5d64e4 which added persistence and caused the stale-session bug.
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        m_sessionId = prefs.getString(PREF_SESSION_ID, null);
        m_apiLevel = prefs.getInt(PREF_API_LEVEL, 0);
        // m_sessionValid intentionally stays false here.
    }

    public String getSessionId() {
        return m_sessionId;
    }

    public void setSessionId(String sessionId) {
        m_sessionId = sessionId;
        PreferenceManager.getDefaultSharedPreferences(this)
                .edit()
                .putString(PREF_SESSION_ID, sessionId)
                .apply();
        // Any change to the session id invalidates the "authenticated this
        // process" flag. Only the successful-login path re-sets it to true,
        // so callers that just clear the id no longer need to touch it.
        m_sessionValid = false;
    }

    public int getApiLevel() {
        return m_apiLevel;
    }

    public void setApiLevel(int apiLevel) {
        m_apiLevel = apiLevel;
        PreferenceManager.getDefaultSharedPreferences(this)
                .edit()
                .putInt(PREF_API_LEVEL, apiLevel)
                .apply();
    }

    public boolean isSessionValid() {
        return m_sessionValid;
    }

    public void setSessionValid(boolean valid) {
        m_sessionValid = valid;
    }

    public void save(Bundle out) {

        out.setClassLoader(getClass().getClassLoader());
        out.putString("gs:sessionId", m_sessionId);
        out.putInt("gs:apiLevel", m_apiLevel);
        out.putSerializable("gs:customSortTypes", m_customSortModes);
    }

    public void load(Bundle in) {
        if (in != null) {
            m_sessionId = in.getString("gs:sessionId");
            m_apiLevel = in.getInt("gs:apiLevel");

            HashMap<?, ?> tmp = BundleCompat.getSerializable(in, "gs:customSortTypes", HashMap.class);

            m_customSortModes.clear();

            if (tmp != null) {
                for (Map.Entry<?, ?> entry : tmp.entrySet()) {
                    if (entry.getKey() instanceof String && entry.getValue() instanceof String) {
                        m_customSortModes.put((String) entry.getKey(), (String) entry.getValue());
                    }
                }
            }
        }
    }

    public boolean isWifiConnected() {
        NetworkCapabilities capabilities = m_cmgr.getNetworkCapabilities(m_cmgr.getActiveNetwork());

        return capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);

        if (!BuildConfig.DEBUG)
            ACRA.init(this, new CoreConfigurationBuilder()
                    .withBuildConfigClass(BuildConfig.class)
                    .withReportContent(ReportField.APP_VERSION_NAME, ReportField.APP_VERSION_CODE, ReportField.STACK_TRACE)
                    .withReportFormat(StringFormat.KEY_VALUE_LIST)
                    .withPluginConfigurations(
                            new DialogConfigurationBuilder()
                                    .withText(getString(R.string.crash_dialog_text_email))
                                    .withResTheme(R.style.Theme_AppCompat_Dialog)
                                    .build(),
                            new MailSenderConfigurationBuilder()
                                    .withMailTo("cthulhoo+ttrss-acra@gmail.com")
                                    .withReportAsFile(true)
                                    .withReportFileName("crash.txt")
                                    .build()
                    )
                    .build());
    }
}
