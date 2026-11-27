package org.fox.ttrss.util;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;

import org.fox.ttrss.types.Article;

import java.util.Objects;

public class ArticleDiffItemCallback extends DiffUtil.ItemCallback<Article> {
    private static final String TAG = ArticleDiffItemCallback.class.getSimpleName();

    public enum ChangePayload {UNREAD, MARKED, SELECTED, PUBLISHED, NOTE, ACTIVE, SCORE, FEED_TITLE}

    ;

    @Override
    public boolean areItemsTheSame(@NonNull Article oldItem, @NonNull Article newItem) {
        return oldItem.id == newItem.id;
    }

    @Override
    public Object getChangePayload(@NonNull Article oldItem, @NonNull Article newItem) {

        if (!Objects.equals(oldItem.feed_title, newItem.feed_title))
            return null;

        if (oldItem.unread != newItem.unread)
            return ChangePayload.UNREAD;
        else if (oldItem.marked != newItem.marked)
            return ChangePayload.MARKED;
        else if (oldItem.selected != newItem.selected)
            return ChangePayload.SELECTED;
        else if (oldItem.published != newItem.published)
            return ChangePayload.PUBLISHED;
        else if (!oldItem.note.equals(newItem.note))
            return ChangePayload.NOTE;
        else if (oldItem.score != newItem.score)
            return ChangePayload.SCORE;
        else if (oldItem.active != newItem.active)
            return ChangePayload.ACTIVE;

        return null;
    }

    @Override
    public boolean areContentsTheSame(@NonNull Article oldItem, @NonNull Article newItem) {
        return oldItem.id == newItem.id && oldItem.unread == newItem.unread && oldItem.marked == newItem.marked
                && oldItem.selected == newItem.selected && oldItem.published == newItem.published
                && oldItem.score == newItem.score && oldItem.note.equals(newItem.note) && oldItem.active == newItem.active
                && Objects.equals(oldItem.feed_title, newItem.feed_title);
    }
}
