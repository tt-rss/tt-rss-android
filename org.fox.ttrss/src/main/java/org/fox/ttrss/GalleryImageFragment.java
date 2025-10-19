package org.fox.ttrss;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;

import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;

import com.bogdwellers.pinchtozoom.ImageMatrixTouchHandler;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.DrawableImageViewTarget;
import com.bumptech.glide.request.target.Target;

public class GalleryImageFragment extends GalleryBaseFragment {
    private static final String TAG = GalleryBaseFragment.class.getSimpleName();

    String m_url;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            m_url = savedInstanceState.getString("m_url");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_gallery_entry, container, false);

        Log.d(TAG, "called for URL: " + m_url);

        ImageView imgView = view.findViewById(R.id.flavor_image);

        // TODO: ImageMatrixTouchHandler doesn't like context menus
        ImageMatrixTouchHandler touchHandler = new PagerAwareImageMatrixTouchHandler(view.getContext());

        imgView.setOnTouchListener(touchHandler);

        // shared element transitions stop GIFs from playing
        if (!m_url.toLowerCase().contains(".gif")) {
            ViewCompat.setTransitionName(imgView, "gallery:" + m_url);
        }

        registerForContextMenu(imgView);

        final ProgressBar progressBar = view.findViewById(R.id.flavor_image_progress);
        final View errorMessage = view.findViewById(R.id.flavor_image_error);

        // final GlideDrawableImageViewTarget glideImage = new GlideDrawableImageViewTarget(imgView);

        Glide.with(this)
                .load(m_url)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .skipMemoryCache(false)
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                        progressBar.setVisibility(View.GONE);
                        errorMessage.setVisibility(View.VISIBLE);

                        // ActivityCompat.startPostponedEnterTransition(m_activity);

                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                        progressBar.setVisibility(View.GONE);
                        errorMessage.setVisibility(View.GONE);

                        // ActivityCompat.startPostponedEnterTransition(m_activity);

                        return false;
                    }
                })
                .into(new DrawableImageViewTarget(imgView));

        return view;
    }

    public void initialize(String url) {
        m_url = url;
    }

    @Override
    public void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);

        out.putString("m_url", m_url);
    }

    /*@Override
    public boolean onContextItemSelected(MenuItem item) {
        int position = m_pager.getCurrentItem();
        String url = m_checkedUrls.get(position);

        if (!onImageMenuItemSelected(item, url))
            return super.onContextItemSelected(item);
        else
            return true;
    }*/

}
