package org.fox.ttrss;

import android.annotation.SuppressLint;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.loader.content.Loader;

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

public class RootCategoriesFragment extends FeedsFragment {
	private final String TAG = this.getClass().getSimpleName();

	@Override
	@NonNull
	public Loader<JsonElement> onCreateLoader(int id, Bundle args) {
		HashMap<String, String> params = new HashMap<>();
		params.put("op", "getCategories");
		params.put("sid", m_activity.getSessionId());

		// this confusingly named option means "return top level categories only"
		params.put("enable_nested", "true");

		if (m_activity.getUnreadOnly())
			params.put("unread_only", "true");

		return new ApiLoader(getContext(), params);
	}

	@SuppressLint("DefaultLocale")
	static class CatOrderComparator implements Comparator<Feed> {

		@Override
		public int compare(Feed a, Feed b) {
			if (a.id >= 0 && b.id >= 0)
				if (a.order_id != 0 && b.order_id != 0)
					return a.order_id - b.order_id;
				else
					return a.title.toUpperCase().compareTo(b.title.toUpperCase());
			else
				return a.id - b.id;
		}

	}

	@Override
	protected void sortFeeds(List<Feed> feeds, Feed feed) {
		try {
			feeds.sort(new CatOrderComparator());
		} catch (IllegalArgumentException e) {
			//
		}
	}

	@Override
	protected int getIconForFeed(Feed feed) {
		if (feed.id == Feed.TYPE_TOGGLE_UNREAD)
			return super.getIconForFeed(feed);
		else if (feed.id == Feed.CAT_LABELS)
			return R.drawable.outline_label_24;
		else if (feed.id == Feed.CAT_SPECIAL)
			return R.drawable.baseline_folder_special_24;
		else
			return R.drawable.baseline_folder_open_24;
	}

	@Override
	public void onLoadFinished(Loader<JsonElement> loader, JsonElement result) {
		if (m_swipeLayout != null) m_swipeLayout.setRefreshing(false);

		if (result != null) {
			try {
				JsonArray content = result.getAsJsonArray();
				if (content != null) {

					Type listType = new TypeToken<List<Feed>>() {}.getType();
					List<Feed> feedsJson = new Gson().fromJson(content, listType);

					List<Feed> feeds = new ArrayList<>();

					sortFeeds(feedsJson, m_rootFeed);

					// virtual cats implemented in getCategories since api level 1
					if (m_activity.getApiLevel() == 0) {
						feeds.add(0, new Feed(-2, getString(R.string.cat_labels), false));
						feeds.add(1, new Feed(-1, getString(R.string.cat_special), false));
						feeds.add(new Feed(0, getString(R.string.cat_uncategorized), false));
					}

					if (feedsJson.stream().noneMatch(a -> a.id == -1))
						feeds.add(new Feed(-1, getString(R.string.cat_special), true));

					for (Feed f : feedsJson) {
						f.is_cat = true;
						feeds.add(f);
					}

					feeds.add(new Feed(Feed.TYPE_DIVIDER));
					feeds.add(new Feed(Feed.TYPE_TOGGLE_UNREAD, getString(R.string.unread_only), true));

					m_adapter.submitList(feeds);

					return;
				}

			} catch (Exception e) {
				m_activity.toast(e.getMessage());
			}
		}

		ApiLoader apiLoader = (ApiLoader) loader;

		if (apiLoader.getLastError() != null && apiLoader.getLastError() != ApiCommon.ApiError.SUCCESS) {
			if (apiLoader.getLastError() == ApiCommon.ApiError.LOGIN_FAILED) {
				m_activity.login(true);
			} else {
				if (apiLoader.getLastErrorMessage() != null) {
					m_activity.toast(getString(apiLoader.getErrorMessage()) + "\n" + apiLoader.getLastErrorMessage());
				} else {
					m_activity.toast(apiLoader.getErrorMessage());
				}
			}
		}
	}
}

