package org.fox.ttrss;

import android.content.res.Configuration;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.MediaController;

import androidx.core.view.ViewCompat;

import java.io.IOException;

public class GalleryVideoFragment extends GalleryBaseFragment {
    private static final String TAG = GalleryVideoFragment.class.getSimpleName();

    String m_url;
    String m_coverUrl;
    MediaPlayer m_mediaPlayer;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            m_url = savedInstanceState.getString("m_url");
            m_coverUrl = savedInstanceState.getString("m_coverUrl");
        }
    }

    @Override
    public void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);

        out.putString("m_url", m_url);
        out.putString("m_coverUrl", m_coverUrl);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_gallery_entry, container, false);

        Log.d(TAG, "called for URL: " + m_url + " Cover: " + m_coverUrl);

        ImageView imgView = view.findViewById(R.id.flavor_image);

        ViewCompat.setTransitionName(imgView, "gallery:" + m_url);

        registerForContextMenu(imgView);

        // ActivityCompat.startPostponedEnterTransition(m_activity);

        view.findViewById(R.id.flavor_image_progress).setVisibility(View.VISIBLE);

        initializeVideoPlayer(view);

        return view;
    }

    private void initializeVideoPlayer(final View view) {
        final MediaController mediaController = new MediaController(m_activity);
        final TextureView textureView = view.findViewById(R.id.flavor_video);

        registerForContextMenu(textureView);

        textureView.setOnClickListener(v -> {
            try {

                if (mediaController.isShowing())
                    mediaController.hide();
                else
                    mediaController.show();

            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        mediaController.setAnchorView(textureView);

        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                Surface s = new Surface(surface);

                m_mediaPlayer = new MediaPlayer();

                mediaController.setMediaPlayer(new MediaController.MediaPlayerControl() {
                    @Override
                    public void start() {
                        m_mediaPlayer.start();
                    }

                    @Override
                    public void pause() {
                        m_mediaPlayer.pause();
                    }

                    @Override
                    public int getDuration() {
                        return m_mediaPlayer.getDuration();
                    }

                    @Override
                    public int getCurrentPosition() {
                        return m_mediaPlayer.getCurrentPosition();
                    }

                    @Override
                    public void seekTo(int pos) {
                        m_mediaPlayer.seekTo(pos);
                    }

                    @Override
                    public boolean isPlaying() {
                        return m_mediaPlayer.isPlaying();
                    }

                    @Override
                    public int getBufferPercentage() {
                        return 0;
                    }

                    @Override
                    public boolean canPause() {
                        return true;
                    }

                    @Override
                    public boolean canSeekBackward() {
                        return true;
                    }

                    @Override
                    public boolean canSeekForward() {
                        return true;
                    }

                    @Override
                    public int getAudioSessionId() {
                        return 0;
                    }
                });

                m_mediaPlayer.setSurface(s);

                try {
                    m_mediaPlayer.setDataSource(m_url);
                } catch (IOException e) {
                    view.findViewById(R.id.flavor_image_error).setVisibility(View.VISIBLE);
                    e.printStackTrace();
                }

                m_mediaPlayer.setOnPreparedListener(mp -> {
                    view.findViewById(R.id.flavor_image_progress).setVisibility(View.GONE);

                    try {
                        resizeSurface(textureView);
                        mp.setLooping(true);

                        mp.start();
                    } catch (IllegalStateException e) {
                        e.printStackTrace();
                    }
                });

                m_mediaPlayer.prepareAsync();
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
        });

    }

    public void initialize(String url, String coverUrl) {
        m_url = url;
        m_coverUrl = coverUrl;
    }

    protected void resizeSurface(View surfaceView) {
        // get the dimensions of the video (only valid when surfaceView is set)
        float videoWidth = m_mediaPlayer.getVideoWidth();
        float videoHeight = m_mediaPlayer.getVideoHeight();

        Rect rectangle = new Rect();
        m_activity.getWindow().getDecorView().getWindowVisibleDisplayFrame(rectangle);

        int actionBarHeight = m_activity.isPortrait() ? m_activity.getSupportActionBar().getHeight() : 0;

        Display display = m_activity.getWindowManager().getDefaultDisplay();
        float containerWidth = display.getWidth();
        float containerHeight = display.getHeight() - rectangle.top - actionBarHeight;

        // set dimensions to surfaceView's layout params (maintaining aspect ratio)
        android.view.ViewGroup.LayoutParams lp = surfaceView.getLayoutParams();
        lp.width = (int) containerWidth;
        lp.height = (int) ((videoHeight / videoWidth) * containerWidth);
        if (lp.height > containerHeight) {
            lp.width = (int) ((videoWidth / videoHeight) * containerHeight);
            lp.height = (int) containerHeight;
        }
        surfaceView.setLayoutParams(lp);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        try {
            resizeSurface(getView().findViewById(R.id.flavor_video));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
