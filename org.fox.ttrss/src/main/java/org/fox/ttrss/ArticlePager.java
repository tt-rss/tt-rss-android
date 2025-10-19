package org.fox.ttrss;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import org.fox.ttrss.types.Article;
import org.fox.ttrss.types.Feed;
import org.fox.ttrss.util.ArticleDiffItemCallback;
import org.fox.ttrss.util.DiffFragmentStateAdapter;

import java.util.ArrayList;

public class ArticlePager extends androidx.fragment.app.Fragment {

    private static final String TAG = ArticlePager.class.getSimpleName();
    private PagerAdapter m_adapter;
    private HeadlinesEventListener m_listener;
    private OnlineActivity m_activity;
    private Feed m_feed;
    private ViewPager2 m_pager;

    private static class PagerAdapter extends DiffFragmentStateAdapter<Article> {

        public PagerAdapter(@NonNull Fragment fragment) {
            super(fragment, new ArticleDiffItemCallback());
        }

        private void syncToSharedArticles() {
            submitList(new ArrayList<>(Application.getArticles()));
        }

        @Override
        @NonNull
        public Fragment createFragment(int position) {
            Article article = getItem(position);

            ArticleFragment af = new ArticleFragment();
            af.initialize(article);

            return af;
        }
    }

    public void initialize(int articleId, Feed feed) {
        m_feed = feed;
    }

    @Override
    public void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);

        out.putParcelable("m_feed", m_feed);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            m_feed = savedInstanceState.getParcelable("m_feed");
        }

        setRetainInstance(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_article_pager, container, false);

        m_adapter = new PagerAdapter(this);
        m_adapter.submitList(Application.getArticles());

        ArticleModel model = Application.getArticlesModel();

        // deal with further updates
        model.getArticles().observe(getActivity(), articles -> {
            Log.d(TAG, "observed article list size=" + articles.size());
            m_adapter.submitList(articles);
        });

        model.getActive().observe(getActivity(), (activeArticle) -> {
            Log.d(TAG, "observed active article=" + activeArticle);

            if (activeArticle != null) {
                int position = model.getArticles().getValue().indexOf(activeArticle);

                if (position != -1 && position != m_pager.getCurrentItem())
                    m_pager.setCurrentItem(position, false);
            }
        });

        m_pager = view.findViewById(R.id.article_pager);

        m_pager.setAdapter(m_adapter);
        m_pager.setOffscreenPageLimit(3);

        m_pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                Log.d(TAG, "onPageSelected: " + position);

                // wtf
                if (position != -1) {
                    Article article = Application.getArticles().get(position);

                    m_listener.onArticleSelected(article);
                }
            }
        });

        return view;
    }

    @Override
    public void onAttach(@NonNull Activity activity) {
        super.onAttach(activity);

        m_listener = (HeadlinesEventListener) activity;
        m_activity = (OnlineActivity) activity;
    }

    @SuppressLint("NewApi")
    @Override
    public void onResume() {
        super.onResume();

        m_activity.invalidateOptionsMenu();
    }

    public void switchToArticle(boolean next) {
        int position = m_pager.getCurrentItem();

        if (position != -1) {

            if (next)
                position++;
            else
                position--;

            try {
                Article targetArticle = Application.getArticles().get(position);

                Application.getArticlesModel().setActive(targetArticle);

            } catch (IndexOutOfBoundsException e) {
                e.printStackTrace();
            }
        }
    }

    public void syncToSharedArticles() {
        if (m_adapter != null)
            m_adapter.syncToSharedArticles();
    }
}
