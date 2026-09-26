package org.fox.ttrss;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.PreferenceManager;

public class PreferencesActivity extends CommonActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // we use that before parent onCreate so let's init locally
        m_prefs = PreferenceManager
                .getDefaultSharedPreferences(getApplicationContext());

        setAppTheme(m_prefs);

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_preferences);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        if (savedInstanceState == null) {
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();

            ft.replace(R.id.preferences_container, new PreferencesFragment());
            ft.commit();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

}
