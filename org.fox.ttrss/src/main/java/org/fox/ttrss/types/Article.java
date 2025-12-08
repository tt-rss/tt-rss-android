package org.fox.ttrss.types;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jspecify.annotations.NonNull;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// TODO: serialize Labels
public class Article {
    public static final int TYPE_AMR_FOOTER = -2;

    public static final int FLAVOR_KIND_ALBUM = 1;
    public static final int FLAVOR_KIND_VIDEO = 2;
    public static final int FLAVOR_KIND_YOUTUBE = 3;

    public static final int SCORE_LOW = -500;
    public static final int SCORE_HIGH = 500;

    public static final int UPDATE_FIELD_MARKED = 0;
    public static final int UPDATE_FIELD_PUBLISHED = 1;
    public static final int UPDATE_FIELD_UNREAD = 2;
    public static final int UPDATE_FIELD_NOTE = 3;
    public static final int UPDATE_FIELD_SCORE = 4;

    public static final int UPDATE_SET_FALSE = 0;
    public static final int UPDATE_SET_TRUE = 1;
    public static final int UPDATE_TOGGLE = 2;

    public int id;
    public boolean unread;
    public boolean marked;
    public boolean published;
    public int score;
    public int updated;
    public boolean is_updated;
    public String title;
    public String link;
    public int feed_id;
    public List<String> tags;
    public List<Attachment> attachments;
    public String content;
    public String excerpt;
    public List<List<String>> labels;
    public String feed_title;
    public int comments_count;
    public String comments_link;
    public boolean always_display_attachments;
    public String author;
    public String note;
    public boolean selected;
    public boolean active;
    public String flavor_image;
    public String flavor_stream;
    public int flavor_kind;
    public String site_url;

    /* not serialized */
    transient public Document articleDoc;
    transient public Element flavorImage;

    transient public String flavorImageUri;
    transient public String flavorStreamUri;
    transient public String youtubeVid;
    transient public List<Element> mediaList = new ArrayList<>();

    public Article() {

    }

    public void cleanupExcerpt() {
        if (excerpt != null) {
            excerpt = excerpt.replace("&hellip;", "…");
            excerpt = excerpt.replace("]]>", "");
            excerpt = Jsoup.parse(excerpt).text();
        }
    }

    public void collectMediaInfo() {
        if (flavor_image != null && !flavor_image.isEmpty()) {
            flavorImageUri = flavor_image;

            flavorImage = new Element("img")
                    .attr("src", flavorImageUri);

            if (flavor_stream != null && !flavor_stream.isEmpty()) {
                flavorStreamUri = flavor_stream;
            }

            return;
        }

        articleDoc = Jsoup.parse(content);

        if (articleDoc != null) {
            mediaList = articleDoc.select("img,video,iframe[src*=youtube.com/embed/]");

            for (Element e : mediaList) {
                if ("iframe".equalsIgnoreCase(e.tagName())) {
                    flavorImage = e;
                    break;
                } /*else if ("video".equalsIgnoreCase(e.tagName())) {
					flavorImage = e;
					break;
				}*/
            }

            if (flavorImage == null) {
                for (Element e : mediaList) {
                    flavorImage = e;
                    break;
                }
            }

            if (flavorImage != null) {
                try {

                    if ("video".equalsIgnoreCase(flavorImage.tagName())) {
                        Element source = flavorImage.select("source").first();

                        if (source != null) {
                            flavorStreamUri = source.attr("src");
                            flavorImageUri = flavorImage.attr("poster");
                        }
                    } else if ("iframe".equalsIgnoreCase(flavorImage.tagName())) {

                        String srcEmbed = flavorImage.attr("src");

                        if (!srcEmbed.isEmpty()) {
                            Pattern pattern = Pattern.compile("/embed/([\\w-]+)");
                            Matcher matcher = pattern.matcher(srcEmbed);

                            if (matcher.find()) {
                                youtubeVid = matcher.group(1);

                                flavorImageUri = "https://img.youtube.com/vi/" + youtubeVid + "/hqdefault.jpg";
                                flavorStreamUri = "https://youtu.be/" + youtubeVid;
                            }
                        }
                    } else {
                        flavorImageUri = flavorImage.attr("src");

                        if (!flavorImageUri.isEmpty() && flavorImageUri.startsWith("//")) {
                            flavorImageUri = "https:" + flavorImageUri;
                        }

                        flavorStreamUri = null;
                    }
                } catch (Exception e) {
                    e.printStackTrace();

                    flavorImage = null;
                    flavorImageUri = null;
                    flavorStreamUri = null;
                }
            }
        }

        if (flavorImageUri == null || flavorImageUri.isEmpty()) {
            // consider attachments
            if (attachments != null) {
                for (Attachment a : attachments) {
                    if (a.content_type != null && a.content_type.contains("image/")) {
                        flavorImageUri = a.content_url;

                        if (flavorImageUri != null && flavorImageUri.startsWith("//")) {
                            flavorImageUri = "https:" + flavorImageUri;
                        }

                        // this is needed for the gallery view
                        flavorImage = new Element("img")
                                .attr("src", flavorImageUri);

                        break;
                    }
                }
            }
        }

        //Log.d("Article", "collectMediaInfo: " + flavorImage);
    }

    public Article(int id) {
        this.id = id;
        this.title = "ID:" + id;
        fixNullFields();
    }

    public Article(Article clone) {
        id = clone.id;
        unread = clone.unread;
        marked = clone.marked;
        published = clone.published;
        score = clone.score;
        updated = clone.updated;
        is_updated = clone.is_updated;
        title = clone.title;
        link = clone.link;
        feed_id = clone.feed_id;
        tags = clone.tags;
        attachments = clone.attachments;
        content = clone.content;
        excerpt = clone.excerpt;
        labels = clone.labels;
        feed_title = clone.feed_title;
        comments_count = clone.comments_count;
        comments_link = clone.comments_link;
        always_display_attachments = clone.always_display_attachments;
        author = clone.author;
        note = clone.note;
        selected = clone.selected;
        flavor_image = clone.flavor_image;
        flavor_stream = clone.flavor_stream;
        flavor_kind = clone.flavor_kind;
        site_url = clone.site_url;
        active = clone.active;

        articleDoc = clone.articleDoc;
        flavorImage = clone.flavorImage;

        flavorImageUri = clone.flavorImageUri;
        flavorStreamUri = clone.flavorStreamUri;
        youtubeVid = clone.youtubeVid;
        mediaList = new ArrayList<>(clone.mediaList);
    }

    /**
     * set fields which might be missing during JSON deserialization to sane values
     */
    public void fixNullFields() {
        if (note == null) note = "";
        if (link == null) link = "";
        if (tags == null) tags = new ArrayList<>();
        if (excerpt == null) excerpt = "";
        if (content == null) content = "";
        if (comments_link == null) comments_link = "";
    }

    /**
     * compares by id only, we need this to skip manual lookup by id
     */
    @Override
    public boolean equals(Object other) {
        if (other == null)
            return false;

        if (other == this)
            return true;

        if (this.getClass() != other.getClass())
            return false;

        Article article = (Article) other;

        return article.id == this.id;
    }

    @NonNull
    @Override
    public String toString() {
        return "{id:" + this.id + ",unread:" + this.unread +
                ",marked:" + this.marked + ",published:" + this.published + ",score:" + this.score +
                ",selected:" + this.selected + "}";
    }

    public boolean isHostDistinct() {
        try {
            String siteDomain = new URL(site_url).getHost().replace("www.", "");
            String linkDomain = new URL(link).getHost().replace("www.", "");

            return !linkDomain.contains(siteDomain);

        } catch (MalformedURLException e) {
            //
        }

        return false;
    }

    @NonNull
    public String getLinkHost() {
        try {
            return new URL(link).getHost();
        } catch (MalformedURLException e) {
            //
        }

        return "";
    }
}
