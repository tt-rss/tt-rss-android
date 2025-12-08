package org.fox.ttrss;

import android.annotation.SuppressLint;
import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;

import org.fox.ttrss.types.Feed;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class RootCategoriesModel extends FeedsModel {
    private static final String TAG = RootCategoriesModel.class.getSimpleName();

    public RootCategoriesModel(@NonNull Application application) {
        super(application);
    }

    @Override
    protected void loadInBackground() {
        Log.d(TAG, this + " loadInBackground");

        m_isLoading.postValue(true);

        boolean expandSpecial = m_prefs.getBoolean("expand_special_cat", true);

        final List<Feed> feedsCombined = new ArrayList<>();

        m_executor.execute(() -> {

            // get special category with counters for embedding
            if (expandSpecial) {
                final HashMap<String, String> params = new HashMap<>();

                params.put("op", "getFeeds");
                params.put("cat_id", String.valueOf(Feed.CAT_SPECIAL));
                params.put("include_nested", "true");
                params.put("sid", ((org.fox.ttrss.Application) getApplication()).getSessionId());

                final JsonElement result = ApiCommon.performRequest(getApplication(), params, this);

                if (BuildConfig.DEBUG)
                    Log.d(TAG, "got result=" + result);

                try {
                    JsonArray content = result.getAsJsonArray();
                    if (content != null) {

                        Type listType = new TypeToken<List<Feed>>() {
                        }.getType();

                        List<Feed> feedsJson = new Gson().fromJson(content, listType);

                        // seems to be necessary evil because of deserialization
                        feedsJson = feedsJson.stream().peek(Feed::fixNullFields).collect(Collectors.toList());

                        sortFeeds(feedsJson, m_feed, new SpecialOrderComparator());

                        feedsCombined.addAll(feedsJson);
                    }
                } catch (Exception e) {
                    setLastError(ApiCommon.ApiError.OTHER_ERROR);
                    setLastErrorMessage(e.getMessage());

                    e.printStackTrace();
                }
            }

            // get all root categories
            final HashMap<String, String> params = new HashMap<>();

            params.put("op", "getCategories");
            params.put("sid", ((org.fox.ttrss.Application) getApplication()).getSessionId());

            // this confusingly named option means "return top level categories only"
            params.put("enable_nested", "true");

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

                    sortFeeds(feedsJson, m_feed, new CatOrderComparator());

                    // virtual cats implemented in getCategories since api level 1
                    if (org.fox.ttrss.Application.getInstance().getApiLevel() == 0) {
                        feedsCombined.add(0, new Feed(-2, getApplication().getString(R.string.cat_labels), true));

                        if (!expandSpecial)
                            feedsCombined.add(1, new Feed(-1, getApplication().getString(R.string.cat_special), true));

                        feedsCombined.add(new Feed(0, getApplication().getString(R.string.cat_uncategorized), true));
                    }

                    if (unreadOnly)
                        feedsJson = feedsJson.stream()
                                .filter(f -> f.id == Feed.CAT_SPECIAL || f.unread > 0)
                                .collect(Collectors.toList());

                    // force returned objects to become categories
                    feedsJson = feedsJson.stream()
                            .peek(f -> f.is_cat = true)
                            .collect(Collectors.toList());

                    if (expandSpecial) {
                        feedsJson = feedsJson.stream()
                                .filter(f -> f.id != Feed.CAT_SPECIAL)
                                .collect(Collectors.toList());

                        if (!feedsJson.isEmpty())
                            feedsCombined.add(new Feed(Feed.TYPE_DIVIDER));
                    }

                    feedsCombined.addAll(feedsJson);

                    m_feeds.postValue(feedsCombined);
                }
            } catch (Exception e) {
                setLastError(ApiCommon.ApiError.OTHER_ERROR);
                setLastErrorMessage(e.getMessage());

                e.printStackTrace();
            }

            m_isLoading.postValue(false);
        });
    }

    static class CatOrderComparator implements Comparator<Feed> {

        @Override
        public int compare(Feed a, Feed b) {
            if (a.id >= 0 && b.id >= 0)
                if (a.order_id != 0 && b.order_id != 0)
                    return a.order_id - b.order_id;
                else
                    return a.title.compareToIgnoreCase(b.title);
            else
                return a.id - b.id;
        }

    }

}
