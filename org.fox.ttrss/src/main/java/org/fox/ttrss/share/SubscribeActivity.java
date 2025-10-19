package org.fox.ttrss.share;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import org.fox.ttrss.ApiCommon;
import org.fox.ttrss.ApiRequest;
import org.fox.ttrss.R;
import org.fox.ttrss.types.Feed;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SubscribeActivity extends CommonShareActivity {
    private static final String TAG = SubscribeActivity.class.getSimpleName();

    private Button m_postButton;
    private Button m_catButton;
    private CatListAdapter m_catAdapter;
    private FeedListAdapter m_feedAdapter;
    private final List<Feed> m_cats = new ArrayList<>();
    private final ArrayList<Map.Entry<String, JsonElement>> m_feeds = new ArrayList<>();
    private ProgressBar m_progressBar;

    private static final int REQ_CATS = 1;
    private static final int REQ_POST = 2;

    static class TitleComparator implements Comparator<Feed> {

        @Override
        public int compare(Feed a, Feed b) {
            if (a.id >= 0 && b.id >= 0)
                return a.title.compareTo(b.title);
            else
                return a.id - b.id;
        }

    }

    public void sortCats() {

        if (m_catAdapter != null) {
            m_cats.sort(new TitleComparator());
            m_catAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_LEFT_ICON);

        String urlValue = getIntent().getDataString();

        if (urlValue == null)
            urlValue = getIntent().getStringExtra(Intent.EXTRA_TEXT);

        if (savedInstanceState != null) {
            urlValue = savedInstanceState.getString("url");
        }

        setContentView(R.layout.activity_subscribe);

        setSmallScreen(false);

        m_progressBar = findViewById(R.id.subscribe_progress);
        Spinner catList = findViewById(R.id.category_spinner);

        if (m_cats.isEmpty()) m_cats.add(new Feed(0, "Uncategorized", true));

        m_catAdapter = new CatListAdapter(this, android.R.layout.simple_spinner_dropdown_item, m_cats);
        catList.setAdapter(m_catAdapter);

        final Spinner feedList = findViewById(R.id.feed_spinner);
        feedList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String feed = m_feedAdapter.getItemURL(position);
                EditText feedUrl = findViewById(R.id.feed_url);

                if (feed != null && feedUrl != null) {
                    feedUrl.setText(feed);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        m_feedAdapter = new FeedListAdapter(this, android.R.layout.simple_spinner_dropdown_item, m_feeds);

        feedList.setAdapter(m_feedAdapter);

        EditText feedUrl = (EditText) findViewById(R.id.feed_url);
        feedUrl.setText(urlValue);

        m_postButton = (Button) findViewById(R.id.subscribe_button);

        m_postButton.setOnClickListener(v -> login(REQ_POST));

        m_catButton = (Button) findViewById(R.id.cats_button);

        m_catButton.setOnClickListener(v -> login(REQ_CATS));

        login(REQ_CATS);
    }

    @Override
    public void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);

        EditText url = findViewById(R.id.url);

        if (url != null) {
            out.putString("url", url.getText().toString());
        }

    }

    private void subscribeToFeed() {
        m_postButton.setEnabled(false);

        @SuppressLint("StaticFieldLeak") ApiRequest req = new ApiRequest(getApplicationContext()) {
            protected void onPostExecute(JsonElement result) {
                m_progressBar.setVisibility(View.INVISIBLE);

                if (m_lastError != null && m_lastError != ApiCommon.ApiError.SUCCESS) {
                    toast(getErrorMessage());
                } else {
                    try {
                        int rc = -1;

                        try {
                            rc = result.getAsJsonObject().get("status").getAsJsonObject().get("code").getAsInt();
                        } catch (Exception e) {
                            toast(e.getMessage());
                        }

                        switch (rc) {
                            case -1:
                                toast(R.string.error_api_unknown);
                                //finish();
                                break;
                            case 0:
                                toast(R.string.error_feed_already_exists_);
                                //finish();
                                break;
                            case 1:
                                toast(R.string.subscribed_to_feed);
                                finish();
                                break;
                            case 2:
                                toast(R.string.error_invalid_url);
                                break;
                            case 3:
                                toast(R.string.error_url_is_an_html_page_no_feeds_found);
                                break;
                            case 4:
                                //toast(R.string.error_url_contains_multiple_feeds);

                                JsonObject feeds = result.getAsJsonObject().get("status").getAsJsonObject().get("feeds").getAsJsonObject();

                                if (feeds != null) {
                                    m_feeds.clear();
                                    m_feeds.addAll(feeds.entrySet());

                                    m_feedAdapter.notifyDataSetChanged();

                                    findViewById(R.id.feed_spinner).setVisibility(View.VISIBLE);

                                } else {
                                    toast(R.string.error_while_subscribing);
                                }

                                break;
                            case 5:
                                toast(R.string.error_could_not_download_url);
                                break;
                        }

                    } catch (Exception e) {
                        toast(e.getMessage());
                    }
                }

                m_postButton.setEnabled(true);
            }
        };

        Spinner catSpinner = findViewById(R.id.category_spinner);

        final Feed cat = m_catAdapter.getCategory(catSpinner.getSelectedItemPosition());
        final EditText feedUrl = findViewById(R.id.feed_url);

        if (feedUrl != null) {
            HashMap<String, String> map = new HashMap<>();
            map.put("sid", m_sessionId);
            map.put("op", "subscribeToFeed");
            map.put("feed_url", feedUrl.getText().toString());

            if (cat != null) {
                map.put("category_id", String.valueOf(cat.id));
            }

            m_progressBar.setVisibility(View.VISIBLE);

            req.execute(map);
        }
    }

    @Override
    public void onLoggingIn(int requestId) {
        switch (requestId) {
            case REQ_CATS:
                m_catButton.setEnabled(false);
                break;
            case REQ_POST:
                m_postButton.setEnabled(false);
                break;
        }
    }

    private void updateCats() {
        @SuppressLint("StaticFieldLeak") ApiRequest req = new ApiRequest(getApplicationContext()) {
            protected void onPostExecute(JsonElement result) {
                m_progressBar.setVisibility(View.INVISIBLE);

                if (m_lastError != null && m_lastError != ApiCommon.ApiError.SUCCESS) {
                    toast(getErrorMessage());
                } else {
                    JsonArray content = result.getAsJsonArray();

                    if (content != null) {
                        Type listType = new TypeToken<List<Feed>>() {
                        }.getType();
                        final List<Feed> catsJson = new Gson().fromJson(content, listType);

                        m_cats.clear();
                        m_cats.addAll(catsJson.stream().filter(f -> f.id > 0).collect(Collectors.toList()));

                        sortCats();

                        m_cats.add(0, new Feed(0, "Uncategorized", true));

                        m_catAdapter.notifyDataSetChanged();

                        toast(R.string.category_list_updated);
                    }
                }

                m_catButton.setEnabled(true);
            }
        };

        HashMap<String, String> map = new HashMap<>();
        map.put("sid", m_sessionId);
        map.put("op", "getCategories");

        m_progressBar.setVisibility(View.VISIBLE);

        req.execute(map);
    }

    @Override
    protected void onLoggedIn(int requestId) {
        switch (requestId) {
            case REQ_CATS:
                updateCats();
                break;
            case REQ_POST:
                m_postButton.setEnabled(true);
                if (m_apiLevel < 5) {
                    toast(R.string.api_too_low);
                } else {
                    subscribeToFeed();
                }
                break;
        }
    }

    private static class CatListAdapter extends ArrayAdapter<String> {
        private final List<Feed> m_items;

        public CatListAdapter(Context context, int resource,
                              List<Feed> items) {
            super(context, resource);

            m_items = items;
        }

        @Override
        public String getItem(int item) {
            return m_items.get(item).title;
        }

        public Feed getCategory(int item) {
            try {
                return m_items.get(item);
            } catch (ArrayIndexOutOfBoundsException e) {
                return null;
            }
        }

        @Override
        public int getCount() {
            return m_items.size();
        }
    }

    private static class FeedListAdapter extends ArrayAdapter<String> {
        private final List<Map.Entry<String, JsonElement>> m_items;

        public FeedListAdapter(Context context, int resource, List<Map.Entry<String, JsonElement>> items) {
            super(context, resource);

            m_items = items;
        }

        @Override
        public String getItem(int item) {
            return m_items.get(item).getValue().getAsString();
        }

        public String getItemURL(int item) {
            try {
                return m_items.get(item).getKey();
            } catch (ArrayIndexOutOfBoundsException e) {
                return null;
            }
        }

        @Override
        public int getCount() {
            return m_items.size();
        }
    }

}
