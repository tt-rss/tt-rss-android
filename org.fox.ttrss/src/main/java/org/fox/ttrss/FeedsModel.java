package org.fox.ttrss;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.preference.PreferenceManager;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;

import org.fox.ttrss.types.Feed;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class FeedsModel extends AndroidViewModel implements ApiCommon.ApiCaller {
    private static final String TAG = FeedsModel.class.getSimpleName();
    protected MutableLiveData<List<Feed>> m_feeds = new MutableLiveData<>(new ArrayList<>());
    protected MutableLiveData<Integer> m_loadingProgress = new MutableLiveData<>(Integer.valueOf(0));
    protected MutableLiveData<Long> m_lastUpdate = new MutableLiveData<>(Long.valueOf(0));
    protected MutableLiveData<Boolean> m_isLoading = new MutableLiveData<>(Boolean.valueOf(false));

    protected Feed m_feed;

    protected ExecutorService m_executor = Executors.newSingleThreadExecutor();
    protected Handler m_mainHandler = new Handler(Looper.getMainLooper());

    private final int m_responseCode = 0;
    protected String m_responseMessage;
    private int m_apiStatusCode = 0;
    private String m_lastErrorMessage;
    private ApiCommon.ApiError m_lastError;

    protected SharedPreferences m_prefs;

    public FeedsModel(@NonNull Application application) {
        super(application);

        m_prefs = PreferenceManager.getDefaultSharedPreferences(application);

        Log.d(TAG, this + " created");
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

    public void startLoading(Feed feed) {
        Log.d(TAG, "startLoading feed id=" + feed.id + " cat=" + feed.is_cat);

        m_feed = feed;

        loadInBackground();
    }

    @Override
    public void notifyProgress(int progress) {
        m_loadingProgress.postValue(progress);
    }

    protected void loadInBackground() {
        Log.d(TAG, this + " loadInBackground");

        m_isLoading.postValue(true);

        m_executor.execute(() -> {
            final HashMap<String, String> params = new HashMap<>();

            params.put("op", "getFeeds");
            params.put("cat_id", String.valueOf(m_feed.id));
            params.put("include_nested", "true");
            params.put("sid", ((org.fox.ttrss.Application) getApplication()).getSessionId());

            final JsonElement result = ApiCommon.performRequest(getApplication(), params, this);

            if (BuildConfig.DEBUG)
                Log.d(TAG, "got result=" + result);

            boolean unreadOnly = m_prefs.getBoolean("show_unread_only", true);

            try {
                JsonArray content = result.getAsJsonArray();
                if (content != null) {

                    Type listType = new TypeToken<List<Feed>>() {
                    }.getType();

                    List<Feed> feedsJson = new Gson().fromJson(content, listType);

                    // seems to be necessary evil because of deserialization
                    feedsJson = feedsJson.stream().peek(Feed::fixNullFields).collect(Collectors.toList());

                    if (unreadOnly && m_feed.id != Feed.CAT_SPECIAL)
                        feedsJson = feedsJson.stream()
                                .filter(f -> f.unread > 0)
                                .collect(Collectors.toList());

                    sortFeeds(feedsJson, m_feed, null);

                    m_feeds.postValue(feedsJson);
                }
            } catch (Exception e) {
                setLastError(ApiCommon.ApiError.OTHER_ERROR);
                setLastErrorMessage(e.getMessage());

                e.printStackTrace();
            }

            m_isLoading.postValue(false);
        });
    }

    public LiveData<Integer> getLoadingProgress() {
        return m_loadingProgress;
    }

    public LiveData<Long> getUpdatesData() {
        return m_lastUpdate;
    }

    public LiveData<Boolean> getIsLoading() {
        return m_isLoading;
    }

    public LiveData<List<Feed>> getFeeds() {
        return m_feeds;
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

    static class FeedOrderComparator implements Comparator<Feed> {

        @Override
        public int compare(Feed a, Feed b) {
            if (a.id >= 0 && b.id >= 0)
                if (a.is_cat && b.is_cat)
                    if (a.order_id != 0 && b.order_id != 0)
                        return a.order_id - b.order_id;
                    else
                        return a.title.compareToIgnoreCase(b.title);
                else if (a.is_cat)
                    return -1;
                else if (b.is_cat)
                    return 1;
                else if (a.order_id != 0 || b.order_id != 0)
                    // feed with a zero order_id appears first
                    return a.order_id - b.order_id;
                else
                    // title used as tie breaker when both feeds have a zero order_id
                    return a.title.compareToIgnoreCase(b.title);
            else if (a.id < CommonActivity.LABEL_BASE_INDEX && b.id < CommonActivity.LABEL_BASE_INDEX)
                return a.title.compareToIgnoreCase(b.title);
            else
                return a.id - b.id;
        }

    }

    static class FeedUnreadComparator implements Comparator<Feed> {

        @Override
        public int compare(Feed a, Feed b) {
            if (a.unread != b.unread)
                return b.unread - a.unread;
            else
                return a.title.compareToIgnoreCase(b.title);
        }

    }

    static class FeedTitleComparator implements Comparator<Feed> {

        @Override
        public int compare(Feed a, Feed b) {
            if (a.is_cat && b.is_cat)
                return a.title.compareToIgnoreCase(b.title);
            else if (a.is_cat && !b.is_cat)
                return -1;
            else if (!a.is_cat && b.is_cat)
                return 1;
            else if (a.id >= 0 && b.id >= 0)
                return a.title.compareToIgnoreCase(b.title);
            else
                return a.id - b.id;
        }

    }

    static class SpecialOrderComparator implements Comparator<Feed> {
        static List<Integer> order = Arrays.asList(Feed.ALL_ARTICLES, Feed.FRESH, Feed.MARKED,
                Feed.PUBLISHED, Feed.ARCHIVED, Feed.RECENTLY_READ);

        @Override
        public int compare(Feed a, Feed b) {
            return Integer.valueOf(order.indexOf(a.id)).compareTo(order.indexOf(b.id));
        }
    }

    protected void sortFeeds(@NonNull List<Feed> feeds, @NonNull Feed feed, @Nullable Comparator<Feed> comparator) {

        if (comparator == null) {
            if (feed.id == -1) {
                comparator = new SpecialOrderComparator();
            } else {
                if (m_prefs.getBoolean("sort_feeds_by_unread", false)) {
                    comparator = new FeedUnreadComparator();
                } else {
                    if (org.fox.ttrss.Application.getInstance().getApiLevel() >= 3) {
                        comparator = new FeedOrderComparator();
                    } else {
                        comparator = new FeedTitleComparator();
                    }
                }
            }
        }

        try {
            feeds.sort(comparator);
        } catch (IllegalArgumentException e) {
            //
        }
    }

}
