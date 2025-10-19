package org.fox.ttrss;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.PreferenceManager;

import com.google.android.material.bottomappbar.BottomAppBar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.fox.ttrss.types.Article;
import org.fox.ttrss.types.Feed;

public class DetailActivity extends OnlineActivity implements HeadlinesEventListener {
    private static final String TAG = DetailActivity.class.getSimpleName();
    protected BottomAppBar m_bottomAppBar;

    protected SharedPreferences m_prefs;

    @SuppressLint("NewApi")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        m_prefs = PreferenceManager
                .getDefaultSharedPreferences(getApplicationContext());

        setAppTheme(m_prefs);

        super.onCreate(savedInstanceState);

        if (m_prefs.getBoolean("force_phone_layout", false)) {
            setContentView(R.layout.activity_detail_phone);
        } else {
            setContentView(R.layout.activity_detail);
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        setSmallScreen(findViewById(R.id.sw600dp_anchor) == null);

        Application.getInstance().load(savedInstanceState);

        View headlines = findViewById(R.id.headlines_fragment);

        if (headlines != null)
            headlines.setVisibility(isPortrait() ? View.GONE : View.VISIBLE);

        if (!isPortrait() && !isSmallScreen()) {
            enableActionModeObserver();
        }

        m_loadingProgress = findViewById(R.id.loading_progress);

        m_bottomAppBar = findViewById(R.id.detail_bottom_appbar);

        if (m_bottomAppBar != null) {
            m_bottomAppBar.setOnMenuItemClickListener(item -> {
                Article activeArticle = Application.getArticlesModel().getActiveArticle();

                if (activeArticle != null) {
                    int itemId = item.getItemId();

                    if (itemId == R.id.article_set_labels) {
                        editArticleLabels(activeArticle);

                        return true;
                    } else if (itemId == R.id.toggle_attachments) {
                        displayAttachments(activeArticle);

                        return true;
                    } else if (itemId == R.id.article_edit_note) {
                        editArticleNote(activeArticle);

                        return true;
                    } else if (itemId == R.id.article_set_score) {
                        setArticleScore(activeArticle);

                        return true;
                    } else if (itemId == R.id.toggle_unread) {
                        Article articleClone = new Article(activeArticle);
                        articleClone.unread = !articleClone.unread;

                        saveArticleUnread(articleClone);
                    }
                }

                return false;
            });
        }

        FloatingActionButton fab = findViewById(R.id.detail_fab);

        if (fab != null) {
            if (m_prefs.getBoolean("enable_article_fab", true)) {
                fab.show();

                fab.setOnClickListener(view -> {
                    Article activeArticle = Application.getArticlesModel().getActiveArticle();

                    if (activeArticle != null)
                        openUri(Uri.parse(activeArticle.link));
                });
            } else {
                fab.hide();
            }
        }

        if (savedInstanceState == null) {
            Intent i = getIntent();

            if (i.getExtras() != null) {
                boolean shortcutMode = i.getBooleanExtra("shortcut_mode", false);

                Log.d(TAG, "is_shortcut_mode: " + shortcutMode);

                Feed tmpFeed;

                if (shortcutMode) {
                    int feedId = i.getIntExtra("feed_id", 0);
                    boolean isCat = i.getBooleanExtra("feed_is_cat", false);
                    String feedTitle = i.getStringExtra("feed_title");

                    tmpFeed = new Feed(feedId, feedTitle, isCat);
                } else {
                    tmpFeed = i.getParcelableExtra("feed");
                }

                final Feed activeFeed = tmpFeed;
                final int openedArticleId = i.getIntExtra("openedArticleId", 0);
                final String searchQuery = i.getStringExtra("searchQuery");

                setActiveFeed(activeFeed);

                FragmentTransaction ft = getSupportFragmentManager().beginTransaction();

                HeadlinesFragment hf = new HeadlinesFragment();
                hf.initialize(activeFeed, true);

                ft.replace(R.id.headlines_fragment, hf, FRAG_HEADLINES);

                ArticlePager ap = new ArticlePager();
                ap.initialize(openedArticleId, activeFeed);

                ft.replace(R.id.article_fragment, ap, FRAG_ARTICLE);
                ft.commit();

                initBottomBarMenu();
            }
        }
    }

    @Override
    public void invalidateOptionsMenu() {
        super.invalidateOptionsMenu();

        initBottomBarMenu();
    }

    protected void initBottomBarMenu() {
        if (m_bottomAppBar != null) {
            Menu menu = m_bottomAppBar.getMenu();

            menu.findItem(R.id.article_set_labels).setEnabled(getApiLevel() >= 1);
            menu.findItem(R.id.article_edit_note).setEnabled(getApiLevel() >= 1);

            Article activeArticle = Application.getArticlesModel().getActiveArticle();

            if (activeArticle != null) {
                if (activeArticle.score > 0) {
                    menu.findItem(R.id.article_set_score).setIcon(R.drawable.baseline_trending_up_24);
                } else if (activeArticle.score < 0) {
                    menu.findItem(R.id.article_set_score).setIcon(R.drawable.baseline_trending_down_24);
                } else {
                    menu.findItem(R.id.article_set_score).setIcon(R.drawable.baseline_trending_flat_24);
                }

                menu.findItem(R.id.toggle_unread).setIcon(activeArticle.unread ? R.drawable.baseline_email_24 :
                        R.drawable.baseline_drafts_24);

                menu.findItem(R.id.toggle_attachments).setVisible(activeArticle.attachments != null && !activeArticle.attachments.isEmpty());
            }
        }
    }

    @Override
    protected void loginSuccess(boolean refresh) {
        Log.d(TAG, "loginSuccess");

        invalidateOptionsMenu();

        if (refresh) refresh();
    }

    @Override
    public void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);

        Application.getInstance().save(out);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        Log.d(TAG, "onOptionsItemSelected, unhandled id=" + item.getItemId());
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void initMenu() {
        super.initMenu();

        if (m_menu != null && getSessionId() != null) {
            m_menu.setGroupVisible(R.id.menu_group_feeds, false);

            m_menu.setGroupVisible(R.id.menu_group_headlines, !isPortrait() && !isSmallScreen());

            m_menu.findItem(R.id.catchup_above).setVisible(!isSmallScreen());

            ArticlePager af = (ArticlePager) getSupportFragmentManager().findFragmentByTag(FRAG_ARTICLE);

            m_menu.setGroupVisible(R.id.menu_group_article, af != null);

            m_menu.findItem(R.id.search).setVisible(false);
        }
    }

    @Override
    public void onArticleSelected(Article article) {

        Article articleClone = new Article(article);

        if (articleClone.unread) {
            articleClone.unread = false;
            saveArticleUnread(articleClone);
        }

        Application.getArticlesModel().setActive(articleClone);
    }

    @Override
    public void onHeadlinesLoaded(boolean appended) {
        setLoadingVisible(false);

        ArticlePager ap = (ArticlePager) getSupportFragmentManager().findFragmentByTag(FRAG_ARTICLE);

        if (ap != null) {
            ap.syncToSharedArticles();
        }
    }

    @Override
    public void onHeadlinesLoadingProgress(int progress) {
        setLoadingVisible(progress < 100);
        setLoadingProgress(progress);
    }

    @Override
    public void onBackPressed() {
        Intent resultIntent = new Intent();

        setResult(Activity.RESULT_OK, resultIntent);

        try {
            super.onBackPressed();
        } catch (IllegalStateException e) {
            // java.lang.IllegalStateException: Can not perform this action after onSaveInstanceState
            e.printStackTrace();
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if (isFinishing()) {
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
        }

    }
}
