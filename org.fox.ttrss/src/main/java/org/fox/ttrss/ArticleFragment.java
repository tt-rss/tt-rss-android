package org.fox.ttrss;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebBackForwardList;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebView.HitTestResult;
import android.webkit.WebViewClient;
import android.widget.TextView;

import androidx.core.text.HtmlCompat;
import androidx.preference.PreferenceManager;

import org.fox.ttrss.types.Article;
import org.fox.ttrss.types.Attachment;
import org.jsoup.helper.StringUtil;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

// TODO: add ability to update already rendered contents from article somehow (to refresh note, etc)
public class ArticleFragment extends androidx.fragment.app.Fragment {
    private static final String TAG = ArticleFragment.class.getSimpleName();

    private SharedPreferences m_prefs;
    protected Article m_article;
    private DetailActivity m_activity;
    private WebView m_web;
    //protected View m_fab;
    protected int m_articleFontSize;
    protected int m_articleSmallFontSize;

    public void initialize(Article article) {
        m_article = article;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
                                    ContextMenuInfo menuInfo) {

        if (v.getId() == R.id.article_content) {
            HitTestResult result = ((WebView) v).getHitTestResult();

            if (result.getType() == HitTestResult.IMAGE_TYPE || result.getType() == HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {

                menu.setHeaderTitle(result.getExtra());
                getActivity().getMenuInflater().inflate(R.menu.content_gallery_entry, menu);

                /* FIXME I have no idea how to do this correctly ;( */

                m_activity.setLastContentImageHitTestUrl(result.getExtra());

            } else {
                menu.setHeaderTitle(m_article.title);
                getActivity().getMenuInflater().inflate(R.menu.context_article_link, menu);
            }
        } else {
            menu.setHeaderTitle(m_article.title);
            getActivity().getMenuInflater().inflate(R.menu.context_article_link, menu);
        }

        super.onCreateContextMenu(menu, v, menuInfo);

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            int articleId = savedInstanceState.getInt("m_articleId");

            m_article = Application.getArticlesModel().getById(articleId);
        }
    }

    @SuppressLint({"NewApi", "SimpleDateFormat"})
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, final Bundle savedInstanceState) {

        final View view = inflater.inflate(R.layout.fragment_article, container, false);

        // couldn't reinitialize state properly, might as well bail out
        if (m_article == null) {
            m_activity.finish();
        }

        m_articleFontSize = m_prefs.getInt("article_font_size_sp_int", 16);
        m_articleSmallFontSize = Math.max(10, Math.min(18, m_articleFontSize - 2));

        TextView title = view.findViewById(R.id.title);

        if (title != null) {

            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.min(21, m_articleFontSize + 3));

            String titleStr;

            if (m_article.title.length() > 200)
                titleStr = m_article.title.substring(0, 200) + "...";
            else
                titleStr = m_article.title;

            title.setText(HtmlCompat.fromHtml(titleStr, HtmlCompat.FROM_HTML_MODE_LEGACY));
            //title.setPaintFlags(title.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
            title.setOnClickListener(v -> {
                try {
                    m_activity.openUri(Uri.parse(m_article.link));
                } catch (Exception e) {
                    e.printStackTrace();
                    m_activity.toast(R.string.error_other_error);
                }
            });

        }

        TextView comments = view.findViewById(R.id.comments);

        if (comments != null) {
            if (m_activity.getApiLevel() >= 4 && m_article.comments_count > 0) {
                comments.setTextSize(TypedValue.COMPLEX_UNIT_SP, m_articleSmallFontSize);

                String commentsTitle = getResources().getQuantityString(R.plurals.article_comments, m_article.comments_count, m_article.comments_count);
                comments.setText(commentsTitle);

                //comments.setPaintFlags(title.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
                comments.setOnClickListener(v -> {
                    try {
                        String url = (m_article.comments_link != null && !m_article.comments_link.isEmpty()) ?
                                m_article.comments_link : m_article.link;

                        m_activity.openUri(Uri.parse(url));
                    } catch (Exception e) {
                        e.printStackTrace();
                        m_activity.toast(R.string.error_other_error);
                    }
                });

            } else {
                comments.setVisibility(View.GONE);
            }
        }

        TextView linkHost = view.findViewById(R.id.link_host);

        if (linkHost != null) {
            if (m_article.isHostDistinct()) {
                linkHost.setText(m_article.getLinkHost());
                linkHost.setVisibility(View.VISIBLE);
            } else {
                linkHost.setVisibility(View.GONE);
            }
        }

        TextView note = view.findViewById(R.id.note);
        View noteContainer = view.findViewById(R.id.note_container);

        if (note != null && noteContainer != null) {
            if (!m_article.note.isEmpty()) {
                note.setTextSize(TypedValue.COMPLEX_UNIT_SP, m_articleSmallFontSize);
                note.setText(m_article.note);
                noteContainer.setVisibility(View.VISIBLE);
            } else {
                noteContainer.setVisibility(View.GONE);
            }

            note.setOnClickListener(view1 -> m_activity.editArticleNote(m_article));
        }

        TextView dv = view.findViewById(R.id.date);

        if (dv != null) {
            dv.setTextSize(TypedValue.COMPLEX_UNIT_SP, m_articleSmallFontSize);

            Date d = new Date(m_article.updated * 1000L);
            long half_a_year_ago = System.currentTimeMillis() / 1000L - 182 * 24 * 60 * 60;
            DateFormat df;
            if (m_article.updated < half_a_year_ago)
                df = new SimpleDateFormat("MMM dd, yyyy");
            else
                df = new SimpleDateFormat("MMM dd, HH:mm");
            dv.setText(df.format(d));
        }

        TextView tagv = view.findViewById(R.id.tags);

        if (tagv != null) {
            tagv.setTextSize(TypedValue.COMPLEX_UNIT_SP, m_articleSmallFontSize);

            if (m_article.feed_title != null) {
                String fTitle = m_article.feed_title;

                if (m_article.author != null && !m_article.author.isEmpty()) {
                    fTitle += " (" + getString(R.string.author_formatted, m_article.author) + ")";
                }

                tagv.setText(fTitle);
            } else if (m_article.tags != null) {
                tagv.setText(StringUtil.join(m_article.tags, ", "));
            } else {
                tagv.setVisibility(View.GONE);
            }
        }

        m_web = view.findViewById(R.id.article_content);

        m_web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                try {
                    m_activity.openUri(Uri.parse(url));

                    return true;

                } catch (Exception e) {
                    e.printStackTrace();
                }

                return false;
            }
        });

        m_web.setOnLongClickListener(v -> {
            HitTestResult result = ((WebView) v).getHitTestResult();

            if (result.getType() == HitTestResult.IMAGE_TYPE || result.getType() == HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                registerForContextMenu(m_web);
                m_activity.openContextMenu(m_web);
                unregisterForContextMenu(m_web);
                return true;
            } else {
                return false;
            }
        });

        renderContent(savedInstanceState);

        return view;
    }

    protected void renderContent(Bundle savedInstanceState) {
        if (!isAdded() || m_web == null) return;

        Log.d(TAG, "renderContent: " + m_article.title);

        WebSettings ws = m_web.getSettings();
        ws.setSupportZoom(false);

        TypedValue tvTextColor = new TypedValue();
        getActivity().getTheme().resolveAttribute(R.attr.colorOnSurface, tvTextColor, true);

        String textColor = String.format("#%06X", (0xFFFFFF & tvTextColor.data));

        String cssOverride = "body { color : " + textColor + "; }";

        TypedValue tvColorPrimary = new TypedValue();
        getActivity().getTheme().resolveAttribute(R.attr.colorPrimary, tvColorPrimary, true);

        String linkHexColor = String.format("#%06X", (0xFFFFFF & tvColorPrimary.data));
        cssOverride += " a:link {color: " + linkHexColor + ";} a:visited { color: " + linkHexColor + ";}";

        String articleContent = m_article.content;

        ws.setJavaScriptEnabled(m_prefs.getBoolean("article_javascript_enabled", false));

        m_web.setBackgroundColor(Color.TRANSPARENT);

        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        ws.setMediaPlaybackRequiresUserGesture(true);

        if (m_prefs.getBoolean("justify_article_text", true)) {
            cssOverride += "body { text-align : justify; } ";
        }

        cssOverride += " blockquote { border-left: 5px solid gray; padding-left: 10px; margin-left: 0; margin-right: 0; }";

        ws.setDefaultFontSize(m_articleFontSize);

        int margin8dp = CommonActivity.dpToPx(getContext(), 8);

        StringBuilder content = new StringBuilder("<html>" +
                "<head>" +
                "<meta content=\"text/html; charset=utf-8\" http-equiv=\"content-type\">" +
                "<meta name=\"viewport\" content=\"width=device-width, user-scalable=no\" />" +
                "<style type=\"text/css\">" +
                "body { padding : 0px; margin : " + margin8dp + "px; line-height : 1.3; word-wrap: break-word; }" +
                "h1, h2, h3, h4, h5, h6 { line-height: 1; text-align: initial; }" +
                "img, video, iframe { max-width : 100%; width : auto; height : auto; }" +
                "img, video { object-fit : contain }" +
                " table { width : 100%; }" +
                cssOverride +
                "</style>" +
                "</head>" +
                "<body dir='auto'>");

        content.append(articleContent);

        if (m_article.attachments != null && !m_article.attachments.isEmpty()) {
            String flatContent = articleContent.replaceAll("[\r\n]", "");
            boolean hasImages = flatContent.matches(".*?<img[^>+].*?");

            for (Attachment a : m_article.attachments) {
                if (a.content_type != null && a.content_url != null) {
                    try {
                        if (a.content_type.contains("image") &&
                                (!hasImages || m_article.always_display_attachments)) {

                            URL url = new URL(a.content_url.trim());
                            String strUrl = url.toString().trim();

                            content.append("<p><img src=\"" + strUrl.replace("\"", "\\\"") + "\"></p>");
                        }

                    } catch (MalformedURLException e) {
                        //
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        content.append("</body></html>");

        try {
            String baseUrl = null;

            try {
                URL url = new URL(m_article.link);
                baseUrl = url.getProtocol() + "://" + url.getHost();
            } catch (MalformedURLException e) {
                //
            }

            if (savedInstanceState == null) {
                m_web.loadDataWithBaseURL(baseUrl, content.toString(), "text/html", "utf-8", null);
            } else {
                WebBackForwardList rc = m_web.restoreState(savedInstanceState);

                if (rc == null) {
                    // restore failed...
                    m_web.loadDataWithBaseURL(baseUrl, content.toString(), "text/html", "utf-8", null);
                }
            }

        } catch (RuntimeException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void onPause() {
        super.onPause();

        if (m_web != null) m_web.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (m_web != null) m_web.onResume();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        m_prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
        m_activity = (DetailActivity) activity;

    }

    @Override
    public void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);

        out.putInt("m_articleId", m_article.id);
    }
}
