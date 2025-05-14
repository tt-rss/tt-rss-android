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
import androidx.recyclerview.widget.DiffUtil;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import org.fox.ttrss.types.Article;
import org.fox.ttrss.types.ArticleList;
import org.fox.ttrss.types.Feed;
import org.fox.ttrss.util.DiffFragmentStateAdapter;
import org.fox.ttrss.util.HeadlinesDiffItemCallback;
import org.fox.ttrss.util.HeadlinesDiffUtilCallback;

public class ArticlePager extends androidx.fragment.app.Fragment {

	private final String TAG = this.getClass().getSimpleName();
	private PagerAdapter m_adapter;
	private HeadlinesEventListener m_listener;
	private int m_articleId;
	private OnlineActivity m_activity;
	private Feed m_feed;
	private ViewPager2 m_pager;

	private static class PagerAdapter extends DiffFragmentStateAdapter<Article> {

		public PagerAdapter(@NonNull Fragment fragment) {
			super(fragment, new HeadlinesDiffItemCallback());

			syncToSharedArticles();
		}

		private void syncToSharedArticles() {
			ArticleList tmp = new ArticleList();
			tmp.addAll(Application.getArticles());

			submitList(tmp);
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
		m_articleId = articleId;
		m_feed = feed;
	}

	@Override
	public void onSaveInstanceState(Bundle out) {
		super.onSaveInstanceState(out);

		out.putInt("m_articleId", m_articleId);
		out.putParcelable("m_feed", m_feed);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (savedInstanceState != null) {
			m_articleId = savedInstanceState.getInt("m_articleId");
			m_feed = savedInstanceState.getParcelable("m_feed");
		}

		setRetainInstance(true);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {    	
		View view = inflater.inflate(R.layout.fragment_article_pager, container, false);

		m_adapter = new PagerAdapter(this);
		
		m_pager = view.findViewById(R.id.article_pager);

		m_listener.onArticleSelected(Application.getArticles().getById(m_articleId), false);

		m_pager.setAdapter(m_adapter);
		m_pager.setOffscreenPageLimit(3);

		int position = Application.getArticles().getPositionById(m_articleId);

		if (position != -1)
			m_pager.setCurrentItem(position, false);

		m_pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
			@Override
			public void onPageSelected(int position) {
				Log.d(TAG, "onPageSelected: " + position);

				// wtf
				if (position != -1) {
					Article article = Application.getArticles().get(position);

					if (article != null) {
						m_articleId = article.id;

						m_listener.onArticleSelected(article, false);
					}
				}
			}
		});

		return view;
	}

	@Override
	public void onAttach(@NonNull Activity activity) {
		super.onAttach(activity);		
		
		m_listener = (HeadlinesEventListener)activity;
		m_activity = (OnlineActivity)activity;
	}

	@SuppressLint("NewApi")
	@Override
	public void onResume() {
		super.onResume();
		m_activity.invalidateOptionsMenu();
	}

	public void setActiveArticleId(int articleId) {
		if (m_pager != null && articleId != m_articleId) {
			int position = Application.getArticles().getPositionById(articleId);

			if (position != -1)
				m_pager.setCurrentItem(position, false);
		}
	}

	public void switchToArticle(boolean next) {
		int position = Application.getArticles().getPositionById(m_articleId);

		if (position != -1) {

			if (next)
				position++;
			else
				position--;

			try {
				Article targetArticle = Application.getArticles().get(position);

				setActiveArticleId(targetArticle.id);
			} catch (IndexOutOfBoundsException e) {
				e.printStackTrace();
			}
		}
	}

	public int getSelectedArticleId() {
		return m_articleId;
	}

	public void notifyItemChanged(int position) {
		if (m_adapter != null)
			m_adapter.notifyItemChanged(position);
	}

	public void syncToSharedArticles() {
		if (m_adapter != null)
			m_adapter.syncToSharedArticles();
	}
}
