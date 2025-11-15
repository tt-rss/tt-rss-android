package org.fox.ttrss;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Point;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.Html;
import android.transition.Fade;
import android.transition.Transition;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.amulyakhare.textdrawable.TextDrawable;
import com.amulyakhare.textdrawable.util.ColorGenerator;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.DrawableImageViewTarget;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.target.Target;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import org.fox.ttrss.glide.ProgressTarget;
import org.fox.ttrss.types.Article;
import org.fox.ttrss.types.Attachment;
import org.fox.ttrss.types.Feed;
import org.fox.ttrss.util.ArticleDiffItemCallback;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

public class HeadlinesFragment extends androidx.fragment.app.Fragment {

    private boolean m_isLazyLoading;

    public void notifyItemChanged(int position) {
        if (m_adapter != null)
            m_adapter.notifyItemChanged(position);
    }

    public static final int FLAVOR_IMG_MIN_SIZE = 128;

    private static final String TAG = HeadlinesFragment.class.getSimpleName();

    private Feed m_feed;

    private String m_searchQuery = "";

    private SharedPreferences m_prefs;

    private ArticleListAdapter m_adapter;
    private final List<Article> m_readArticles = new ArrayList<>();
    private HeadlinesEventListener m_listener;
    private OnlineActivity m_activity;
    private SwipeRefreshLayout m_swipeLayout;
    private boolean m_compactLayoutMode = false;
    private RecyclerView m_list;
    private LinearLayoutManager m_layoutManager;
    private HeadlinesFragmentModel m_headlinesFragmentModel;

    private MediaPlayer m_mediaPlayer;
    private TextureView m_activeTexture;

    public void initialize(Feed feed) {
        m_feed = feed;
    }

    public void initialize(Feed feed, boolean compactMode) {
        m_feed = feed;
        m_compactLayoutMode = compactMode;
    }

    public boolean onArticleMenuItemSelected(MenuItem item, Article article, int position) {

        if (article == null) return false;

        int itemId = item.getItemId();
        if (itemId == R.id.article_set_labels) {
            m_activity.editArticleLabels(article);
            return true;
        } else if (itemId == R.id.article_edit_note) {
            m_activity.editArticleNote(article);
            return true;
        } else if (itemId == R.id.headlines_article_unread) {
            Article articleClone = new Article(article);
            articleClone.unread = !articleClone.unread;

            m_activity.saveArticleUnread(articleClone);

            return true;
        } else if (itemId == R.id.headlines_article_link_copy) {
            m_activity.copyToClipboard(article.link);
            return true;
        } else if (itemId == R.id.headlines_article_link_open) {
            m_activity.openUri(Uri.parse(article.link));

            if (article.unread) {
                Article articleClone = new Article(article);
                articleClone.unread = !articleClone.unread;

                m_activity.saveArticleUnread(articleClone);
            }
            return true;
        } else if (itemId == R.id.headlines_share_article) {
            m_activity.shareArticle(article);
            return true;
        } else if (itemId == R.id.catchup_above) {
            m_activity.confirmCatchupAbove(article);
            return true;
        }
        Log.d(TAG, "onArticleMenuItemSelected, unhandled id=" + item.getItemId());
        return false;
    }

    // all onContextItemSelected are invoked in sequence so we might get a context menu for headlines, etc
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item
                .getMenuInfo();

        if (info != null) {
            try {
                Article article = Application.getArticles().get(info.position);

                if (!onArticleMenuItemSelected(item, article, info.position))
                    return super.onContextItemSelected(item);
            } catch (IndexOutOfBoundsException e) {
                e.printStackTrace();
            }
        }

        Log.d(TAG, "onContextItemSelected, unhandled id=" + item.getItemId());
        return super.onContextItemSelected(item);
    }

    public HeadlinesFragment() {
        super();

        Transition fade = new Fade();

        setEnterTransition(fade);
        setReenterTransition(fade);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
                                    ContextMenuInfo menuInfo) {

        getActivity().getMenuInflater().inflate(R.menu.context_headlines, menu);

        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;

        Article article = m_adapter.getCurrentList().get(info.position);

        menu.setHeaderTitle(article.title);

        menu.findItem(R.id.article_set_labels).setEnabled(m_activity.getApiLevel() >= 1);
        menu.findItem(R.id.article_edit_note).setEnabled(m_activity.getApiLevel() >= 1);

        super.onCreateContextMenu(menu, v, menuInfo);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            m_feed = savedInstanceState.getParcelable("m_feed");
            m_searchQuery = savedInstanceState.getString("m_searchQuery");
            m_compactLayoutMode = savedInstanceState.getBoolean("m_compactLayoutMode");
        }

        setRetainInstance(true);

        Glide.get(getContext()).clearMemory();
    }

    @Override
    public void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);

        out.putParcelable("m_feed", m_feed);
        out.putString("m_searchQuery", m_searchQuery);
        out.putBoolean("m_compactLayoutMode", m_compactLayoutMode);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView");

        m_headlinesFragmentModel = new ViewModelProvider(this).get(HeadlinesFragmentModel.class);

        String headlineMode = m_prefs.getString("headline_mode", "HL_DEFAULT");

        if ("HL_COMPACT".equals(headlineMode) || "HL_COMPACT_NOIMAGES".equals(headlineMode))
            m_compactLayoutMode = true;

        DisplayMetrics metrics = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(metrics);

        View view = inflater.inflate(R.layout.fragment_headlines, container, false);

        m_swipeLayout = view.findViewById(R.id.headlines_swipe_container);

        // see below re: viewpager2
        if (!(m_activity instanceof DetailActivity))
            m_swipeLayout.setOnRefreshListener(() -> refresh(false));
        else
            m_swipeLayout.setEnabled(false);

        m_list = view.findViewById(R.id.headlines_list);
        registerForContextMenu(m_list);

        m_layoutManager = new LinearLayoutManager(m_activity.getApplicationContext());
        m_list.setLayoutManager(m_layoutManager);
        m_list.setItemAnimator(new DefaultItemAnimator());

        m_adapter = new ArticleListAdapter();
        m_list.setAdapter(m_adapter);

        if (savedInstanceState == null && Application.getArticles().isEmpty()) {
            refresh(false);
        }

        // we disable this because default implementationof viewpager2 does not support removing/reordering/changing items
        // https://stackoverflow.com/questions/69368198/delete-item-in-android-viewpager2
        if (m_prefs.getBoolean("headlines_swipe_to_dismiss", true) && !(m_activity instanceof DetailActivity)) {

            ItemTouchHelper swipeHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {

                @Override
                public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                    return false;
                }

                @Override
                public int getSwipeDirs(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {

                    int position = viewHolder.getBindingAdapterPosition();

                    try {
                        Article article = Application.getArticles().get(position);

                        if (article == null || article.id < 0)
                            return 0;
                    } catch (IndexOutOfBoundsException e) {
                        return 0;
                    }

                    return super.getSwipeDirs(recyclerView, viewHolder);
                }

                @Override
                public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {

                    final int adapterPosition = viewHolder.getBindingAdapterPosition();

                    try {
                        final Article article = Application.getArticles().get(adapterPosition);
                        final boolean wasUnread;

                        if (article != null && article.id > 0) {
                            if (article.unread) {
                                wasUnread = true;

                                article.unread = false;
                                m_activity.saveArticleUnread(article);
                            } else {
                                wasUnread = false;
                            }

                            List<Article> tmpRemove = new ArrayList<>(Application.getArticles());
                            tmpRemove.remove(adapterPosition);

                            Application.getArticlesModel().update(tmpRemove);

                            Snackbar.make(m_list, R.string.headline_undo_row_prompt, Snackbar.LENGTH_LONG)
                                    .setAction(getString(R.string.headline_undo_row_button), v -> {

                                        if (wasUnread) {
                                            article.unread = true;
                                            m_activity.saveArticleUnread(article);
                                        }

                                        List<Article> tmpInsert = new ArrayList<>(Application.getArticles());
                                        tmpInsert.add(adapterPosition, article);

                                        Application.getArticlesModel().update(tmpInsert);
                                    }).show();

                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });

            swipeHelper.attachToRecyclerView(m_list);

        }

        m_list.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);

                ArticleModel model = Application.getArticlesModel();

                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    if (!m_readArticles.isEmpty() && !m_isLazyLoading && !model.isLoading() && m_prefs.getBoolean("headlines_mark_read_scroll", false)) {
                        Log.d(TAG, "marking articles as read, count=" + m_readArticles.size());

                        // since we clear the list after we send the batch to mark as read, we need to pass a cloned arraylist here,
                        // otherwise nothing would get marked as read when async operation completes
                        m_activity.setArticlesUnread(new ArrayList<>(m_readArticles), Article.UPDATE_SET_FALSE);

                        m_readArticles.clear();

                        new Handler().postDelayed(() -> m_activity.refresh(false), 100);
                    }
                }
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                int firstVisibleItem = m_layoutManager.findFirstVisibleItemPosition();
                int lastVisibleItem = m_layoutManager.findLastVisibleItemPosition();

                // Log.d(TAG, "onScrolled: FVI=" + firstVisibleItem + " LVI=" + lastVisibleItem);

                if (m_prefs.getBoolean("headlines_mark_read_scroll", false)) {
                    for (int i = 0; i < firstVisibleItem; i++) {
                        try {
                            Article article = Application.getArticles().get(i);

                            if (article.unread && !m_readArticles.contains(article))
                                m_readArticles.add(new Article(article));

                        } catch (IndexOutOfBoundsException e) {
                            e.printStackTrace();
                        }
                    }

                    // Log.d(TAG, "pending to auto mark as read count=" + m_readArticles.size());
                }

                ArticleModel model = Application.getArticlesModel();

                if (dy > 0 && !m_isLazyLoading && !model.isLoading() && model.isLazyLoadEnabled() &&
                        lastVisibleItem >= Application.getArticles().size() - 5) {

                    Log.d(TAG, "attempting to lazy load more articles (onScrolled)...");

                    m_isLazyLoading = true;

                    // this has to be dispatched delayed, consequent adapter updates are forbidden in scroll handler
                    new Handler().postDelayed(() -> refresh(true), 250);
                }
            }
        });

        ArticleModel model = Application.getArticlesModel();

        model.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            Log.d(TAG, "observed headlines isLoading=" + isLoading + " lazyLoadEnabled=" + model.isLazyLoadEnabled());

            if (m_swipeLayout != null)
                m_swipeLayout.setRefreshing(isLoading);
        });

        // this gets notified on loading %
        model.getLoadingProgress().observe(getViewLifecycleOwner(), progress -> {
            Log.d(TAG, "observed headlines loading progress=" + progress);

            m_listener.onHeadlinesLoadingProgress(progress);
        });

        // this gets notified if active article changes
        model.getActive().observe(getViewLifecycleOwner(), (activeArticle) -> {
            Log.d(TAG, "observed active article=" + activeArticle);

            if (activeArticle != null) {

                // we can't be sure scrollToArticle() below actually does anything in DetailView because our fragment might be invisible in some layouts
                // so we also trigger lazy load on active article change
                if (m_activity instanceof DetailActivity) {
                    int position = Application.getArticles().indexOf(activeArticle);

                    if (!m_isLazyLoading && !model.isLoading() && model.isLazyLoadEnabled() &&
                            position >= Application.getArticles().size() - 5) {

                        Log.d(TAG, "attempting to lazy load more articles (observed active article change)...");

                        m_isLazyLoading = true;

                        // this has to be dispatched delayed, consequent adapter updates are forbidden in scroll handler
                        new Handler().postDelayed(() -> refresh(true), 250);
                    }
                }

                scrollToArticle(activeArticle);
            }
        });

        // this gets notified on network update
        model.getUpdatesData().observe(getViewLifecycleOwner(), lastUpdate -> {
            if (lastUpdate > 0) {
                List<Article> tmp = new ArrayList<>(model.getArticles().getValue());

                Log.d(TAG, "observed headlines last update=" + lastUpdate + " article count=" + tmp.size());

                if (m_prefs.getBoolean("headlines_mark_read_scroll", false))
                    tmp.add(new Article(Article.TYPE_AMR_FOOTER));

                final boolean appended = model.getAppend();

                m_adapter.submitList(tmp, () -> {
                    if (!appended)
                        m_list.scrollToPosition(0);

                    m_isLazyLoading = false;

                    m_listener.onHeadlinesLoaded(appended);
                });

                if (model.getFirstIdChanged() && getView() != null)
                    Snackbar.make(getView(), R.string.headlines_row_top_changed, Snackbar.LENGTH_LONG)
                            .setAction(R.string.reload, v -> refresh(false)).show();

                if (model.getLastError() != null && model.getLastError() != ApiCommon.ApiError.SUCCESS) {

                    m_isLazyLoading = false;

                    if (model.getLastError() == ApiCommon.ApiError.LOGIN_FAILED) {
                        m_activity.login();
                        return;
                    }

                    m_listener.onHeadlinesLoaded(appended);

                    if (model.getLastErrorMessage() != null) {
                        m_activity.toast(m_activity.getString(model.getErrorMessage()) + "\n" + model.getLastErrorMessage());
                    } else {
                        m_activity.toast(model.getErrorMessage());
                    }
                }
            }
        });

        // loaded articles might get modified for all sorts of reasons
        model.getArticles().observe(getViewLifecycleOwner(), articles -> {
            Log.d(TAG, "observed headlines article list size=" + articles.size());

            List<Article> tmp = new ArrayList<>(articles);

            if (m_prefs.getBoolean("headlines_mark_read_scroll", false))
                tmp.add(new Article(Article.TYPE_AMR_FOOTER));

            m_adapter.submitList(tmp);
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        Log.d(TAG, "onResume");

        syncToSharedArticles();

        m_activity.invalidateOptionsMenu();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        m_prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
        m_activity = (OnlineActivity) activity;
        m_listener = (HeadlinesEventListener) activity;
    }

    public void refresh(final boolean append) {
        ArticleModel model = Application.getArticlesModel();

        // we do not support non-append refreshes while in DetailActivity because of viewpager2
        if (m_activity instanceof DetailActivity && !append)
            return;

        if (!append) {
            model.setActive(null);
            model.setSelection(ArticleModel.ArticlesSelection.NONE);
        }

        model.startLoading(append, m_feed, m_activity.getResizeWidth());
    }

    static class ArticleViewHolder extends RecyclerView.ViewHolder {
        public View view;

        public TextView titleView;
        public TextView feedTitleView;
        public MaterialButton markedView;
        public MaterialButton scoreView;
        public MaterialButton publishedView;
        public TextView excerptView;
        public ImageView flavorImageView;
        public ImageView flavorVideoKindView;
        public TextView authorView;
        public TextView dateView;
        public CheckBox selectionBoxView;
        public MaterialButton menuButtonView;
        public ViewGroup flavorImageHolder;
        public ProgressBar flavorImageLoadingBar;
        public View headlineFooter;
        public ImageView textImage;
        public ImageView textChecked;
        public View headlineHeader;
        public View flavorImageOverflow;
        public TextureView flavorVideoView;
        public MaterialButton attachmentsView;
        public TextView linkHost;

        public ArticleViewHolder(View v) {
            super(v);

            view = v;

            titleView = v.findViewById(R.id.title);

            feedTitleView = v.findViewById(R.id.feed_title);
            markedView = v.findViewById(R.id.marked);
            scoreView = v.findViewById(R.id.score);
            publishedView = v.findViewById(R.id.published);
            excerptView = v.findViewById(R.id.excerpt);
            flavorImageView = v.findViewById(R.id.flavor_image);
            flavorVideoKindView = v.findViewById(R.id.flavor_video_kind);
            authorView = v.findViewById(R.id.author);
            dateView = v.findViewById(R.id.date);
            selectionBoxView = v.findViewById(R.id.selected);
            menuButtonView = v.findViewById(R.id.article_menu_button);
            flavorImageHolder = v.findViewById(R.id.flavor_image_holder);
            flavorImageLoadingBar = v.findViewById(R.id.flavor_image_progressbar);
            textImage = v.findViewById(R.id.text_image);
            textChecked = v.findViewById(R.id.text_checked);
            headlineHeader = v.findViewById(R.id.headline_header);
            flavorImageOverflow = v.findViewById(R.id.gallery_overflow);
            flavorVideoView = v.findViewById(R.id.flavor_video);
            attachmentsView = v.findViewById(R.id.attachments);
            linkHost = v.findViewById(R.id.link_host);
        }
    }

    private static class FlavorProgressTarget<Z> extends ProgressTarget<String, Z> {
        private final ArticleViewHolder holder;

        public FlavorProgressTarget(Target<Z> target, String model, ArticleViewHolder holder) {
            super(target);
            setModel(model);
            this.holder = holder;
        }

        @Override
        public float getGranualityPercentage() {
            return 0.1f; // this matches the format string for #text below
        }

        @Override
        protected void onConnecting() {
            holder.flavorImageHolder.setVisibility(View.VISIBLE);

            holder.flavorImageLoadingBar.setIndeterminate(true);
            holder.flavorImageLoadingBar.setVisibility(View.VISIBLE);
        }

        @Override
        protected void onDownloading(long bytesRead, long expectedLength) {
            holder.flavorImageHolder.setVisibility(View.VISIBLE);

            holder.flavorImageLoadingBar.setIndeterminate(false);
            holder.flavorImageLoadingBar.setProgress((int) (100 * bytesRead / expectedLength));
        }

        @Override
        protected void onDownloaded() {
            holder.flavorImageHolder.setVisibility(View.VISIBLE);

            holder.flavorImageLoadingBar.setIndeterminate(true);
        }

        @Override
        protected void onDelivered() {
            holder.flavorImageHolder.setVisibility(View.VISIBLE);

            holder.flavorImageLoadingBar.setVisibility(View.INVISIBLE);
        }
    }

    private class ArticleListAdapter extends ListAdapter<Article, ArticleViewHolder> {
        public static final int VIEW_NORMAL = 0;
        public static final int VIEW_AMR_FOOTER = 1;

        private final ColorGenerator m_colorGenerator = ColorGenerator.DEFAULT;
        private final TextDrawable.IBuilder m_drawableBuilder = TextDrawable.builder().round();
        private final ColorStateList m_cslTertiary;
        private final ColorStateList m_cslPrimary;
        private final int m_colorSurfaceContainerLowest;
        private final int m_colorSurface;
        private final int m_colorPrimary;
        private final int m_colorTertiary;
        private final int m_colorSecondary;
        private final int m_colorOnSurface;
        private final int m_colorTertiaryContainer;
        private final int m_colorOnTertiaryContainer;

        boolean m_flavorImageEnabled;
        private final int m_screenWidth;
        private final int m_screenHeight;

        private final int m_headlineSmallFontSize;
        private final int m_headlineFontSize;
        private final boolean m_enableIconTinting;

        private final ConnectivityManager m_cmgr;

        private boolean canShowFlavorImage() {
            if (m_flavorImageEnabled) {
                if (m_prefs.getBoolean("headline_images_wifi_only", false)) {
                    // why do i have to get this service every time instead of using a member variable :(
                    NetworkInfo wifi = m_cmgr.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

                    if (wifi != null)
                        return wifi.isConnected();

                } else {
                    return true;
                }
            }

            return false;
        }

        private int colorFromAttr(int attr) {
            TypedValue tv = new TypedValue();
            m_activity.getTheme().resolveAttribute(attr, tv, true);
            return ContextCompat.getColor(m_activity, tv.resourceId);
        }

        public ArticleListAdapter() {
            super(new ArticleDiffItemCallback());

            Display display = m_activity.getWindowManager().getDefaultDisplay();
            Point size = new Point();
            display.getSize(size);
            m_screenHeight = size.y;
            m_screenWidth = size.x;

            String headlineMode = m_prefs.getString("headline_mode", "HL_DEFAULT");
            m_flavorImageEnabled = "HL_DEFAULT".equals(headlineMode) || "HL_COMPACT".equals(headlineMode);

            m_colorPrimary = colorFromAttr(R.attr.colorPrimary);
            m_colorSecondary = colorFromAttr(R.attr.colorSecondary);
            m_colorTertiary = colorFromAttr(R.attr.colorTertiary);

            m_cslTertiary = ColorStateList.valueOf(m_colorTertiary);
            m_cslPrimary = ColorStateList.valueOf(m_colorPrimary);

            m_colorSurfaceContainerLowest = colorFromAttr(R.attr.colorSurfaceContainerLowest);
            m_colorSurface = colorFromAttr(R.attr.colorSurface);
            m_colorOnSurface = colorFromAttr(R.attr.colorOnSurface);

            m_colorTertiaryContainer = colorFromAttr(R.attr.colorTertiaryContainer);
            m_colorOnTertiaryContainer = colorFromAttr(R.attr.colorOnTertiaryContainer);

            m_headlineFontSize = m_prefs.getInt("headlines_font_size_sp_int", 13);
            m_headlineSmallFontSize = Math.max(10, Math.min(18, m_headlineFontSize - 2));

            m_enableIconTinting = m_prefs.getBoolean("enable_icon_tinting", true);

            m_cmgr = (ConnectivityManager) m_activity.getSystemService(Context.CONNECTIVITY_SERVICE);
        }

        @NonNull
        @Override
        public ArticleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

            int layoutId = m_compactLayoutMode ? R.layout.headlines_row_compact : R.layout.headlines_row;

            if (viewType == VIEW_AMR_FOOTER) {
                layoutId = R.layout.headlines_footer;
            }

            View v = LayoutInflater.from(parent.getContext()).inflate(layoutId, parent, false);

            ArticleViewHolder holder = new ArticleViewHolder(v);

            // set on click handlers once when view is created

            holder.view.setOnClickListener(view -> {
                int position = m_list.getChildAdapterPosition(view);

                if (position != -1) {
                    Article article = m_adapter.getItem(position);

                    m_listener.onArticleSelected(article);
                }
            });

            holder.view.setOnLongClickListener(view -> {
                m_list.showContextMenuForChild(view);
                return true;
            });

            // block footer clicks to make button/selection clicking easier
            if (holder.headlineFooter != null) {
                holder.headlineFooter.setOnClickListener(view -> {
                    //
                });
            }

            if (holder.attachmentsView != null) {
                holder.attachmentsView.setOnClickListener(view -> {
                    int position = m_list.getChildAdapterPosition(holder.view);

                    if (position != -1) {
                        Article article = m_adapter.getItem(position);
                        m_activity.displayAttachments(article);
                    }
                });
            }

            if (holder.flavorImageView != null) {
                holder.flavorImageView.setOnClickListener(view -> {
                    int position = m_list.getChildAdapterPosition(holder.view);

                    if (position != -1) {
                        Article article = m_adapter.getItem(position);
                        openGalleryForType(article, holder, holder.flavorImageView);
                    }
                });
            }

            if (holder.flavorImageOverflow != null) {
                holder.flavorImageOverflow.setOnClickListener(view -> {
                    PopupMenu popup = new PopupMenu(getContext(), holder.flavorImageOverflow);
                    MenuInflater inflater = popup.getMenuInflater();
                    inflater.inflate(R.menu.content_gallery_entry, popup.getMenu());

                    int position = m_list.getChildAdapterPosition(holder.view);

                    if (position != -1) {
                        Article article = m_adapter.getItem(position);

                        popup.setOnMenuItemClickListener(item -> {

                            Uri mediaUri = Uri.parse(article.flavorStreamUri != null ? article.flavorStreamUri :
                                    article.flavorImageUri);

                            int itemId = item.getItemId();
                            if (itemId == R.id.article_img_open) {
                                m_activity.openUri(mediaUri);
                                return true;
                            } else if (itemId == R.id.article_img_copy) {
                                m_activity.copyToClipboard(mediaUri.toString());
                                return true;
                            } else if (itemId == R.id.article_img_share) {
                                m_activity.shareImageFromUri(mediaUri.toString());
                                return true;
                            } else if (itemId == R.id.article_img_share_url) {
                                m_activity.shareText(mediaUri.toString());
                                return true;
                            } else if (itemId == R.id.article_img_view_caption) {
                                m_activity.displayImageCaption(article.flavorImageUri, article.content);
                                return true;
                            }
                            return false;
                        });

                        popup.show();
                    }
                });

                holder.flavorImageView.setOnLongClickListener(view -> {
                    m_list.showContextMenuForChild(holder.view);
                    return true;
                });
            }

            if (holder.menuButtonView != null) {
                holder.menuButtonView.setOnClickListener(view -> {

                    int position = m_list.getChildAdapterPosition(holder.view);

                    if (position != -1) {
                        PopupMenu popup = new PopupMenu(getContext(), view);
                        MenuInflater inflater = popup.getMenuInflater();
                        inflater.inflate(R.menu.context_headlines, popup.getMenu());

                        popup.getMenu().findItem(R.id.article_set_labels).setEnabled(m_activity.getApiLevel() >= 1);
                        popup.getMenu().findItem(R.id.article_edit_note).setEnabled(m_activity.getApiLevel() >= 1);

                        popup.setOnMenuItemClickListener(item -> onArticleMenuItemSelected(item,
                                getItem(position),
                                m_list.getChildAdapterPosition(holder.view)));

                        popup.show();
                    }
                });
            }

            if (holder.markedView != null) {
                holder.markedView.setOnClickListener(view -> {
                    int position = m_list.getChildAdapterPosition(holder.view);

                    if (position != -1) {
                        Article article = new Article(getItem(position));
                        article.marked = !article.marked;

                        m_activity.saveArticleMarked(article);
                    }
                });
            }

            if (holder.selectionBoxView != null) {
                holder.selectionBoxView.setOnClickListener(view -> {
                    int position = m_list.getChildAdapterPosition(holder.view);

                    if (position != -1) {
                        Article article = new Article(getItem(position));

                        CheckBox cb = (CheckBox) view;

                        article.selected = cb.isChecked();

                        Application.getArticlesModel().update(article);
                    }
                });
            }

            if (holder.publishedView != null) {
                holder.publishedView.setOnClickListener(view -> {
                    int position = m_list.getChildAdapterPosition(holder.view);

                    if (position != -1) {
                        Article article = new Article(getItem(position));
                        article.published = !article.published;

                        m_activity.saveArticlePublished(article);
                    }
                });
            }

            if (holder.textImage != null) {
                holder.textImage.setOnClickListener(view -> {
                    int position = m_list.getChildAdapterPosition(holder.view);

                    if (position != -1) {
                        Article article = new Article(getItem(position));
                        article.selected = !article.selected;

                        Application.getArticlesModel().update(article);
                    }
                });

                holder.textImage.setOnLongClickListener(view -> {
                    int position = m_list.getChildAdapterPosition(holder.view);

                    if (position != -1) {
                        Article article = getItem(position);

                        openGalleryForType(article, holder, holder.textImage);
                    }

                    return true;
                });
            }

            if (holder.scoreView != null) {
                if (m_activity.getApiLevel() >= 16) {
                    holder.scoreView.setOnClickListener(view -> {
                        int position = m_list.getChildAdapterPosition(holder.view);

                        if (position != -1) {

                            final Article articleClone = new Article(getItem(position));
                            final EditText edit = new EditText(getActivity());

                            edit.setText(String.valueOf(articleClone.score));

                            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext())
                                    .setTitle(R.string.score_for_this_article)
                                    .setPositiveButton(R.string.set_score,
                                            (dialog, which) -> {
                                                try {
                                                    articleClone.score = Integer.parseInt(edit.getText().toString());
                                                    m_activity.saveArticleScore(articleClone);

                                                } catch (NumberFormatException e) {
                                                    m_activity.toast(R.string.score_invalid);
                                                    e.printStackTrace();
                                                }
                                            })
                                    .setNegativeButton(getString(R.string.cancel),
                                            (dialog, which) -> {
                                            }).setView(edit);

                            Dialog dialog = builder.create();
                            dialog.show();
                        }
                    });
                } else {
                    holder.scoreView.setVisibility(View.GONE);
                }
            }

            return holder;
        }

        @Override
        public void onViewRecycled(@NonNull ArticleViewHolder holder) {
            super.onViewRecycled(holder);

            if (holder.flavorImageView != null)
                Glide.with(HeadlinesFragment.this).clear(holder.flavorImageView);
        }

        @Override
        // https://stackoverflow.com/questions/33176336/need-an-example-about-recyclerview-adapter-notifyitemchangedint-position-objec/50085835#50085835
        public void onBindViewHolder(@NonNull final ArticleViewHolder holder, final int position, final List<Object> payloads) {
            if (!payloads.isEmpty()) {
                Log.d(TAG, "onBindViewHolder, payloads=" + payloads + " position=" + position);

                final Article article = getItem(position);

                for (final Object pobject : payloads) {
                    ArticleDiffItemCallback.ChangePayload payload = (ArticleDiffItemCallback.ChangePayload) pobject;

                    switch (payload) {
                        case UNREAD:
                        case ACTIVE:
                            updateUnreadView(article, holder);
                            break;
                        case MARKED:
                            updateMarkedView(article, holder);
                            break;
                        case SELECTED:
                            updateSelectedView(article, holder);
                            updateTextImage(article, holder);
                            break;
                        case PUBLISHED:
                            updatePublishedView(article, holder);
                            break;
                        case SCORE:
                            updateScoreView(article, holder);
                            break;
                    }
                }
            } else {
                super.onBindViewHolder(holder, position, payloads);
            }
        }

        private void updateUnreadView(final Article article, final ArticleViewHolder holder) {
            if (m_compactLayoutMode) {
                holder.view.setBackgroundColor(article.unread ? m_colorSurfaceContainerLowest : 0);
            } else {
                MaterialCardView card = (MaterialCardView) holder.view;

                card.setCardBackgroundColor(article.unread ? m_colorSurfaceContainerLowest : m_colorSurface);
            }

            if (holder.titleView != null) {
                holder.titleView.setTypeface(null, article.unread ? Typeface.BOLD : Typeface.NORMAL);
                holder.titleView.setTextColor(article.unread ? m_colorOnSurface : m_colorPrimary);
            }

            updateActiveView(article, holder);
        }

        private void updateActiveView(final Article article, final ArticleViewHolder holder) {
            if (m_activity instanceof DetailActivity) {
                if (article.active) {
                    holder.view.setBackgroundColor(m_colorTertiaryContainer);

                    if (holder.titleView != null) {
                        holder.titleView.setTextColor(m_colorOnTertiaryContainer);
                    }
                }

                if (holder.excerptView != null) {
                    holder.excerptView.setTextColor(article.active ? m_colorOnTertiaryContainer : m_colorOnSurface);
                }

                if (holder.feedTitleView != null) {
                    holder.feedTitleView.setTextColor(article.active ? m_colorOnTertiaryContainer : m_colorSecondary);
                }
            }
        }

        @Override
        public void onBindViewHolder(@NonNull final ArticleViewHolder holder, int position) {
            Article article = getItem(position);

            if (article.id == Article.TYPE_AMR_FOOTER && m_prefs.getBoolean("headlines_mark_read_scroll", false)) {
                WindowManager wm = (WindowManager) m_activity.getSystemService(Context.WINDOW_SERVICE);
                Display display = wm.getDefaultDisplay();
                int screenHeight = (int) (display.getHeight() * 1.5);

                holder.view.setLayoutParams(new ListView.LayoutParams(ListView.LayoutParams.MATCH_PARENT, screenHeight));
            }

            // nothing else of interest for those below anyway
            if (article.id < 0) return;

            updateUnreadView(article, holder);
            updateTextImage(article, holder);
            updateTitleView(article, holder);
            updateMarkedView(article, holder);
            updateScoreView(article, holder);
            updatePublishedView(article, holder);
            updateAttachmentsView(article, holder);
            updateLinkHost(article, holder);
            updateExcerptView(article, holder);
            updateAuthorView(article, holder);
            updateDateView(article, holder);
            updateSelectedView(article, holder);

            if (!m_compactLayoutMode && holder.flavorImageHolder != null) {

                // reset our view to default in case of recycling
                holder.flavorImageLoadingBar.setVisibility(View.GONE);
                holder.flavorImageLoadingBar.setIndeterminate(false);

                holder.flavorImageView.setVisibility(View.GONE);
                holder.flavorVideoKindView.setVisibility(View.GONE);
                holder.flavorImageOverflow.setVisibility(View.GONE);
                holder.flavorVideoView.setVisibility(View.GONE);
                holder.flavorImageHolder.setVisibility(View.GONE);

                if (canShowFlavorImage() && article.flavorImageUri != null && holder.flavorImageView != null) {
                    int maxImageHeight = (int) (m_screenHeight * 0.5f);

                    // we also downsample below using glide to save RAM
                    holder.flavorImageView.setMaxHeight(maxImageHeight);

                    if (m_headlinesFragmentModel.getFlavorImageSizes().containsKey(article.flavorImageUri)) {
                        Size size = m_headlinesFragmentModel.getFlavorImageSizes().get(article.flavorImageUri);

                        if (BuildConfig.DEBUG)
                            Log.d(TAG, "using cached resource size for " + article.flavorImageUri + " " + size.getWidth() + "x" + size.getHeight());

                        if (size.getWidth() > FLAVOR_IMG_MIN_SIZE && size.getHeight() > FLAVOR_IMG_MIN_SIZE) {
                            loadFlavorImage(article, holder, maxImageHeight);
                        }

                    } else {
                        if (BuildConfig.DEBUG)
                            Log.d(TAG, "checking resource size for " + article.flavorImageUri);
                        checkImageAndLoad(article, holder, maxImageHeight);
                    }
                }

				/* if (m_prefs.getBoolean("inline_video_player", false) && article.flavorImage != null &&
						"video".equalsIgnoreCase(article.flavorImage.tagName()) && article.flavorStreamUri != null) {

					holder.flavorVideoView.setOnLongClickListener(v -> {
                        releaseSurface();
                        openGalleryForType(article, holder, holder.flavorImageView);
                        return true;
                    });

					holder.flavorImageView.setOnClickListener(view -> {
                        releaseSurface();
                        m_mediaPlayer = new MediaPlayer();

                        holder.flavorVideoView.setVisibility(View.VISIBLE);
                        final ProgressBar bar = holder.flavorImageLoadingBar;

                        bar.setIndeterminate(true);
                        bar.setVisibility(View.VISIBLE);

                        holder.flavorVideoView.setOnClickListener(v -> {
							try {
								if (m_mediaPlayer.isPlaying())
									m_mediaPlayer.pause();
								else
									m_mediaPlayer.start();
								} catch (IllegalStateException e) {
									releaseSurface();
								}
							});

                        m_activeTexture = holder.flavorVideoView;

                        ViewGroup.LayoutParams lp = m_activeTexture.getLayoutParams();

                        Drawable drawable = holder.flavorImageView.getDrawable();

                        if (drawable != null) {

                            float aspect = drawable.getIntrinsicWidth() / (float) drawable.getIntrinsicHeight();

                            lp.height = holder.flavorImageView.getMeasuredHeight();
                            lp.width = (int) (lp.height * aspect);

                            m_activeTexture.setLayoutParams(lp);
                        }

                        holder.flavorVideoView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                                 @Override
                                 public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                                     try {
                                         m_mediaPlayer.setSurface(new Surface(surface));

                                         m_mediaPlayer.setDataSource(article.flavorStreamUri);

                                         m_mediaPlayer.setOnPreparedListener(mp -> {
                                             try {
												 bar.setVisibility(View.GONE);
                                                 mp.setLooping(true);
                                                 mp.start();
                                             } catch (IllegalStateException e) {
                                                 e.printStackTrace();
                                             }
                                         });

                                         m_mediaPlayer.prepareAsync();
                                     } catch (Exception e) {
                                         e.printStackTrace();
                                     }

                                 }

                                 @Override
                                 public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

                                 }

                                 @Override
                                 public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                                     try {
                                         m_mediaPlayer.release();
                                     } catch (Exception e) {
                                         e.printStackTrace();
                                     }
                                     return false;
                                 }

                                 @Override
                                 public void onSurfaceTextureUpdated(SurfaceTexture surface) {

                                 }
                             }
                        );

                    });

				} else {
					holder.flavorImageView.setOnClickListener(view -> openGalleryForType(article, holder, holder.flavorImageView));
				} */
            }
        }

        private void updateTitleView(final Article article, final ArticleViewHolder holder) {
            if (holder.titleView != null) {
                holder.titleView.setText(Html.fromHtml(article.title));
                holder.titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.min(21, m_headlineFontSize + 3));
            }

            if (holder.feedTitleView != null) {
                if (article.feed_title != null && m_feed != null && (m_feed.is_cat || m_feed.id < 0)) {
                    holder.feedTitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, m_headlineSmallFontSize);
                    holder.feedTitleView.setText(article.feed_title);
                } else {
                    holder.feedTitleView.setVisibility(View.GONE);
                }
            }
        }

        private void updateDateView(final Article article, final ArticleViewHolder holder) {
            if (holder.dateView != null) {
                holder.dateView.setTextSize(TypedValue.COMPLEX_UNIT_SP, m_headlineSmallFontSize);

                Date d = new Date((long) article.updated * 1000);
                Date now = new Date();
                long half_a_year_ago = now.getTime() / 1000L - 182 * 24 * 60 * 60;

                DateFormat df;

                if (now.getYear() == d.getYear() && now.getMonth() == d.getMonth() && now.getDay() == d.getDay()) {
                    df = new SimpleDateFormat("HH:mm");
                } else if (article.updated > half_a_year_ago) {
                    df = new SimpleDateFormat("MMM dd");
                } else {
                    df = new SimpleDateFormat("MMM yyyy");
                }

                df.setTimeZone(TimeZone.getDefault());
                holder.dateView.setText(df.format(d));
            }
        }

        private void updateAuthorView(final Article article, final ArticleViewHolder holder) {
            String articleAuthor = article.author != null ? article.author : "";

            if (holder.authorView != null) {
                holder.authorView.setTextSize(TypedValue.COMPLEX_UNIT_SP, m_headlineSmallFontSize);

                if (!articleAuthor.isEmpty()) {
                    holder.authorView.setText(getString(R.string.author_formatted, articleAuthor));
                } else {
                    holder.authorView.setText("");
                }
            }
        }

        private void updateExcerptView(final Article article, final ArticleViewHolder holder) {
            if (holder.excerptView != null) {
                if (!m_prefs.getBoolean("headlines_show_content", true)) {
                    holder.excerptView.setVisibility(View.GONE);
                } else {
                    String excerpt = "";

                    try {
                        if (article.excerpt != null) {
                            excerpt = article.excerpt;
                        } else if (article.articleDoc != null) {
                            excerpt = article.articleDoc.text();

                            if (excerpt.length() > CommonActivity.EXCERPT_MAX_LENGTH)
                                excerpt = excerpt.substring(0, CommonActivity.EXCERPT_MAX_LENGTH) + "…";
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        excerpt = "";
                    }

                    holder.excerptView.setTextSize(TypedValue.COMPLEX_UNIT_SP, m_headlineFontSize);
                    holder.excerptView.setText(excerpt);

                    if (!excerpt.isEmpty()) {
                        holder.excerptView.setVisibility(View.VISIBLE);
                    } else {
                        holder.excerptView.setVisibility(View.GONE);
                    }
                }
            }
        }

        private void updateLinkHost(final Article article, final ArticleViewHolder holder) {
            if (holder.linkHost != null) {
                if (article.isHostDistinct()) {
                    holder.linkHost.setTextSize(TypedValue.COMPLEX_UNIT_SP, m_headlineSmallFontSize);
                    holder.linkHost.setText(article.getLinkHost());
                    holder.linkHost.setVisibility(View.VISIBLE);
                } else {
                    holder.linkHost.setVisibility(View.GONE);
                }
            }
        }

        private void updateAttachmentsView(final Article article, final ArticleViewHolder holder) {
            if (holder.attachmentsView != null) {
                if (article.attachments != null && !article.attachments.isEmpty()) {
                    holder.attachmentsView.setVisibility(View.VISIBLE);
                } else {
                    holder.attachmentsView.setVisibility(View.GONE);
                }
            }
        }

        private void updateMarkedView(final Article article, final ArticleViewHolder holder) {
            if (holder.markedView != null) {
                holder.markedView.setIconResource(article.marked ? R.drawable.baseline_star_24 : R.drawable.baseline_star_outline_24);

                if (m_enableIconTinting)
                    holder.markedView.setIconTint(article.marked ? m_cslTertiary : m_cslPrimary);
            }
        }

        private void updateTextImage(final Article article, final ArticleViewHolder holder) {
            if (holder.textImage != null) {
                updateTextCheckedState(article, holder);

                ViewCompat.setTransitionName(holder.textImage,
                        "gallery:" + article.flavorImageUri);
            }
        }

        private void updateSelectedView(final Article article, final ArticleViewHolder holder) {
            if (holder.selectionBoxView != null) {
                holder.selectionBoxView.setChecked(article.selected);
            }
        }

        private void updateScoreView(final Article article, final ArticleViewHolder holder) {
            if (holder.scoreView != null) {
                int scoreDrawable = R.drawable.baseline_trending_flat_24;

                if (article.score > 0)
                    scoreDrawable = R.drawable.baseline_trending_up_24;
                else if (article.score < 0)
                    scoreDrawable = R.drawable.baseline_trending_down_24;

                holder.scoreView.setIconResource(scoreDrawable);

                if (m_enableIconTinting) {
                    if (article.score > Article.SCORE_HIGH)
                        holder.scoreView.setIconTint(m_cslTertiary);
                    else
                        holder.scoreView.setIconTint(m_cslPrimary);
                }
            }
        }

        private void updatePublishedView(final Article article, final ArticleViewHolder holder) {
            if (holder.publishedView != null) {
                // otherwise we just use tinting in actionbar
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !m_prefs.getBoolean("enable_icon_tinting", true)) {
                    holder.publishedView.setIconResource(article.published ? R.drawable.rss_box : R.drawable.rss);
                }

                if (m_enableIconTinting)
                    holder.publishedView.setIconTint(article.published ? m_cslTertiary : m_cslPrimary);
            }
        }

        private void loadFlavorImage(final Article article, final ArticleViewHolder holder, final int maxImageHeight) {
            Glide.with(HeadlinesFragment.this)
                    .load(article.flavorImageUri)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .override(m_screenWidth, maxImageHeight)
                    .diskCacheStrategy(DiskCacheStrategy.DATA)
                    .skipMemoryCache(false)
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                            holder.flavorImageHolder.setVisibility(View.GONE);

                            holder.flavorImageView.setVisibility(View.GONE);
                            holder.flavorImageOverflow.setVisibility(View.VISIBLE);

                            return false;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                            holder.flavorImageHolder.setVisibility(View.VISIBLE);

                            holder.flavorImageView.setVisibility(View.VISIBLE);
                            holder.flavorImageOverflow.setVisibility(View.VISIBLE);

                            adjustVideoKindView(holder, article);

                            return false;
                        }
                    })
                    .into(new DrawableImageViewTarget(holder.flavorImageView));
        }

        private void checkImageAndLoad(final Article article, final ArticleViewHolder holder, final int maxImageHeight) {
            FlavorProgressTarget<Size> flavorProgressTarget = new FlavorProgressTarget<>(new SimpleTarget<Size>() {
                @Override
                public void onResourceReady(@NonNull Size resource, @Nullable com.bumptech.glide.request.transition.Transition<? super Size> transition) {

                    if (BuildConfig.DEBUG)
                        Log.d(TAG, "got resource of " + resource.getWidth() + "x" + resource.getHeight());

                    m_headlinesFragmentModel.getFlavorImageSizes().put(article.flavorImageUri, resource);

                    if (resource.getWidth() > FLAVOR_IMG_MIN_SIZE && resource.getHeight() > FLAVOR_IMG_MIN_SIZE) {

                        // now we can actually load the image into our drawable
                        loadFlavorImage(article, holder, maxImageHeight);

                    } else {
                        holder.flavorImageHolder.setVisibility(View.GONE);

                        holder.flavorImageView.setVisibility(View.VISIBLE);
                        holder.flavorImageOverflow.setVisibility(View.VISIBLE);
                    }
                }
            }, article.flavorImageUri, holder);

            Glide.with(HeadlinesFragment.this)
                    .as(Size.class)
                    .load(article.flavorImageUri)
                    .diskCacheStrategy(DiskCacheStrategy.DATA)
                    .skipMemoryCache(true)
                    .into(flavorProgressTarget);
        }

        @Override
        public int getItemViewType(int position) {
            Article a = getItem(position);

            if (a.id == Article.TYPE_AMR_FOOTER) {
                return VIEW_AMR_FOOTER;
            } else {
                return VIEW_NORMAL;
            }
        }

        private void updateTextCheckedState(final Article article, final ArticleViewHolder holder) {
            String tmp = !article.title.isEmpty() ? article.title.substring(0, 1).toUpperCase() : "?";

            if (article.selected) {
                holder.textImage.setImageDrawable(m_drawableBuilder.build(" ", 0xff616161));
                holder.textChecked.setVisibility(View.VISIBLE);
            } else {
                final Drawable textDrawable = m_drawableBuilder.build(tmp, m_colorGenerator.getColor(article.title));

                holder.textImage.setImageDrawable(textDrawable);

                if (!canShowFlavorImage() || article.flavorImage == null) {
                    holder.textImage.setImageDrawable(textDrawable);
                } else {
                    Glide.with(HeadlinesFragment.this)
                            .load(article.flavorImageUri)
                            .transition(DrawableTransitionOptions.withCrossFade())
                            .placeholder(textDrawable)
                            .thumbnail(0.5f)
                            .apply(RequestOptions.circleCropTransform())
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .skipMemoryCache(false)
                            .into(holder.textImage);
                }

                holder.textChecked.setVisibility(View.GONE);
            }
        }

        private void openGalleryForType(final Article article, final ArticleViewHolder holder, final View transitionView) {
            //Log.d(TAG, "openGalleryForType: " + article + " " + holder + " " + transitionView);

            if (article.flavorImage != null) {
                if ("iframe".equalsIgnoreCase(article.flavorImage.tagName())) {
                    m_activity.openUri(Uri.parse(article.flavorStreamUri));
                } else {

                    Intent intent = new Intent(m_activity, GalleryActivity.class);

                    intent.putExtra("firstSrc", article.flavorStreamUri != null ? article.flavorStreamUri : article.flavorImageUri);
                    intent.putExtra("title", article.title);

                    // FIXME maybe: gallery view works with document as html, it's easier to add this hack rather than
                    // rework it to additionally operate on separate attachment array (?)
                    // also, maybe consider video attachments? kinda hard to do without a poster tho (for flavor view)

                    String tempContent = article.content;

                    if (article.attachments != null) {
                        Document doc = new Document("");

                        for (Attachment a : article.attachments) {
                            if (a.content_type != null) {
                                if (a.content_type.contains("image/")) {
                                    Element img = new Element("img").attr("src", a.content_url);
                                    doc.appendChild(img);
                                }
                            }
                        }

                        tempContent = doc.outerHtml() + tempContent;
                    }

                    intent.putExtra("content", tempContent);

					/* ActivityOptionsCompat options =
							ActivityOptionsCompat.makeSceneTransitionAnimation(m_activity,
									transitionView != null ? transitionView : holder.flavorImageView,
									"gallery:" + (article.flavorStreamUri != null ? article.flavorStreamUri : article.flavorImageUri));

					ActivityCompat.startActivity(m_activity, intent, options.toBundle()); */

                    startActivity(intent);
                }
            }
        }

        private void adjustVideoKindView(final ArticleViewHolder holder, final Article article) {
            if (article.flavorImage != null) {
                if (article.flavor_kind == Article.FLAVOR_KIND_YOUTUBE || "iframe".equalsIgnoreCase(article.flavorImage.tagName())) {
                    holder.flavorVideoKindView.setImageResource(R.drawable.baseline_play_circle_outline_24);
                    holder.flavorVideoKindView.setVisibility(View.VISIBLE);
                } else if (article.flavor_kind == Article.FLAVOR_KIND_VIDEO || "video".equalsIgnoreCase(article.flavorImage.tagName())) {
                    holder.flavorVideoKindView.setImageResource(R.drawable.baseline_play_circle_24);
                    holder.flavorVideoKindView.setVisibility(View.VISIBLE);
                } else {
                    holder.flavorVideoKindView.setVisibility(View.INVISIBLE);
                }
            } else {
                holder.flavorVideoKindView.setVisibility(View.INVISIBLE);
            }
        }
    }

    private void releaseSurface() {
        try {
            if (m_mediaPlayer != null) {
                m_mediaPlayer.release();
            }
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }

        try {
            if (m_activeTexture != null) {
                m_activeTexture.setVisibility(View.GONE);
            }
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
    }

    public void scrollToArticle(Article article) {
        int position = Application.getArticles().indexOf(article);

        if (position != -1)
            m_list.scrollToPosition(position);
    }


    public Feed getFeed() {
        return m_feed;
    }

    @Override
    public void onPause() {
        super.onPause();

        releaseSurface();
    }

    private void syncToSharedArticles() {
        List<Article> tmp = new ArrayList<>(Application.getArticles());

        if (m_prefs.getBoolean("headlines_mark_read_scroll", false))
            tmp.add(new Article(Article.TYPE_AMR_FOOTER));

        m_adapter.submitList(tmp);
    }

    /**
     * Marks articles as read based on current scroll position
     */
    private void markVisibleArticlesAsRead() {
        if (!m_prefs.getBoolean("headlines_mark_read_scroll", false)) {
            return;
        }

        int firstVisibleItem = m_layoutManager.findFirstVisibleItemPosition();

        for (int i = 0; i < firstVisibleItem; i++) {
            try {
                Article article = Application.getArticles().get(i);

                if (article.unread && !m_readArticles.contains(article))
                    m_readArticles.add(new Article(article));

            } catch (IndexOutOfBoundsException e) {
                e.printStackTrace();
            }
        }

        // Mark articles as read if list is not loading
        if (!m_readArticles.isEmpty()) {
            ArticleModel model = Application.getArticlesModel();
            if (!m_isLazyLoading && !model.isLoading()) {
                Log.d(TAG, "marking articles as read, count=" + m_readArticles.size());
                m_activity.setArticlesUnread(new ArrayList<>(m_readArticles), Article.UPDATE_SET_FALSE);
                m_readArticles.clear();
                new Handler().postDelayed(() -> m_activity.refresh(false), 100);
            }
        }
    }

    /**
     * Scrolls the headlines list down by one page height
     * @return true if scrolling was performed or refresh triggered
     */
    public boolean scrollDown() {
        if (m_list == null) return false;

        // Check if we can scroll down
        if (!m_list.canScrollVertically(1)) {
            // Already at bottom, trigger refresh instead
            m_activity.refresh(false);
            return true;
        }

        // Scroll down by one page (use view height as page size)
        int pageHeight = m_list.getHeight();
        m_list.scrollBy(0, pageHeight);

        // Mark visible articles as read
        markVisibleArticlesAsRead();

        return true;
    }

    /**
     * Scrolls the headlines list up by one page height
     * @return true if scrolling was performed, false if already at top
     */
    public boolean scrollUp() {
        if (m_list == null) return false;

        // Check if we can scroll up
        if (!m_list.canScrollVertically(-1)) {
            return false; // Already at top
        }

        // Scroll up by one page (use view height as page size)
        int pageHeight = m_list.getHeight();
        m_list.scrollBy(0, -pageHeight);

        // Mark visible articles as read
        markVisibleArticlesAsRead();

        return true;
    }

}
