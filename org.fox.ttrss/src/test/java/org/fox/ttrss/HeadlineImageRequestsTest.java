package org.fox.ttrss;

import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import android.util.Size;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.bumptech.glide.RequestManager;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;

import org.junit.Test;

public class HeadlineImageRequestsTest {
    private HeadlinesFragment.ArticleViewHolder newHolder() {
        View row = mock(View.class);
        ImageView image = mock(ImageView.class);
        ImageView thumbnail = mock(ImageView.class);
        ViewGroup imageHolder = mock(ViewGroup.class);
        ProgressBar progress = mock(ProgressBar.class);
        when(row.findViewById(R.id.flavor_image)).thenReturn(image);
        when(row.findViewById(R.id.text_image)).thenReturn(thumbnail);
        when(row.findViewById(R.id.flavor_image_holder)).thenReturn(imageHolder);
        when(row.findViewById(R.id.flavor_image_progressbar)).thenReturn(progress);
        return new HeadlinesFragment.ArticleViewHolder(row);
    }

    private HeadlinesFragment.FlavorProgressTarget<Size> newTarget(
            HeadlinesFragment.ArticleViewHolder holder) {
        return new HeadlinesFragment.FlavorProgressTarget<>(new SimpleTarget<Size>() {
            @Override
            public void onResourceReady(Size resource, Transition<? super Size> transition) {
                // These tests exercise progress callbacks and cancellation.
            }
        }, "https://example.com/article-image.jpg", holder);
    }

    @Test
    public void clearsSizeRequestAndBothImageViewsBeforeReuse() {
        HeadlinesFragment.ArticleViewHolder holder = newHolder();
        HeadlinesFragment.FlavorProgressTarget<Size> target = newTarget(holder);
        holder.flavorSizeTarget = target;
        RequestManager requests = mock(RequestManager.class);

        // Glide may invoke cleanup synchronously inside clear(). It must not
        // make the previous article's image container visible again.
        doAnswer(invocation -> {
            target.onLoadCleared(null);
            return null;
        }).when(requests).clear(target);

        holder.clearImages(requests);

        verify(requests).clear(target);
        verify(requests).clear(holder.flavorImageView);
        verify(requests).clear(holder.textImage);
        assertNull(holder.flavorSizeTarget);
        verifyNoInteractions(holder.flavorImageHolder, holder.flavorImageLoadingBar);
    }

    @Test
    public void lateProgressCannotChangeReusedRow() {
        HeadlinesFragment.ArticleViewHolder holder = newHolder();
        HeadlinesFragment.FlavorProgressTarget<Size> oldTarget = newTarget(holder);
        holder.clearImages(mock(RequestManager.class));

        oldTarget.onConnecting();
        oldTarget.onDownloading(50, 100);
        oldTarget.onDownloaded();
        oldTarget.onDelivered();

        verifyNoInteractions(holder.flavorImageHolder, holder.flavorImageLoadingBar);

        // Requests for the new binding must still be able to show progress.
        newTarget(holder).onConnecting();
        verify(holder.flavorImageHolder).setVisibility(View.VISIBLE);
        verify(holder.flavorImageLoadingBar).setVisibility(View.VISIBLE);
    }
}
