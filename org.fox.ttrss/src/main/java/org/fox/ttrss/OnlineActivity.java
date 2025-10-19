package org.fox.ttrss;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import org.fox.ttrss.share.SubscribeActivity;
import org.fox.ttrss.types.Article;
import org.fox.ttrss.types.Feed;
import org.fox.ttrss.types.Label;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SuppressLint("StaticFieldLeak")
public class OnlineActivity extends CommonActivity {
    private static final String TAG = OnlineActivity.class.getSimpleName();

    protected SharedPreferences m_prefs;
    protected Menu m_menu;

    private Feed m_activeFeed;

    private ActionMode m_headlinesActionMode;
    private HeadlinesActionModeCallback m_headlinesActionModeCallback;

    private String m_lastImageHitTestUrl;
    protected LinearProgressIndicator m_loadingProgress;

    public void catchupDialog(final Feed feed) {

        if (getApiLevel() >= 15) {

            int selectedIndex = 0;

            final String searchQuery = Application.getArticlesModel().getSearchQuery();

            int titleStringId = !searchQuery.isEmpty() ? R.string.catchup_dialog_title_search : R.string.catchup_dialog_title;

            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                    .setTitle(getString(titleStringId, feed.title))
                    .setSingleChoiceItems(
                            new String[]{
                                    getString(R.string.catchup_dialog_all_articles),
                                    getString(R.string.catchup_dialog_1day),
                                    getString(R.string.catchup_dialog_1week),
                                    getString(R.string.catchup_dialog_2week)
                            },
                            selectedIndex, (dialog, which) -> {
                            })
                    .setPositiveButton(R.string.catchup,
                            (dialog, which) -> {

                                ListView list = ((AlertDialog) dialog).getListView();

                                if (list.getCheckedItemCount() > 0) {
                                    int position = list.getCheckedItemPosition();

                                    String[] catchupModes = {"all", "1day", "1week", "2week"};
                                    String mode = catchupModes[position];

                                    catchupFeed(feed, mode, true, searchQuery);
                                }
                            })
                    .setNegativeButton(R.string.dialog_cancel,
                            (dialog, which) -> {

                            });

            Dialog dialog = builder.create();
            dialog.show();

        } else {
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                    .setMessage(getString(R.string.catchup_dialog_title, feed.title))
                    .setPositiveButton(R.string.catchup,
                            (dialog, which) -> catchupFeed(feed, "all", true, ""))
                    .setNegativeButton(R.string.dialog_cancel,
                            (dialog, which) -> {

                            });

            Dialog dialog = builder.create();
            dialog.show();
        }
    }

    public void confirmCatchupAbove(final Article article) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setMessage(getString(R.string.confirm_catchup_above_title, article.title))
                .setPositiveButton(R.string.dialog_ok,
                        (dialog, which) -> catchupAbove(article))
                .setNegativeButton(R.string.dialog_cancel,
                        (dialog, which) -> {
                        });

        Dialog dialog = builder.create();
        dialog.show();
    }

    //protected PullToRefreshAttacher m_pullToRefreshAttacher;

    protected static abstract class OnLoginFinishedListener {
        public abstract void OnLoginSuccess();

        public abstract void OnLoginFailed();
    }

    private class HeadlinesActionModeCallback implements ActionMode.Callback {

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            m_headlinesActionMode = null;

            Application.getArticlesModel().setSelection(ArticleModel.ArticlesSelection.NONE);

            invalidateOptionsMenu();
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {

            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.action_mode_headlines, menu);

            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            onOptionsItemSelected(item);
            return false;
        }
    }

    protected String getSessionId() {
        return Application.getInstance().getSessionId();
    }

    protected void setSessionId(String sessionId) {
        Application.getInstance().setSessionId(sessionId);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {

        // we use that before parent onCreate so let's init locally
        m_prefs = PreferenceManager
                .getDefaultSharedPreferences(getApplicationContext());

        setAppTheme(m_prefs);

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_login);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (savedInstanceState != null) {
            m_activeFeed = savedInstanceState.getParcelable("m_activeFeed");
        }

        m_headlinesActionModeCallback = new HeadlinesActionModeCallback();

        ArticleModel model = Application.getArticlesModel();
        model.getActive().observe(this, (articles) -> {
            invalidateOptionsMenu();
        });
    }

    public void login() {
        login(false, null);
    }

    public void login(boolean refresh) {
        login(refresh, null);
    }

    public void login(boolean refresh, OnLoginFinishedListener listener) {

        if (m_prefs.getString("ttrss_url", "").trim().isEmpty()) {

            setLoadingStatus(R.string.login_need_configure);

            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                    .setMessage(R.string.dialog_need_configure_prompt)
                    .setCancelable(false)
                    .setPositiveButton(R.string.dialog_open_preferences, (dialog, id) -> {
                        // launch preferences

                        Intent intent = new Intent(OnlineActivity.this,
                                PreferencesActivity.class);
                        startActivityForResult(intent, 0);
                    })
                    .setNegativeButton(R.string.cancel, (dialog, id) -> dialog.cancel());

            Dialog alert = builder.create();
            alert.show();

        } else {
            setLoadingStatus(R.string.login_in_progress);

            LoginRequest ar = new LoginRequest(getApplicationContext(), refresh, listener);

            HashMap<String, String> map = new HashMap<>();
            map.put("op", "login");
            map.put("user", m_prefs.getString("login", "").trim());
            map.put("password", m_prefs.getString("password", "").trim());

            ar.execute(map);

            setLoadingStatus(R.string.login_in_progress);
        }
    }

    protected void loginSuccess(boolean refresh) {
        setLoadingStatus(R.string.blank);

        initMenu();

        Intent intent = new Intent(OnlineActivity.this, MasterActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);

        startActivityForResult(intent, 0);
        overridePendingTransition(0, 0);

        finish();
    }

    @Override
    public boolean onContextItemSelected(android.view.MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.article_img_open) {
            if (getLastContentImageHitTestUrl() != null) {
                try {
                    openUri(Uri.parse(getLastContentImageHitTestUrl()));
                } catch (Exception e) {
                    e.printStackTrace();
                    toast(R.string.error_other_error);
                }
            }
            return true;
        } else if (itemId == R.id.article_img_copy) {
            if (getLastContentImageHitTestUrl() != null) {
                copyToClipboard(getLastContentImageHitTestUrl());
            }
            return true;
        } else if (itemId == R.id.article_img_share) {
            if (getLastContentImageHitTestUrl() != null) {
                shareImageFromUri(getLastContentImageHitTestUrl());
            }
            return true;
        } else if (itemId == R.id.article_img_share_url) {
            if (getLastContentImageHitTestUrl() != null) {
                shareText(getLastContentImageHitTestUrl());
            }
            return true;
        } else if (itemId == R.id.article_img_view_caption) {
            if (getLastContentImageHitTestUrl() != null) {
                Article selectedArticle = Application.getArticlesModel().getActiveArticle();

                if (selectedArticle != null)
                    displayImageCaption(getLastContentImageHitTestUrl(), selectedArticle.content);
            }
            return true;
        } else if (itemId == R.id.article_link_share) {
            Article selectedArticle = Application.getArticlesModel().getActiveArticle();

            if (selectedArticle != null)
                shareArticle(selectedArticle);
            return true;
        } else if (itemId == R.id.article_link_copy) {
            Article selectedArticle = Application.getArticlesModel().getActiveArticle();

            if (selectedArticle != null)
                copyToClipboard(selectedArticle.link);
            return true;
        }
        Log.d(TAG, "onContextItemSelected, unhandled id=" + item.getItemId());
        return super.onContextItemSelected(item);
    }

    public void displayAttachments(Article article) {
        if (article != null && article.attachments != null && !article.attachments.isEmpty()) {
            CharSequence[] items = new CharSequence[article.attachments.size()];
            final CharSequence[] itemUrls = new CharSequence[article.attachments.size()];

            for (int i = 0; i < article.attachments.size(); i++) {
                items[i] = article.attachments.get(i).title != null && !article.attachments.get(i).title.isEmpty() ?
                        article.attachments.get(i).title : article.attachments.get(i).content_url;

                itemUrls[i] = article.attachments.get(i).content_url;
            }

            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.attachments_prompt)
                    .setCancelable(true)
                    .setSingleChoiceItems(items, 0, (dialog, which) -> {
                        //
                    }).setNeutralButton(R.string.attachment_copy, (dialog, which) -> {
                        int selectedPosition = ((AlertDialog) dialog).getListView().getCheckedItemPosition();

                        copyToClipboard((String) itemUrls[selectedPosition]);
                    }).setPositiveButton(R.string.attachment_view, (dialog, id) -> {
                        int selectedPosition = ((AlertDialog) dialog).getListView().getCheckedItemPosition();

                        openUri(Uri.parse((String) itemUrls[selectedPosition]));

                        dialog.cancel();
                    }).setNegativeButton(R.string.dialog_cancel, (dialog, id) -> dialog.cancel());

            Dialog dialog = builder.create();
            dialog.show();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final HeadlinesFragment hf = (HeadlinesFragment) getSupportFragmentManager().findFragmentByTag(FRAG_HEADLINES);

        Article activeArticle = Application.getArticlesModel().getActiveArticle();

        Log.d(TAG, "onOptionsItemSelected, active article=" + activeArticle);

        int itemId = item.getItemId();
        if (itemId == R.id.subscribe_to_feed) {
            Intent subscribe = new Intent(OnlineActivity.this, SubscribeActivity.class);
            startActivityForResult(subscribe, 0);
            return true;
        } else if (itemId == R.id.toggle_attachments) {
            if (activeArticle != null)
                displayAttachments(activeArticle);
            return true;
        } else if (itemId == R.id.login) {
            login();
            return true;
        } else if (itemId == R.id.article_edit_note) {
            if (activeArticle != null)
                editArticleNote(activeArticle);
            return true;
        } else if (itemId == R.id.preferences) {
            Intent intent = new Intent(OnlineActivity.this,
                    PreferencesActivity.class);
            startActivityForResult(intent, 0);
            return true;
        } else if (itemId == R.id.search) {
            final EditText edit = new EditText(this);
            edit.setText(Application.getArticlesModel().getSearchQuery());

            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.search)
                    .setPositiveButton(getString(R.string.search),
                            (dialog4, which) -> {

                                String query = edit.getText().toString().trim();

                                Application.getArticlesModel().setSearchQuery(query);

                            })
                    .setNegativeButton(getString(R.string.cancel),
                            (dialog3, which) -> {

                                //

                            }).setView(edit);

            Dialog dialog = builder.create();
            dialog.show();
            return true;
        } else if (itemId == R.id.headlines_mark_as_read) {
            if (hf != null) {
                Feed feed = hf.getFeed();

                if (feed != null) {
                    catchupDialog(hf.getFeed());
                }
            }
            return true;
        } else if (itemId == R.id.headlines_display_mode) {
            if (hf != null) {
                String headlineMode = m_prefs.getString("headline_mode", "HL_DEFAULT");
                String[] headlineModeNames = getResources().getStringArray(R.array.headline_mode_names);
                final String[] headlineModeValues = getResources().getStringArray(R.array.headline_mode_values);

                int selectedIndex = Arrays.asList(headlineModeValues).indexOf(headlineMode);

                MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.headlines_set_display_mode)
                        .setSingleChoiceItems(headlineModeNames,
                                selectedIndex, (dialog2, which) -> {
                                    dialog2.cancel();

                                    SharedPreferences.Editor editor = m_prefs.edit();
                                    editor.putString("headline_mode", headlineModeValues[which]);
                                    editor.apply();

                                    FragmentTransaction ft = getSupportFragmentManager().beginTransaction();

                                    HeadlinesFragment hfnew = new HeadlinesFragment();

                                    hfnew.initialize(hf.getFeed());

                                    ft.replace(R.id.headlines_fragment, hfnew, FRAG_HEADLINES);

                                    ft.commit();
                                });

                Dialog dialog = builder.create();
                dialog.show();

            }
            return true;
        } else if (itemId == R.id.headlines_view_mode) {
            if (hf != null) {
                String viewMode = getViewMode();

                int selectedIndex = 0;

                switch (viewMode) {
                    case "all_articles":
                        selectedIndex = 1;
                        break;
                    case "marked":
                        selectedIndex = 2;
                        break;
                    case "published":
                        selectedIndex = 3;
                        break;
                    case "unread":
                        selectedIndex = 4;
                        break;
                }

                MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.headlines_set_view_mode)
                        .setSingleChoiceItems(
                                new String[]{
                                        getString(R.string.headlines_adaptive),
                                        getString(R.string.headlines_all_articles),
                                        getString(R.string.headlines_starred),
                                        getString(R.string.headlines_published),
                                        getString(R.string.headlines_unread)},
                                selectedIndex, (dialog1, which) -> {
                                    switch (which) {
                                        case 0:
                                            setViewMode("adaptive");
                                            break;
                                        case 1:
                                            setViewMode("all_articles");
                                            break;
                                        case 2:
                                            setViewMode("marked");
                                            break;
                                        case 3:
                                            setViewMode("published");
                                            break;
                                        case 4:
                                            setViewMode("unread");
                                            break;
                                    }
                                    dialog1.cancel();

                                    refresh();
                                });

                Dialog dialog = builder.create();
                dialog.show();

            }
            return true;
        } else if (itemId == R.id.headlines_select) {

            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.headlines_select_dialog)
                    .setSingleChoiceItems(
                            new String[]{
                                    getString(R.string.headlines_select_all),
                                    getString(R.string.headlines_select_unread),
                                    getString(R.string.headlines_select_none)},
                            0, (dialog, which) -> {

                                ArticleModel model = Application.getArticlesModel();

                                switch (which) {
                                    case 0:
                                        model.setSelection(ArticleModel.ArticlesSelection.ALL);
                                        break;
                                    case 1:
                                        model.setSelection(ArticleModel.ArticlesSelection.UNREAD);
                                        break;
                                    case 2:
                                        model.setSelection(ArticleModel.ArticlesSelection.NONE);
                                        break;
                                }
                                dialog.cancel();
                            });

            Dialog dialog = builder.create();
            dialog.show();
            return true;
        } else if (itemId == R.id.share_article) {
            if (activeArticle != null)
                shareArticle(activeArticle);

            return true;
        } else if (itemId == R.id.article_set_score) {
            if (activeArticle != null) {
                setArticleScore(activeArticle);
            }
            return true;
        } else if (itemId == R.id.toggle_marked) {
            if (activeArticle != null) {
                Article articleClone = new Article(activeArticle);
                articleClone.marked = !articleClone.marked;

                saveArticleMarked(articleClone);
            }
            return true;
        } else if (itemId == R.id.toggle_unread) {
            if (activeArticle != null) {
                Article articleClone = new Article(activeArticle);
                articleClone.unread = !articleClone.unread;

                saveArticleUnread(articleClone);
            }
            return true;
        } else if (itemId == R.id.selection_toggle_unread) {
            List<Article> selected = Application.getArticlesModel().getSelected();

            if (!selected.isEmpty()) {
                for (Article a : selected) {
                    Article articleClone = new Article(a);
                    articleClone.unread = !articleClone.unread;
                }

                toggleArticlesUnread(selected);
            }
            return true;
        } else if (itemId == R.id.selection_toggle_marked) {
            List<Article> selected = Application.getArticlesModel().getSelected();

            if (!selected.isEmpty()) {
                for (Article a : selected) {
                    Article articleClone = new Article(a);
                    articleClone.marked = !articleClone.marked;
                }

                toggleArticlesMarked(selected);
            }
            return true;
        } else if (itemId == R.id.selection_toggle_published) {
            List<Article> selected = Application.getArticlesModel().getSelected();

            if (!selected.isEmpty()) {
                for (Article a : selected) {
                    Article articleClone = new Article(a);
                    articleClone.published = !articleClone.published;
                }

                toggleArticlesPublished(selected);
            }
            return true;
        } else if (itemId == R.id.toggle_published) {
            if (activeArticle != null) {
                Article articleClone = new Article(activeArticle);
                articleClone.published = !articleClone.published;

                saveArticlePublished(articleClone);
            }
            return true;
        } else if (itemId == R.id.catchup_above) {
            if (activeArticle != null) {
                confirmCatchupAbove(activeArticle);
            }
            return true;
        } else if (itemId == R.id.article_set_labels) {
            if (getApiLevel() != 7) {
                if (activeArticle != null)
                    editArticleLabels(activeArticle);
            } else {
                toast(R.string.server_function_not_available);
            }

            return true;
        }
        Log.d(TAG, "onOptionsItemSelected, unhandled id=" + item.getItemId());
        return super.onOptionsItemSelected(item);
    }

    protected void catchupAbove(final Article startingArticle) {
        if (startingArticle != null) {
            List<Article> tmp = new ArrayList<>();

            for (Article a : Application.getArticles()) {
                if (a.id == startingArticle.id)
                    break;

                Article articleClone = new Article(a);

                if (articleClone.unread) {
                    articleClone.unread = false;

                    tmp.add(articleClone);
                }
            }

            if (!tmp.isEmpty()) {
                setArticlesUnread(tmp, Article.UPDATE_SET_FALSE);
            }
        }
    }

    public void editArticleNote(final Article article) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(article.title);

        final EditText topicEdit = new EditText(this);
        topicEdit.setText(article.note);
        builder.setView(topicEdit);

        builder.setPositiveButton(R.string.article_edit_note, (dialog, which) -> {
            String note = topicEdit.getText().toString().trim();

            saveArticleNote(article, note);
        });

        builder.setNegativeButton(R.string.dialog_cancel, (dialog, which) -> {
            //
        });

        Dialog dialog = builder.create();
        dialog.show();
    }

    public void editArticleLabels(Article article) {
        final int articleId = article.id;

        ApiRequest req = new ApiRequest(getApplicationContext()) {
            @Override
            protected void onPostExecute(JsonElement result) {
                if (result != null) {
                    Type listType = new TypeToken<List<Label>>() {
                    }.getType();
                    final List<Label> labels = new Gson().fromJson(result, listType);

                    CharSequence[] items = new CharSequence[labels.size()];
                    final int[] itemIds = new int[labels.size()];
                    boolean[] checkedItems = new boolean[labels.size()];

                    for (int i = 0; i < labels.size(); i++) {
                        items[i] = labels.get(i).caption;
                        itemIds[i] = labels.get(i).id;
                        checkedItems[i] = labels.get(i).checked;
                    }

                    MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(OnlineActivity.this)
                            .setTitle(R.string.article_set_labels)
                            .setMultiChoiceItems(items, checkedItems, (dialog, which, isChecked) -> {
                                final int labelId = itemIds[which];

                                HashMap<String, String> map = new HashMap<>();
                                map.put("sid", getSessionId());
                                map.put("op", "setArticleLabel");
                                map.put("label_id", String.valueOf(labelId));
                                map.put("article_ids", String.valueOf(articleId));
                                if (isChecked) map.put("assign", "true");

                                ApiRequest req1 = new ApiRequest(OnlineActivity.this);
                                req1.execute(map);

                            }).setPositiveButton(R.string.dialog_close, (dialog, which) -> dialog.cancel());

                    Dialog dialog = builder.create();
                    dialog.show();

                }
            }
        };

        HashMap<String, String> map = new HashMap<>();
        map.put("sid", getSessionId());
        map.put("op", "getLabels");
        map.put("article_id", String.valueOf(articleId));

        req.execute(map);
    }

    private void setLoadingStatus(int status) {
        setLoadingStatus(getString(status));
    }

    private void setLoadingStatus(String status) {
        TextView tv = findViewById(R.id.loading_message);

        if (tv != null) {
            tv.setText(status);
        }
    }

    protected void logout() {
        setSessionId(null);

        setLoadingStatus(R.string.login_ready);

        initMenu();
    }

    protected void loginFailure() {
        setSessionId(null);
        initMenu();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (getSessionId() == null) {
            login();
        } else {
            loginSuccess(false);
        }

        if (m_activeFeed != null)
            setTitle(m_activeFeed.title);

    }

    public Menu getMenu() {
        return m_menu;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.activity_main, menu);

        m_menu = menu;

        initMenu();

        return true;
    }

    public int getApiLevel() {
        return Application.getInstance().getApiLevel();
    }

    protected void setApiLevel(int apiLevel) {
        Application.getInstance().setApiLevel(apiLevel);
    }

    public void shareArticle(Article article) {
        if (article != null) {
            shareText(article.link, article.title);
        }
    }

    public void setArticleScore(Article article) {
        final EditText edit = new EditText(this);
        edit.setText(String.valueOf(article.score));

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.score_for_this_article)
                .setPositiveButton(R.string.set_score,
                        (dialog, which) -> {

                            try {
                                Article articleClone = new Article(article);
                                articleClone.score = Integer.parseInt(edit.getText().toString());

                                saveArticleScore(articleClone);

                            } catch (NumberFormatException e) {
                                toast(R.string.score_invalid);
                                e.printStackTrace();
                            }
                        })
                .setNegativeButton(getString(R.string.cancel),
                        (dialog, which) -> {

                            //

                        }).setView(edit);

        Dialog dialog = builder.create();
        dialog.show();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        ArticlePager ap = (ArticlePager) getSupportFragmentManager().findFragmentByTag(FRAG_ARTICLE);
        HeadlinesFragment hf = (HeadlinesFragment) getSupportFragmentManager().findFragmentByTag(FRAG_HEADLINES);

        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (ap != null && ap.isAdded()) {
                    ap.switchToArticle(false);
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (ap != null && ap.isAdded()) {
                    ap.switchToArticle(true);
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_ESCAPE:
                moveTaskToBack(true);
                return true;
            case KeyEvent.KEYCODE_O:
                if (ap != null) {
                    Article selectedArticle = Application.getArticlesModel().getActiveArticle();

                    if (selectedArticle != null)
                        openUri(Uri.parse(selectedArticle.link));
                }
                return true;
            case KeyEvent.KEYCODE_R:
                refresh();
                return true;
            case KeyEvent.KEYCODE_U:
                if (ap != null) {
                    Article selectedArticle = Application.getArticlesModel().getActiveArticle();

                    if (selectedArticle != null) {
                        selectedArticle.unread = !selectedArticle.unread;
                        saveArticleUnread(selectedArticle);
                    }
                }
                return true;
        }

        if (m_prefs.getBoolean("use_volume_keys", false)) {

            if (ap != null && ap.isAdded()) {
                switch (keyCode) {
                    case KeyEvent.KEYCODE_VOLUME_UP:
                        ap.switchToArticle(false);
                        return true;
                    case KeyEvent.KEYCODE_VOLUME_DOWN:
                        ap.switchToArticle(true);
                        return true;
                }
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    // Handle onKeyUp too to suppress beep
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (m_prefs.getBoolean("use_volume_keys", false)) {

            switch (keyCode) {
                case KeyEvent.KEYCODE_VOLUME_UP:
                case KeyEvent.KEYCODE_VOLUME_DOWN:
                    return true;
            }
        }

        return super.onKeyUp(keyCode, event);
    }

    public void catchupFeed(final Feed feed, final String mode, final boolean refreshAfter, final String searchQuery) {
        Log.d(TAG, "catchupFeed=" + feed + "; mode=" + mode + "; search=" + searchQuery);

        ApiRequest req = new ApiRequest(getApplicationContext()) {
            protected void onPostExecute(JsonElement result) {
                if (refreshAfter)
                    refresh();
            }
        };

        HashMap<String, String> map = new HashMap<>();
        map.put("sid", getSessionId());
        map.put("op", "catchupFeed");
        map.put("feed_id", String.valueOf(feed.id));
        map.put("search_query", searchQuery);
        map.put("search_lang", ""); // for the time being always user per-user default
        map.put("mode", mode);
        if (feed.is_cat)
            map.put("is_cat", "1");

        req.execute(map);
    }

    public void saveArticleUnread(final Article article) {
        setArticlesField(Collections.singletonList(article), Article.UPDATE_FIELD_UNREAD,
                article.unread ? Article.UPDATE_SET_TRUE : Article.UPDATE_SET_FALSE);
    }

    public void saveArticleScore(final Article article) {
        setArticlesField(Collections.singletonList(article), Article.UPDATE_FIELD_SCORE, Article.UPDATE_SET_TRUE);
    }

    public void saveArticleMarked(final Article article) {
        setArticlesField(Collections.singletonList(article), Article.UPDATE_FIELD_MARKED,
                article.marked ? Article.UPDATE_SET_TRUE : Article.UPDATE_SET_FALSE);
    }

    public void saveArticlePublished(final Article article) {
        setArticlesField(Collections.singletonList(article), Article.UPDATE_FIELD_PUBLISHED,
                article.published ? Article.UPDATE_SET_TRUE : Article.UPDATE_SET_FALSE);
    }

    public void saveArticleNote(final Article article, final String note) {
        setArticlesField(Collections.singletonList(article), Article.UPDATE_FIELD_NOTE, Article.UPDATE_SET_TRUE);
    }

    public void toggleArticlesMarked(final List<Article> articles) {
        setArticlesMarked(articles, Article.UPDATE_TOGGLE);
    }

    public void setArticlesMarked(final List<Article> articles, int mode) {
        setArticlesField(articles, Article.UPDATE_FIELD_MARKED, mode);
    }

    public void toggleArticlesUnread(final List<Article> articles) {
        setArticlesUnread(articles, Article.UPDATE_FIELD_UNREAD);
    }

    public void setArticlesUnread(final List<Article> articles, int mode) {
        setArticlesField(articles, Article.UPDATE_FIELD_UNREAD, mode);
    }

    public void toggleArticlesPublished(final List<Article> articles) {
        setArticlesPublished(articles, Article.UPDATE_TOGGLE);
    }

    public void setArticlesPublished(final List<Article> articles, int mode) {
        setArticlesField(articles, Article.UPDATE_FIELD_PUBLISHED, mode);
    }

    public void setArticlesField(final List<Article> articles, int field, int mode) {
        ApiRequest req = new ApiRequest(getApplicationContext()) {
            protected void onPostExecute(JsonElement result) {
                if (m_lastError == ApiCommon.ApiError.SUCCESS) {

                    if (BuildConfig.DEBUG)
                        Log.d(TAG, "setArticleField operation complete, field=" + field + " mode=" + mode);

                    ArticleModel model = Application.getArticlesModel();

                    for (Article a : articles) {
                        Article articleClone = new Article(a);

                        switch (mode) {
                            case Article.UPDATE_SET_FALSE:
                                switch (field) {
                                    case Article.UPDATE_FIELD_MARKED:
                                        articleClone.marked = false;
                                        break;
                                    case Article.UPDATE_FIELD_PUBLISHED:
                                        articleClone.published = false;
                                        break;
                                    case Article.UPDATE_FIELD_UNREAD:
                                        articleClone.unread = false;
                                        break;
                                }
                                break;
                            case Article.UPDATE_SET_TRUE:
                                switch (field) {
                                    case Article.UPDATE_FIELD_MARKED:
                                        articleClone.marked = true;
                                        break;
                                    case Article.UPDATE_FIELD_PUBLISHED:
                                        articleClone.published = true;
                                        break;
                                    case Article.UPDATE_FIELD_UNREAD:
                                        articleClone.unread = true;
                                        break;
                                }
                                break;
                            case Article.UPDATE_TOGGLE:
                                switch (field) {
                                    case Article.UPDATE_FIELD_MARKED:
                                        articleClone.marked = !articleClone.marked;
                                        break;
                                    case Article.UPDATE_FIELD_PUBLISHED:
                                        articleClone.published = !articleClone.published;
                                        break;
                                    case Article.UPDATE_FIELD_UNREAD:
                                        articleClone.unread = !articleClone.unread;
                                        break;
                                }
                                break;
                        }


                        if (BuildConfig.DEBUG)
                            Log.d(TAG, "updating article: " + articleClone);

                        model.update(articleClone);
                    }

                    invalidateOptionsMenu();

                    return;
                }

                if (m_lastError != null && m_lastError == ApiCommon.ApiError.LOGIN_FAILED) {
                    login(true);
                } else {
                    if (m_lastErrorMessage != null) {
                        toast(getString(getErrorMessage()) + "\n" + m_lastErrorMessage);
                    } else {
                        toast(getErrorMessage());
                    }
                }
            }
        };

        HashMap<String, String> map = new HashMap<>();
        map.put("sid", getSessionId());
        map.put("op", "updateArticle");
        map.put("article_ids", articles.stream()
                .map(a -> String.valueOf(a.id))
                .collect(Collectors.joining(",")));
        map.put("mode", String.valueOf(mode));

        if (field == Article.UPDATE_FIELD_NOTE)
            map.put("data", articles.get(0).note);

        if (field == Article.UPDATE_FIELD_SCORE)
            map.put("data", String.valueOf(articles.get(0).score));

        map.put("field", String.valueOf(field));

        req.execute(map);

    }

    // this may be called after activity has been destroyed (i.e. long asynctask)
    protected void initMenu() {
        if (m_menu != null) {
            if (getSessionId() != null) {
                m_menu.setGroupVisible(R.id.menu_group_feeds, true);
                m_menu.setGroupVisible(R.id.menu_group_headlines, true);
                m_menu.setGroupVisible(R.id.menu_group_article, true);
                m_menu.setGroupVisible(R.id.menu_group_logged_out, false);
            } else {
                m_menu.setGroupVisible(R.id.menu_group_feeds, false);
                m_menu.setGroupVisible(R.id.menu_group_headlines, false);
                m_menu.setGroupVisible(R.id.menu_group_article, false);
                m_menu.setGroupVisible(R.id.menu_group_logged_out, true);
            }

            m_menu.setGroupVisible(R.id.menu_group_headlines, false);
            m_menu.setGroupVisible(R.id.menu_group_article, false);
            m_menu.setGroupVisible(R.id.menu_group_feeds, false);

            m_menu.findItem(R.id.subscribe_to_feed).setEnabled(getApiLevel() >= 5);

            MenuItem search = m_menu.findItem(R.id.search);
            search.setEnabled(getApiLevel() >= 2);

            Article activeArticle = Application.getArticlesModel().getActiveArticle();

            Log.d(TAG, "active article=" + activeArticle);

            if (activeArticle != null) {

                m_menu.findItem(R.id.toggle_marked).setIcon(activeArticle.marked ? R.drawable.baseline_star_24 :
                        R.drawable.baseline_star_outline_24);

                // TODO we probably shouldn't do this all the time
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && m_prefs.getBoolean("enable_icon_tinting", true)) {
                    TypedValue tvTertiary = new TypedValue();
                    getTheme().resolveAttribute(R.attr.colorTertiary, tvTertiary, true);

                    ColorStateList colorStateTertiary = ColorStateList.valueOf(ContextCompat.getColor(this, tvTertiary.resourceId));

                    TypedValue tvNormal = new TypedValue();
                    getTheme().resolveAttribute(R.attr.colorControlNormal, tvNormal, true);

                    ColorStateList colorStateNormal = ColorStateList.valueOf(ContextCompat.getColor(this, tvNormal.resourceId));

                    m_menu.findItem(R.id.toggle_published).setIconTintList(activeArticle.published ? colorStateTertiary : colorStateNormal);
                    m_menu.findItem(R.id.toggle_marked).setIconTintList(activeArticle.marked ? colorStateTertiary : colorStateNormal);

                } else {
                    m_menu.findItem(R.id.toggle_published).setIcon(activeArticle.published ? R.drawable.rss_box :
                            R.drawable.baseline_rss_feed_24);
                }
            }
        }
    }

    protected void refresh(boolean includeHeadlines) {
        FeedsFragment ff = (FeedsFragment) getSupportFragmentManager().findFragmentByTag(FRAG_FEEDS);

        if (ff != null) {
            ff.refresh();
        }

        if (includeHeadlines) {
            HeadlinesFragment hf = (HeadlinesFragment) getSupportFragmentManager().findFragmentByTag(FRAG_HEADLINES);

            if (hf != null) {
                hf.refresh(false);
            }
        }
    }

    protected void refresh() {
        refresh(true);
    }

    protected class LoginRequest extends ApiRequest {
        boolean m_refreshAfterLogin;
        OnLoginFinishedListener m_listener;

        public LoginRequest(Context context, boolean refresh, OnLoginFinishedListener listener) {
            super(context);
            m_refreshAfterLogin = refresh;
            m_listener = listener;
        }

        @SuppressLint("StaticFieldLeak")
        protected void onPostExecute(JsonElement result) {
            if (result != null) {
                try {
                    JsonObject content = result.getAsJsonObject();

                    if (content != null) {
                        setSessionId(content.get("session_id").getAsString());

                        JsonElement apiLevel = content.get("api_level");

                        Log.d(TAG, "Authenticated!");

                        if (apiLevel != null) {
                            setApiLevel(apiLevel.getAsInt());
                            Log.d(TAG, "Received API level: " + getApiLevel());

                            // get custom sort from configuration object
                            if (getApiLevel() >= 17) {

                                // daemon_is_running, icons_dir, etc...
                                JsonObject config = content.get("config").getAsJsonObject();

                                Type hashType = new TypeToken<Map<String, String>>() {
                                }.getType();
                                Map<String, String> customSortTypes = new Gson().fromJson(config.get("custom_sort_types"), hashType);

                                setCustomSortModes(customSortTypes);
                            }

                            if (m_listener != null) {
                                m_listener.OnLoginSuccess();
                            } else {
                                loginSuccess(m_refreshAfterLogin);
                            }

                        } else {

                            ApiRequest req = new ApiRequest(OnlineActivity.this) {
                                protected void onPostExecute(JsonElement result) {
                                    setApiLevel(0);

                                    if (result != null) {
                                        try {
                                            setApiLevel(result.getAsJsonObject().get("level").getAsInt());
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    } else if (m_lastError != ApiCommon.ApiError.API_UNKNOWN_METHOD) {
                                        // Unknown method means old tt-rss, in that case we assume API 0 and continue

                                        setLoadingStatus(getErrorMessage());

                                        if (m_lastErrorMessage != null) {
                                            setLoadingStatus(getString(getErrorMessage()) + "\n\n" + m_lastErrorMessage);
                                        } else {
                                            setLoadingStatus(getErrorMessage());
                                        }

                                        if (m_listener != null) {
                                            m_listener.OnLoginFailed();
                                        } else {
                                            loginFailure();
                                        }

                                        return;
                                    }

                                    Log.d(TAG, "Received API level: " + getApiLevel());

                                    loginSuccess(m_refreshAfterLogin);
                                }
                            };

                            HashMap<String, String> map = new HashMap<>();
                            map.put("sid", getSessionId());
                            map.put("op", "getApiLevel");

                            req.execute(map);

                            setLoadingStatus(R.string.loading_message);
                        }

                        return;
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            setSessionId(null);

            if (m_lastErrorMessage != null) {
                setLoadingStatus(getString(getErrorMessage()) + "\n\n" + m_lastErrorMessage);
            } else {
                setLoadingStatus(getString(getErrorMessage()) + "\n\n" + m_apiStatusCode);
            }

            loginFailure();
        }

    }

    public LinkedHashMap<String, String> getSortModes() {
        LinkedHashMap<String, String> tmp = new LinkedHashMap<>();

        tmp.put("default", getString(R.string.headlines_sort_default));
        tmp.put("feed_dates", getString(R.string.headlines_sort_newest_first));
        tmp.put("date_reverse", getString(R.string.headlines_sort_oldest_first));
        tmp.put("title", getString(R.string.headlines_sort_title));

        tmp.putAll(Application.getInstance().m_customSortModes);

        return tmp;
    }

    public String getSortMode() {
        return m_prefs.getString("headlines_sort_mode", "default");
    }

    public void setSortMode(String sortMode) {
        SharedPreferences.Editor editor = m_prefs.edit();
        editor.putString("headlines_sort_mode", sortMode);
        editor.apply();
    }

    private synchronized void setCustomSortModes(Map<String, String> modes) {
        Application.getInstance().m_customSortModes.clear();
        Application.getInstance().m_customSortModes.putAll(modes);
    }

    public void setViewMode(String viewMode) {
        SharedPreferences.Editor editor = m_prefs.edit();
        editor.putString("view_mode", viewMode);
        editor.apply();
    }

    public String getViewMode() {
        return m_prefs.getString("view_mode", "adaptive");
    }

    public void setLastContentImageHitTestUrl(String url) {
        m_lastImageHitTestUrl = url;
    }

    public String getLastContentImageHitTestUrl() {
        return m_lastImageHitTestUrl;
    }

    public int getResizeWidth() {
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);

        return size.x > size.y ? (int) (size.y * 0.75) : (int) (size.x * 0.75);
    }


    public void setLoadingProgress(int progress) {
        if (m_loadingProgress != null)
            m_loadingProgress.setProgress(progress);
    }

    public void setLoadingVisible(boolean visible) {
        if (m_loadingProgress != null)
            m_loadingProgress.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public void enableActionModeObserver() {
        ArticleModel model = Application.getArticlesModel();

        model.getArticles().observe(this, (articles -> {
            int selectedCount = model.getSelected().size();

            Log.d(TAG, "observed selected articles=" + selectedCount);

            if (selectedCount > 0) {
                if (m_headlinesActionMode == null)
                    m_headlinesActionMode = startSupportActionMode(m_headlinesActionModeCallback);

                m_headlinesActionMode.setTitle(String.valueOf(selectedCount));
            } else if (m_headlinesActionMode != null) {
                m_headlinesActionMode.finish();
            }
        }));
    }

    @Override
    public void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);

        out.putParcelable("m_activeFeed", m_activeFeed);

        Application.getInstance().save(out);
    }


    public Feed getActiveFeed() {
        return m_activeFeed;
    }

    public void setActiveFeed(Feed feed) {
        m_activeFeed = feed;

        setTitle(feed.title);

        FeedsFragment ff = (FeedsFragment) getSupportFragmentManager().findFragmentByTag(FRAG_FEEDS);

        if (ff != null) {
            ff.setSelectedFeed(feed);
        }
    }
}
