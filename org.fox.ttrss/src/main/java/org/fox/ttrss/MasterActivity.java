package org.fox.ttrss;


import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.gson.JsonElement;

import org.fox.ttrss.types.Article;
import org.fox.ttrss.types.Feed;

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;

public class MasterActivity extends OnlineActivity implements HeadlinesEventListener {
    private static final String TAG = MasterActivity.class.getSimpleName();

    private static final int HEADLINES_REQUEST = 1;

    protected SharedPreferences m_prefs;
    protected long m_lastRefresh = 0;
    protected long m_lastWidgetRefresh = 0;

    private ActionBarDrawerToggle m_drawerToggle;
    private DrawerLayout m_drawerLayout;

    @SuppressLint("NewApi")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        m_prefs = PreferenceManager
                .getDefaultSharedPreferences(getApplicationContext());

        setAppTheme(m_prefs);

        super.onCreate(savedInstanceState);

        if (m_prefs.getBoolean("force_phone_layout", false)) {
            setContentView(R.layout.activity_master_phone);
        } else {
            setContentView(R.layout.activity_master);
        }

        setSmallScreen(findViewById(R.id.sw600dp_anchor) == null);

        applyEdgeToEdgeInsets();

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        Application.getInstance().load(savedInstanceState);

        enableActionModeObserver();

        m_lastWidgetRefresh = new Date().getTime();

        m_loadingProgress = findViewById(R.id.loading_progress);

        m_drawerLayout = findViewById(R.id.headlines_drawer);

        if (m_drawerLayout != null) {

            m_drawerToggle = new ActionBarDrawerToggle(this, m_drawerLayout, R.string.blank, R.string.blank) {
                @Override
                public void onDrawerOpened(View drawerView) {
                    super.onDrawerOpened(drawerView);

                    Date date = new Date();
                    if (date.getTime() - m_lastRefresh > 60 * 1000) {
                        m_lastRefresh = date.getTime();
                        refresh(false);
                    }
                }

                @Override
                public void onDrawerClosed(View drawerView) {
                    super.onDrawerClosed(drawerView);

                    if (m_prefs.getBoolean("drawer_open_on_start", true)) {
                        SharedPreferences.Editor editor = m_prefs.edit();
                        editor.putBoolean("drawer_open_on_start", false);
                        editor.apply();
                    }

                    invalidateOptionsMenu();
                }
            };

            m_drawerLayout.addDrawerListener(m_drawerToggle);
            m_drawerToggle.setDrawerIndicatorEnabled(true);

            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
        }

        if (savedInstanceState == null) {
            if (m_drawerLayout != null && m_prefs.getBoolean("drawer_open_on_start", true)) {
                m_drawerLayout.openDrawer(GravityCompat.START);
            }

            final Intent i = getIntent();
            boolean shortcutMode = i.getBooleanExtra("shortcut_mode", false);

            Log.d(TAG, "is_shortcut_mode: " + shortcutMode);

            if (shortcutMode) {
                LoginRequest lr = new LoginRequest(this, false, new OnLoginFinishedListener() {

                    @Override
                    public void OnLoginSuccess() {
                        int feedId = i.getIntExtra("feed_id", 0);
                        boolean isCat = i.getBooleanExtra("feed_is_cat", false);
                        String feedTitle = i.getStringExtra("feed_title");

                        // app shortcuts are not allowed to pass string extras
                        if (feedTitle == null)
                            feedTitle = getString(Feed.getSpecialFeedTitleId(feedId, isCat));

                        Feed tmpFeed = new Feed(feedId, feedTitle, isCat);

                        onFeedSelected(tmpFeed);
                    }

                    @Override
                    public void OnLoginFailed() {
                        login();
                    }
                });

                HashMap<String, String> map = new HashMap<>();
                map.put("op", "login");
                map.put("user", m_prefs.getString("login", "").trim());
                map.put("password", m_prefs.getString("password", "").trim());

                lr.execute(map);
            }

            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            FeedsFragment fc;

            if (m_prefs.getBoolean("enable_cats", true)) {
                fc = new RootCategoriesFragment();
                // it doesn't matter which feed is used here
                fc.initialize(new Feed(Feed.CAT_SPECIAL, getString(R.string.cat_special), true), false);
            } else {
                fc = new FeedsFragment();
                fc.initialize(new Feed(Feed.ALL_ARTICLES, getString(R.string.feed_all_articles), true), false);
            }

            ft.replace(R.id.feeds_fragment, fc, FRAG_FEEDS);

			/* FeedsFragment ff = new FeedsFragment();
			ff.initialize(new Feed(12, "Technology", true), true);
			ft.replace(R.id.feeds_fragment, ff, FRAG_FEEDS); */

            // allow overriding feed to open on startup in non-shortcut mode, default to
            // open_on_startup prefs setting and not-category

            int openFeedId = i.getIntExtra("feed_id",
                    Integer.parseInt(m_prefs.getString("open_on_startup", "0")));
            boolean openFeedIsCat = i.getBooleanExtra("feed_is_cat", false);

            String openFeedTitle = i.getStringExtra("feed_title");

            if (openFeedTitle == null)
                openFeedTitle = getString(Feed.getSpecialFeedTitleId(openFeedId, openFeedIsCat));

            if (!shortcutMode && openFeedId != 0) {
                Log.d(TAG, "opening feed id: " + openFeedId);

                onFeedSelected(new Feed(openFeedId, openFeedTitle, openFeedIsCat));
            } else if (m_drawerLayout != null) {
                m_drawerLayout.openDrawer(GravityCompat.START);
            }

            ft.commit();

        } else { // savedInstanceState != null

            if (m_drawerLayout != null && getActiveFeed() == null) {
                m_drawerLayout.openDrawer(GravityCompat.START);
            }
        }

        FloatingActionButton fab = findViewById(R.id.master_fab);

        if (fab != null) {
            fab.show();

            fab.setOnClickListener(view -> {
                HeadlinesFragment hf = (HeadlinesFragment) getSupportFragmentManager().findFragmentByTag(FRAG_HEADLINES);

                if (hf != null && hf.isAdded()) {
                    hf.refresh(false);
                }
            });
        }
    }

    private void applyEdgeToEdgeInsets() {
        // https://stackoverflow.com/questions/79018063/trying-to-understand-edge-to-edge-in-android
        // https://developer.android.com/develop/ui/views/layout/edge-to-edge

        View coordinatorView = findViewById(R.id.headlines_coordinator);

        if (coordinatorView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(coordinatorView, (v, windowInsets) -> {
                Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(0, insets.top, 0, insets.bottom);
                return windowInsets;
            });
        }

        View navigationView = findViewById(R.id.modal_navigation_view);

        if (navigationView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(navigationView, (v, windowInsets) -> {
                Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(0, insets.top, 0, insets.bottom);
                return windowInsets;
            });
        }
    }

    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Sync the toggle state after onRestoreInstanceState has occurred.
        if (m_drawerToggle != null) m_drawerToggle.syncState();
    }

    @Override
    protected void initMenu() {
        super.initMenu();

        if (m_menu != null && getSessionId() != null) {
            Fragment ff = getSupportFragmentManager().findFragmentByTag(FRAG_FEEDS);
            HeadlinesFragment hf = (HeadlinesFragment) getSupportFragmentManager().findFragmentByTag(FRAG_HEADLINES);

            m_menu.setGroupVisible(R.id.menu_group_feeds, ff != null && ff.isAdded());
            m_menu.setGroupVisible(R.id.menu_group_headlines, hf != null && hf.isAdded());
        }
    }

    public void onFeedSelected(Feed feed) {

        // show subfolder of feeds below current level
        if (feed.is_cat && !feed.always_open_headlines) {
            FragmentTransaction ft = getSupportFragmentManager()
                    .beginTransaction();

            FeedsFragment ff = new FeedsFragment();
            ff.initialize(feed, true);
            ft.replace(R.id.feeds_fragment, ff, FRAG_FEEDS);

            ft.addToBackStack(null);
            ft.commit();

        } else {
            // actualy open the feed (i.e. show headlines)

            setActiveFeed(feed);

            if (m_drawerLayout != null) {
                m_drawerLayout.closeDrawers();
            }

            HeadlinesFragment hf = (HeadlinesFragment) getSupportFragmentManager().findFragmentByTag(FRAG_HEADLINES);

            if (hf != null) {
                hf.initialize(feed);
                hf.refresh(false);
            } else {
                FragmentTransaction ft = getSupportFragmentManager()
                        .beginTransaction();

                hf = new HeadlinesFragment();
                hf.initialize(feed);

                ft.replace(R.id.headlines_fragment, hf, FRAG_HEADLINES);

                ft.commit();
            }
        }
    }


    @Override
    public void logout() {
        super.logout();

        finish();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (m_drawerToggle != null && m_drawerToggle.onOptionsItemSelected(item)) {
            return true;
        }

        if (item.getItemId() == R.id.headlines_toggle_sort_order) {
            LinkedHashMap<String, String> sortModes = getSortModes();

            CharSequence[] sortTitles = sortModes.values().toArray(new CharSequence[0]);
            final CharSequence[] sortNames = sortModes.keySet().toArray(new CharSequence[0]);

            String currentMode = getSortMode();

            int i = 0;
            int selectedIndex = 0;

            for (CharSequence tmp : sortNames) {
                if (tmp.equals(currentMode)) {
                    selectedIndex = i;
                    break;
                }

                ++i;
            }

            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.headlines_sort_articles_title))
                    .setSingleChoiceItems(
                            sortTitles,
                            selectedIndex, (dialog, which) -> {

                                try {
//										Log.d(TAG, "sort selected index:" + which + ": " + sortNames[which]);

                                    setSortMode((String) sortNames[which]);

                                } catch (IndexOutOfBoundsException e) {
                                    e.printStackTrace();
                                }

                                dialog.cancel();

                                refresh();
                            });

            Dialog dialog = builder.create();
            dialog.show();

            return true;
        }
        Log.d(TAG, "onOptionsItemSelected, unhandled id=" + item.getItemId());
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (m_drawerLayout != null && !m_drawerLayout.isDrawerOpen(GravityCompat.START) &&
                (getSupportFragmentManager().getBackStackEntryCount() > 0 || getActiveFeed() != null)) {

            m_drawerLayout.openDrawer(GravityCompat.START);
        } else {
            try {
                super.onBackPressed();
            } catch (IllegalStateException e) {
                // java.lang.IllegalStateException: Can not perform this action after onSaveInstanceState
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void loginSuccess(boolean refresh) {
        invalidateOptionsMenu();

        if (refresh) refresh();
    }

    @Override
    public void onResume() {
        super.onResume();

        invalidateOptionsMenu();
    }

    public void onArticleSelected(Article article) {
        Article articleClone = new Article(article);

        if (articleClone.unread) {
            articleClone.unread = false;
            saveArticleUnread(articleClone);
        }

        Application.getArticlesModel().setActive(articleClone);

        if (m_prefs.getBoolean("always_open_uri", false)) {
            openUri(Uri.parse(article.link));
        } else {
            Intent intent = new Intent(MasterActivity.this, DetailActivity.class);
            intent.putExtra("feed", getActiveFeed());

            startActivityForResult(intent, HEADLINES_REQUEST);
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        Date date = new Date();

        if (isFinishing() || date.getTime() - m_lastWidgetRefresh > 60 * 1000) {
            m_lastWidgetRefresh = date.getTime();

            CommonActivity.requestWidgetUpdate(MasterActivity.this);
        }

    }

    @Override
    public void onHeadlinesLoaded(boolean appended) {
        setLoadingVisible(false);
    }

    @Override
    public void onHeadlinesLoadingProgress(int progress) {
        setLoadingVisible(progress < 100);
        setLoadingProgress(progress);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        Log.d(TAG, "onActivityResult:" + requestCode + " " + resultCode + " " + data);

        if (requestCode == HEADLINES_REQUEST) {
            HeadlinesFragment hf = (HeadlinesFragment) getSupportFragmentManager().findFragmentByTag(FRAG_HEADLINES);

            if (hf != null) {
                Article activeArticle = Application.getArticlesModel().getActiveArticle();

                if (activeArticle != null) {
                    Log.d(TAG, "got back from detail activity, scrolling to active article=" + activeArticle);
                    hf.scrollToArticle(activeArticle);
                }
            }
        }
    }

    public void unsubscribeFeed(final Feed feed) {
        ApiRequest req = new ApiRequest(getApplicationContext()) {
            protected void onPostExecute(JsonElement result) {
                refresh();
            }
        };

        HashMap<String, String> map = new HashMap<>();
        map.put("sid", getSessionId());
        map.put("op", "unsubscribeFeed");
        map.put("feed_id", String.valueOf(feed.id));

        req.execute(map);

    }
}
