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
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;

import org.fox.ttrss.types.Article;
import org.fox.ttrss.types.Feed;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class ArticleModel extends AndroidViewModel implements ApiCommon.ApiCaller {
    private static final String TAG = ArticleModel.class.getSimpleName();
    private static final Gson GSON = new Gson();
    private static final Type ARTICLE_LIST_TYPE = new TypeToken<List<Article>>() {}.getType();
    @NonNull
    private final MutableLiveData<List<Article>> m_articles = new MutableLiveData<>(new ArrayList<Article>());
    private SharedPreferences m_prefs;
    protected String m_responseMessage;
    private int m_apiStatusCode = 0;

    private String m_lastErrorMessage;
    private ApiCommon.ApiError m_lastError;
    private Feed m_feed;
    private int m_firstId;
    private String m_searchQuery = "";
    private boolean m_firstIdChanged;
    private int m_offset;
    private String m_paginationViewMode = "adaptive";
    private final AtomicInteger m_loadGeneration = new AtomicInteger();
    private int m_resizeWidth;
    private boolean m_append;
    private boolean m_lazyLoadEnabled = true;
    private MutableLiveData<Boolean> m_isLoading = new MutableLiveData<>(false);
    private ExecutorService m_executor;
    private Handler m_mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable m_notifyArticles = this::notifyArticles;
    private MutableLiveData<Long> m_lastUpdate = new MutableLiveData<>(0L);
    private MutableLiveData<Integer> m_loadingProgress = new MutableLiveData<>(0);
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
        notifyArticlesChanged();
    }

    public void update(@NonNull List<Article> articles) {
        m_articles.setValue(articles);
    }

    private void notifyArticles() {
        m_articles.setValue(m_articles.getValue());
    }

    private void notifyArticlesChanged() {
        // Coalesce UI updates without queueing an old list reference which could
        // overwrite a newly appended page when the notification is delivered.
        m_mainHandler.removeCallbacks(m_notifyArticles);
        m_mainHandler.post(m_notifyArticles);
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
            m_firstId = 0;
            m_firstIdChanged = false;
            m_offset = 0;
            m_paginationViewMode = m_prefs.getString("view_mode", "adaptive");
            m_lazyLoadEnabled = true;
            m_feed = feed;

            loadInBackground();
        } else if (m_feed == null || !m_feed.equals(feed)) {
            // Feed changed; ignore append request for old feed
            Log.d(TAG, "Ignoring append request for feed " + feed + " (current feed is " + m_feed + ")");
        } else if (!m_isLoading.getValue() && m_lazyLoadEnabled) {
            m_append = true;

            loadInBackground();
        } else {
            notifyArticlesChanged();
        }
    }

    public enum ArticlesSelection {ALL, NONE, UNREAD}

    public void setSelection(@NonNull ArticlesSelection select) {
        List<Article> articles = m_articles.getValue();

        for (int i = 0; i < articles.size(); i++) {
            Article articleClone = new Article(articles.get(i));

            if (select == ArticlesSelection.ALL || (select == ArticlesSelection.UNREAD && articleClone.unread)) {
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
        final boolean append = m_append;
        final int generation = m_loadGeneration.incrementAndGet();
        final Feed feed = new Feed(m_feed);
        final String viewMode = m_paginationViewMode;
        final boolean search = m_searchQuery != null && !m_searchQuery.isEmpty();

        m_isLoading.setValue(true);

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
        params.put("view_mode", viewMode);
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
            // Each request owns its error state; superseded requests cannot change
            // the new feed's loading result while their network call completes.
            LoadStatus status = new LoadStatus();
            HeadlinesPageLoader.Result page = null;
            List<Article> loaded = null;
            try {
                page = HeadlinesPageLoader.load(params,
                        append ? articlesWork.stream().map(a -> a.id).collect(Collectors.toSet())
                                : java.util.Collections.emptySet(),
                        requestParams -> {
                            if (generation != m_loadGeneration.get())
                                return null;
                            Log.d(TAG, "loading headlines feed=" + feed.id +
                                    " skip=" + requestParams.get("skip") +
                                    " checkFirstId=" + requestParams.get("check_first_id"));
                            return ApiCommon.performRequest(getApplication(), requestParams, status);
                        });
                if (page != null) {
                    loaded = GSON.fromJson(page.articles, ARTICLE_LIST_TYPE);
                    for (Article article : loaded) {
                        article.collectMediaInfo();
                        article.cleanupExcerpt();
                        article.fixNullFields();
                    }
                }
            } catch (Exception e) {
                status.setLastError(ApiCommon.ApiError.OTHER_ERROR);
                status.setLastErrorMessage(e.getMessage());
                Log.w(TAG, "Could not load headlines", e);
            }

            final HeadlinesPageLoader.Result completedPage = page;
            final List<Article> completedArticles = loaded;
            m_mainHandler.post(() -> {
                if (generation != m_loadGeneration.get())
                    return;

                m_lastError = status.error;
                m_lastErrorMessage = status.message;
                m_apiStatusCode = status.statusCode;
                if (completedPage != null && completedArticles != null
                        && status.error == ApiCommon.ApiError.SUCCESS) {
                    // Merge into the live list, not the pre-request snapshot: read,
                    // selection and active-article updates may have completed meanwhile.
                    List<Article> merged = append ? new ArrayList<>(m_articles.getValue())
                            : new ArrayList<>();
                    for (Article article : completedArticles) {
                        if (!merged.contains(article))
                            merged.add(article);
                    }
                    m_firstId = completedPage.firstId;
                    m_firstIdChanged = false; // Invalidation was recovered within the load.
                    m_lazyLoadEnabled = !completedPage.exhausted;
                    m_offset = merged.size();
                    if (!append) {
                        m_paginationViewMode = HeadlinesPageLoader.resolveViewMode(viewMode,
                                search, feed.id, !getUnread(completedArticles).isEmpty());
                    }
                    Log.d(TAG, "loaded headlines=" + completedArticles.size() +
                            " resultingLocalSize=" + merged.size() +
                            " lazyLoadEnabled=" + m_lazyLoadEnabled);
                    m_articles.setValue(merged);
                }
                m_lastUpdate.setValue(System.currentTimeMillis());
                m_isLoading.setValue(false);
            });
        });
    }

    private final class LoadStatus implements ApiCommon.ApiCaller {
        private ApiCommon.ApiError error = ApiCommon.ApiError.SUCCESS;
        private String message;
        private int statusCode;

        @Override
        public void setStatusCode(int code) { statusCode = code; }

        @Override
        public void setLastError(ApiCommon.ApiError value) { error = value; }

        @Override
        public void setLastErrorMessage(String value) { message = value; }

        @Override
        public void notifyProgress(int progress) { m_loadingProgress.postValue(progress); }
    }

    private int getSkip(boolean append, @NonNull List<Article> articles) {
        if (!append)
            return 0;

        return HeadlinesPageLoader.getSkip(m_paginationViewMode,
                !m_feed.is_cat && m_feed.id == Feed.FRESH,
                !m_feed.is_cat && m_feed.id == Feed.RECENTLY_READ,
                getUnread(articles).size(), articles.size());
    }

    @Override
    public void setStatusCode(int statusCode) {
        m_apiStatusCode = statusCode;
    }

    public int getStatusCode() {
        return m_apiStatusCode;
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
