package org.fox.ttrss;

import android.app.Application;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.preference.PreferenceManager;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import org.fox.ttrss.types.Article;
import org.fox.ttrss.types.Feed;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class ArticleModel extends AndroidViewModel implements ApiCommon.ApiCaller {
    private static final String TAG = ArticleModel.class.getSimpleName();
    @NonNull
    private final MutableLiveData<List<Article>> m_articles = new MutableLiveData<>(new ArrayList<Article>());
    private SharedPreferences m_prefs;
    private final int m_responseCode = 0;
    protected String m_responseMessage;
    private int m_apiStatusCode = 0;

    private String m_lastErrorMessage;
    private ApiCommon.ApiError m_lastError;
    private Feed m_feed;
    private int m_firstId;
    private String m_searchQuery = "";
    private boolean m_firstIdChanged;
    private int m_offset;
    private int m_amountLoaded;
    private int m_resizeWidth;
    private boolean m_append;
    private boolean m_lazyLoadEnabled = true;
    private MutableLiveData<Boolean> m_isLoading = new MutableLiveData<>(Boolean.valueOf(false));
    private ExecutorService m_executor;
    private Handler m_mainHandler = new Handler(Looper.getMainLooper());
    private MutableLiveData<Long> m_lastUpdate = new MutableLiveData<>(Long.valueOf(0));
    private MutableLiveData<Integer> m_loadingProgress = new MutableLiveData<>(Integer.valueOf(0));
    private MutableLiveData<Article> m_activeArticle = new MutableLiveData<>(null);

    public ArticleModel(@NonNull Application application) {
        super(application);

        m_prefs = PreferenceManager.getDefaultSharedPreferences(application);

        // do we need concurrency or not?
        m_executor = Executors.newSingleThreadExecutor();
    }

    public LiveData<Long> getUpdatesData() {
        return m_lastUpdate;
    }

    public LiveData<List<Article>> getArticles() {
        return m_articles;
    }

    public void update(@NonNull Article article) {
        int position = m_articles.getValue().indexOf(article);

        if (position != -1)
            update(position, article);
    }

    public void update(int position, @NonNull Article article) {
        m_articles.getValue().set(position, article);
        m_articles.postValue(m_articles.getValue());
    }

    public void update(@NonNull List<Article> articles) {
        m_articles.postValue(articles);
    }

    public LiveData<Article> getActive() {
        return m_activeArticle;
    }

    /**
     * returns null if there's none or it is invalid (missing in list)
     */
    public Article getActiveArticle() {
        List<Article> articles = m_articles.getValue();

        try {
            // always get uptodate item from model list if possible
            return articles.get(articles.indexOf(m_activeArticle.getValue()));
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    // we store .active flag in articleview for UI update and a separate observable for easy access
    public void setActive(Article article) {
        Article currentlyActive = getActiveArticle();

        Log.d(TAG, "setActive set=" + article + " previous=" + currentlyActive);

        if (currentlyActive != null && (article == null || currentlyActive.id != article.id)) {
            Article currentlyActiveClone = new Article(currentlyActive);
            currentlyActiveClone.active = false;

            update(currentlyActiveClone);
        }

        if (article != null) {
            Article articleClone = new Article(article);

            articleClone.active = true;
            update(articleClone);

            m_activeArticle.postValue(articleClone);
        } else {
            m_activeArticle.postValue(null);
        }
    }


    public void startLoading(boolean append, @NonNull Feed feed, int resizeWidth) {
        Log.d(TAG, "startLoading append=" + append + " feed id=" + feed.id + " cat=" + feed.is_cat + " lazyLoadEnabled=" + m_lazyLoadEnabled + " isLoading=" + m_isLoading.getValue());

        m_resizeWidth = resizeWidth;

        if (!append) {

            // reset search for a different feed
            if (m_feed != null && !m_feed.equals(feed))
                m_searchQuery = "";

            m_append = false;
            m_lazyLoadEnabled = true;
            m_feed = feed;

            loadInBackground();
        } else if (!m_feed.equals(feed)) {
            // Feed changed; ignore append request for old feed
            Log.d(TAG, "Ignoring append request for feed " + feed + " (current feed is " + m_feed + ")");
        } else if (!m_isLoading.getValue() && m_lazyLoadEnabled) {
            m_append = true;

            loadInBackground();
        } else {
            m_articles.postValue(m_articles.getValue());
        }
    }

    public enum ArticlesSelection {ALL, NONE, UNREAD}

    public void setSelection(@NonNull ArticlesSelection select) {
        List<Article> articles = m_articles.getValue();

        for (int i = 0; i < articles.size(); i++) {
            Article articleClone = new Article(articles.get(i));

            if (select == ArticlesSelection.ALL || select == ArticlesSelection.UNREAD && articleClone.unread) {
                articleClone.selected = true;
            } else {
                articleClone.selected = false;
            }

            update(i, articleClone);
        }
    }

    private void loadInBackground() {
        Log.d(TAG, this + " loadInBackground append=" + m_append + " offset=" + m_offset + " lazyLoadEnabled=" + m_lazyLoadEnabled);

        final List<Article> articlesWork = new ArrayList<>(m_articles.getValue());

        m_isLoading.postValue(true);

        final int skip = getSkip(m_append, articlesWork);
        final boolean allowForceUpdate = org.fox.ttrss.Application.getInstance().getApiLevel() >= 9 &&
                !m_feed.is_cat && m_feed.id > 0 && !m_append && skip == 0;

        HashMap<String, String> params = new HashMap<>();

        params.put("op", "getHeadlines");
        params.put("sid", org.fox.ttrss.Application.getInstance().getSessionId());
        params.put("feed_id", String.valueOf(m_feed.id));
        params.put("show_excerpt", "true");
        params.put("excerpt_length", String.valueOf(CommonActivity.EXCERPT_MAX_LENGTH));
        params.put("show_content", "true");
        params.put("include_attachments", "true");
        params.put("view_mode", m_prefs.getString("view_mode", "adaptive"));
        params.put("limit", m_prefs.getString("headlines_request_size", "15"));
        params.put("skip", String.valueOf(skip));
        params.put("include_nested", "true");
        params.put("has_sandbox", "true");
        params.put("order_by", m_prefs.getString("headlines_sort_mode", "default"));

        if (m_prefs.getBoolean("enable_image_downsampling", false)) {
            if (m_prefs.getBoolean("always_downsample_images", false) || !org.fox.ttrss.Application.getInstance().isWifiConnected()) {
                params.put("resize_width", String.valueOf(m_resizeWidth));
            }
        }

        if (m_feed.is_cat)
            params.put("is_cat", "true");

        if (allowForceUpdate) {
            params.put("force_update", "true");
        }

        if (m_searchQuery != null && !m_searchQuery.isEmpty()) {
            params.put("search", m_searchQuery);
            params.put("search_mode", "");
            params.put("match_on", "both");
        }

        if (m_firstId > 0)
            params.put("check_first_id", String.valueOf(m_firstId));

        if (org.fox.ttrss.Application.getInstance().getApiLevel() >= 12) {
            params.put("include_header", "true");
        }

        Log.d(TAG, "firstId=" + m_firstId + " append=" + m_append + " skip=" + skip + " localSize=" + articlesWork.size());

        m_executor.execute(() -> {
            final JsonElement result = ApiCommon.performRequest(getApplication(), params, this);

            if (BuildConfig.DEBUG)
                Log.d(TAG, "got result=" + result);

            if (result != null) {
                try {
                    final JsonArray content = result.getAsJsonArray();

                    if (content != null) {
                        final List<Article> articlesJson;
                        final JsonObject header;

                        if (org.fox.ttrss.Application.getInstance().getApiLevel() >= 12) {
                            header = content.get(0).getAsJsonObject();

                            m_firstIdChanged = header.get("first_id_changed") != null;

                            try {
                                m_firstId = header.get("first_id").getAsInt();
                            } catch (NumberFormatException e) {
                                m_firstId = 0;
                            }

                            Log.d(TAG, this + " firstID=" + m_firstId + " firstIdChanged=" + m_firstIdChanged);

                            Type listType = new TypeToken<List<Article>>() {
                            }.getType();
                            articlesJson = new Gson().fromJson(content.get(1), listType);
                        } else {
                            Type listType = new TypeToken<List<Article>>() {
                            }.getType();
                            articlesJson = new Gson().fromJson(content, listType);
                        }

                        if (!m_append)
                            articlesWork.clear();

                        m_amountLoaded = articlesJson.size();

                        for (Article article : articlesJson)
                            if (!articlesWork.contains(article)) {
                                article.collectMediaInfo();
                                article.cleanupExcerpt();
                                article.fixNullFields();
                                articlesWork.add(article);
                            } else {
                                Log.d(TAG, "duplicate:" + article);
                            }

                        if (m_firstIdChanged) {
                            Log.d(TAG, "first id changed, disabling lazy load");
                            m_lazyLoadEnabled = false;
                        }

                        if (m_amountLoaded < Integer.parseInt(m_prefs.getString("headlines_request_size", "15"))) {
                            Log.d(TAG, this + " amount loaded " + m_amountLoaded + " < request size, disabling lazy load");
                            m_lazyLoadEnabled = false;
                        }

                        m_offset += m_amountLoaded;

                        Log.d(TAG, this + " loaded headlines=" + m_amountLoaded + " resultingLocalSize=" + articlesWork.size());
                    }
                } catch (Exception e) {
                    setLastError(ApiCommon.ApiError.OTHER_ERROR);
                    setLastErrorMessage(e.getMessage());

                    e.printStackTrace();
                }

            }

            m_mainHandler.post(() -> {
                m_articles.setValue(articlesWork);
                m_lastUpdate.setValue(System.currentTimeMillis());
                m_isLoading.setValue(false);
            });
        });
    }

    private int getSkip(boolean append, @NonNull List<Article> articles) {
        int skip = 0;

        if (append) {
            // adaptive, all_articles, marked, published, unread
            String viewMode = m_prefs.getString("view_mode", "adaptive");

            int numUnread = Math.toIntExact(getUnread(articles).size());
            int numAll = Math.toIntExact(articles.size());

            if ("marked".equals(viewMode)) {
                skip = numAll;
            } else if ("published".equals(viewMode)) {
                skip = numAll;
            } else if ("unread".equals(viewMode)) {
                skip = numUnread;
            } else if (m_searchQuery != null && !m_searchQuery.isEmpty()) {
                skip = numAll;
            } else if ("adaptive".equals(viewMode)) {
                skip = numUnread > 0 ? numUnread : numAll;
            } else {
                skip = numAll;
            }
        }

        return skip;
    }

    @Override
    public void setStatusCode(int statusCode) {
        m_apiStatusCode = statusCode;
    }

    @Override
    public void setLastError(ApiCommon.ApiError lastError) {
        m_lastError = lastError;
    }

    @Override
    public void setLastErrorMessage(String message) {
        m_lastErrorMessage = message;
    }

    @Override
    public void notifyProgress(int progress) {
        m_loadingProgress.postValue(progress);
    }

    public boolean getFirstIdChanged() {
        return m_firstIdChanged;
    }

    public boolean getAppend() {
        return m_append;
    }

    public int getOffset() {
        return m_offset;
    }

    public boolean isLazyLoadEnabled() {
        return m_lazyLoadEnabled;
    }

    public int getErrorMessage() {
        return ApiCommon.getErrorMessage(m_lastError);
    }

    ApiCommon.ApiError getLastError() {
        return m_lastError;
    }

    String getLastErrorMessage() {
        return m_lastErrorMessage;
    }

    public boolean isLoading() {
        return m_isLoading.getValue();
    }

    public LiveData<Boolean> getIsLoading() {
        return m_isLoading;
    }

    public LiveData<Integer> getLoadingProgress() {
        return m_loadingProgress;
    }


    public String getSearchQuery() {
        return m_searchQuery;
    }

    public void setSearchQuery(@NonNull String query) {
        if (!m_searchQuery.equals(query)) {
            m_searchQuery = query;

            startLoading(false, m_feed, m_resizeWidth);
        }
    }


    public List<Article> getUnread(List<Article> articles) {
        return articles.stream().filter(a -> {
            return a.unread;
        }).collect(Collectors.toList());
    }

    public List<Article> getSelected() {
        return m_articles.getValue().stream().filter(a -> {
            return a.selected;
        }).collect(Collectors.toList());
    }

    // returns null if not found
    public Article getById(final int id) {
        return m_articles.getValue().stream().filter(a -> a.id == id)
                .findFirst()
                .orElse(null);
    }

}
