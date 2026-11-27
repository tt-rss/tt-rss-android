package org.fox.ttrss.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.fox.ttrss.types.Article;
import org.junit.Test;

public class ArticleDiffItemCallbackTest {
    private final ArticleDiffItemCallback m_callback = new ArticleDiffItemCallback();

    @Test
    public void addingFeedTitleTriggersFullBind() {
        Article oldArticle = new Article(1);
        Article newArticle = new Article(oldArticle);
        newArticle.feed_title = "Example Feed";

        assertTrue(m_callback.areItemsTheSame(oldArticle, newArticle));
        assertFalse(m_callback.areContentsTheSame(oldArticle, newArticle));
        assertNull(m_callback.getChangePayload(oldArticle, newArticle));
    }

    @Test
    public void removingFeedTitleTriggersFullBind() {
        Article oldArticle = new Article(1);
        oldArticle.feed_title = "Example Feed";
        Article newArticle = new Article(oldArticle);
        newArticle.feed_title = null;

        assertFalse(m_callback.areContentsTheSame(oldArticle, newArticle));
        assertNull(m_callback.getChangePayload(oldArticle, newArticle));
    }

    @Test
    public void renamingFeedWithUnreadChangeTriggersFullBind() {
        Article oldArticle = new Article(1);
        oldArticle.feed_title = "Old Feed";
        Article newArticle = new Article(oldArticle);
        newArticle.feed_title = "Renamed Feed";
        newArticle.unread = !oldArticle.unread;

        assertFalse(m_callback.areContentsTheSame(oldArticle, newArticle));
        assertNull(m_callback.getChangePayload(oldArticle, newArticle));
    }

    @Test
    public void unchangedFeedTitlePreservesUnreadPayload() {
        Article oldArticle = new Article(1);
        oldArticle.feed_title = "Example Feed";
        Article newArticle = new Article(oldArticle);

        assertTrue(m_callback.areContentsTheSame(oldArticle, newArticle));
        newArticle.unread = !oldArticle.unread;
        assertSame(ArticleDiffItemCallback.ChangePayload.UNREAD,
                m_callback.getChangePayload(oldArticle, newArticle));
    }
}
