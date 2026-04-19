package org.fox.ttrss;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import com.telefonica.nestedscrollwebview.NestedScrollWebView;

// NestedScrollWebView runs its own scrollBy in onNestedTouchEvent, which fights
// WebView's native pinch-pan when the page is zoomed. This subclass skips that
// nested path during pinch or while zoomed so pan works, then resumes nested
// scrolling at base scale so the AppBarLayout still collapses on scroll.
public class ZoomableNestedScrollWebView extends NestedScrollWebView {
    private float m_scale = 1f;
    private float m_baseScale = 0f;
    private boolean m_skipping = false;

    public ZoomableNestedScrollWebView(Context context) {
        super(context);
        setBlockNestedScrollingOnInternalContentScrollsEnabled(false);
    }

    public ZoomableNestedScrollWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setBlockNestedScrollingOnInternalContentScrollsEnabled(false);
    }

    public ZoomableNestedScrollWebView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setBlockNestedScrollingOnInternalContentScrollsEnabled(false);
    }

    public void setCurrentScale(float scale) {
        if (m_baseScale == 0f || scale < m_baseScale) {
            m_baseScale = scale;
        }
        m_scale = scale;
    }

    public void resetBaseScale() {
        m_baseScale = 0f;
        m_scale = 1f;
    }

    @Override
    public void onNestedTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            m_skipping = false;
        }
        float threshold = (m_baseScale > 0f ? m_baseScale : 1f) * 1.05f;
        boolean shouldSkip = event.getPointerCount() > 1 || m_scale > threshold;
        if (shouldSkip) {
            if (!m_skipping) {
                m_skipping = true;
                MotionEvent cancel = MotionEvent.obtain(event);
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                super.onNestedTouchEvent(cancel);
                cancel.recycle();
            }
            return;
        }
        super.onNestedTouchEvent(event);
    }
}
