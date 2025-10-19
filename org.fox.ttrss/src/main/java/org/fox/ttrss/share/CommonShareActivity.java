package org.fox.ttrss.share;

import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.fox.ttrss.ApiRequest;
import org.fox.ttrss.PreferencesActivity;
import org.fox.ttrss.R;
import org.fox.ttrss.util.SimpleLoginManager;


public abstract class CommonShareActivity extends CommonActivity {
    protected SharedPreferences m_prefs;
    protected String m_sessionId;
    protected int m_apiLevel = 0;

    private static final String TAG = CommonShareActivity.class.getSimpleName();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        m_prefs = PreferenceManager
                .getDefaultSharedPreferences(getApplicationContext());

        if (savedInstanceState != null) {
            m_sessionId = savedInstanceState.getString("m_sessionId");
            m_apiLevel = savedInstanceState.getInt("m_apiLevel");
        }

        super.onCreate(savedInstanceState);
    }

    @Override
    public void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);

        out.putString("m_sessionId", m_sessionId);
        out.putInt("m_apiLevel", m_apiLevel);
    }

    protected abstract void onLoggedIn(int requestId);

    protected abstract void onLoggingIn(int requestId);

    public void login(int requestId) {

        if (m_prefs.getString("ttrss_url", "").trim().isEmpty()) {

            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                    .setMessage(R.string.dialog_need_configure_prompt)
                    .setCancelable(false)
                    .setPositiveButton(R.string.dialog_open_preferences, (dialog, id) -> {
                        // launch preferences

                        Intent intent = new Intent(CommonShareActivity.this,
                                PreferencesActivity.class);
                        startActivityForResult(intent, 0);
                    })
                    .setNegativeButton(R.string.cancel, (dialog, id) -> dialog.cancel());
            Dialog alert = builder.create();
            alert.show();

        } else {

            SimpleLoginManager loginManager = new SimpleLoginManager() {

                @Override
                protected void onLoginSuccess(int requestId, String sessionId, int apiLevel) {
                    m_sessionId = sessionId;
                    m_apiLevel = apiLevel;

                    CommonShareActivity.this.onLoggedIn(requestId);
                }

                @Override
                protected void onLoginFailed(int requestId, ApiRequest ar) {
                    toast(ar.getErrorMessage());
                    setProgressBarIndeterminateVisibility(false);
                }

                @Override
                protected void onLoggingIn(int requestId) {
                    CommonShareActivity.this.onLoggingIn(requestId);
                }
            };

            String login = m_prefs.getString("login", "").trim();
            String password = m_prefs.getString("password", "").trim();

            loginManager.logIn(this, requestId, login, password);
        }
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.preferences) {
            Intent intent = new Intent(CommonShareActivity.this,
                    PreferencesActivity.class);
            startActivityForResult(intent, 0);
            return true;
        }
        Log.d(TAG,
                "onOptionsItemSelected, unhandled id=" + item.getItemId());
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.activity_share, menu);
        return true;
    }


}
