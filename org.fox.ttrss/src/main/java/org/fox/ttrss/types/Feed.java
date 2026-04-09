package org.fox.ttrss.types;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import org.fox.ttrss.R;

import java.util.Objects;

public class Feed implements Comparable<Feed>, Parcelable {
    public static final int TYPE_SENTINEL = -10000;
    public static final int TYPE_GOBACK = -10001;
    public static final int TYPE_DIVIDER = -10002;
    public static final int TYPE_TOGGLE_UNREAD = -10003;
    public static final int TYPE_SETTINGS = -10004;

    public String feed_url;
    public String title;
    public int id;
    public int unread;
    public boolean has_icon;
    public int cat_id;
    public int last_updated;
    public int order_id;
    public String last_error;
    public boolean is_cat;
    public int update_interval;
    transient public boolean always_open_headlines;

    public Feed(int id) {
        this.id = id;
        this.title = "ID:" + id;
        this.is_cat = false;
        this.last_error = "";
    }

    public Feed(int id, String title, boolean is_cat) {
        this.id = id;
        this.title = title;
        this.is_cat = is_cat;
        this.last_error = "";
    }

    public Feed(Feed feed) {
        id = feed.id;
        feed_url = feed.feed_url;
        title = feed.title;
        unread = feed.unread;
        has_icon = feed.has_icon;
        cat_id = feed.cat_id;
        last_updated = feed.last_updated;
        order_id = feed.order_id;
        is_cat = feed.is_cat;
        always_open_headlines = feed.always_open_headlines;
        last_error = feed.last_error;
        update_interval = feed.update_interval;
    }

    public static final int MARKED = -1;
    public static final int PUBLISHED = -2;
    public static final int FRESH = -3;
    public static final int ALL_ARTICLES = -4;
    public static final int RECENTLY_READ = -6;
    public static final int ARCHIVED = 0;

    public static final int CAT_SPECIAL = -1;
    public static final int CAT_LABELS = -2;
    public static final int CAT_UNCATEGORIZED = 0;

    public static int getSpecialFeedTitleId(int feedId, boolean isCat) {
        if (!isCat)
            switch (feedId) {
                case MARKED:
                    return R.string.feed_starred_articles;
                case PUBLISHED:
                    return R.string.feed_published_articles;
                case FRESH:
                    return R.string.fresh_articles;
                case ALL_ARTICLES:
                    return R.string.feed_all_articles;
                case RECENTLY_READ:
                    return R.string.feed_recently_read;
                case ARCHIVED:
                    return R.string.feed_archived_articles;
                case TYPE_TOGGLE_UNREAD:
                    return R.string.unread_only;
                default:
                    throw new IllegalArgumentException("Invalid special feed id: " + feedId);
            }
        else
            switch (feedId) {
                case CAT_LABELS:
                    return R.string.cat_labels;
                case CAT_SPECIAL:
                    return R.string.cat_special;
                case CAT_UNCATEGORIZED:
                    return R.string.cat_uncategorized;
                default:
                    throw new IllegalArgumentException("Invalid special category id: " + feedId);
            }
    }

    public Feed(Parcel in) {
        readFromParcel(in);
    }

    public Feed() {

    }

    @Override
    public boolean equals(Object other) {
        if (other == this)
            return true;

        if (!(other instanceof Feed feed))
            return false;

        return feed.id == this.id && this.is_cat == feed.is_cat;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id, this.is_cat);
    }

    @NonNull
    @Override
    public String toString() {
        return "{id:" + this.id + ",is_cat:" + this.is_cat + "}";
    }

    @Override
    public int compareTo(Feed feed) {
        if (feed.unread != this.unread)
            return feed.unread - this.unread;
        else
            return this.title.compareTo(feed.title);
    }

    public void fixNullFields() {
        if (feed_url == null) feed_url = "";
        if (title == null) title = "";
        if (last_error == null) last_error = "";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(feed_url);
        out.writeString(title);
        out.writeInt(id);
        out.writeInt(unread);
        out.writeInt(has_icon ? 1 : 0);
        out.writeInt(cat_id);
        out.writeInt(last_updated);
        out.writeInt(is_cat ? 1 : 0);
        out.writeInt(order_id);
        out.writeInt(always_open_headlines ? 1 : 0);
        out.writeString(last_error);
        out.writeInt(update_interval);
    }

    public void readFromParcel(Parcel in) {
        feed_url = in.readString();
        title = in.readString();
        id = in.readInt();
        unread = in.readInt();
        has_icon = in.readInt() == 1;
        cat_id = in.readInt();
        last_updated = in.readInt();
        is_cat = in.readInt() == 1;
        order_id = in.readInt();
        always_open_headlines = in.readInt() == 1;
        last_error = in.readString();
        update_interval = in.readInt();
    }

    @SuppressWarnings("rawtypes")
    public static final Parcelable.Creator CREATOR =
            new Parcelable.Creator() {
                @Override
                public Feed createFromParcel(Parcel in) {
                    return new Feed(in);
                }

                @Override
                public Feed[] newArray(int size) {
                    return new Feed[size];
                }
            };
}