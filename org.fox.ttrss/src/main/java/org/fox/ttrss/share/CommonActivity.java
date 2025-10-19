package org.fox.ttrss.share;

import android.app.Activity;
import android.util.Log;
import android.view.Display;
import android.widget.Toast;

public class CommonActivity extends Activity {
    private static final String TAG = CommonActivity.class.getSimpleName();

    private boolean m_smallScreenMode = true;

    protected void setSmallScreen(boolean smallScreen) {
        Log.d(TAG, "m_smallScreenMode=" + smallScreen);
        m_smallScreenMode = smallScreen;
    }

    public void toast(int msgId) {
        Toast toast = Toast.makeText(CommonActivity.this, msgId, Toast.LENGTH_SHORT);
        toast.show();
    }

    public void toast(String msg) {
        Toast toast = Toast.makeText(CommonActivity.this, msg, Toast.LENGTH_SHORT);
        toast.show();
    }

    public boolean isSmallScreen() {
        return m_smallScreenMode;
    }

    public boolean isPortrait() {
        Display display = getWindowManager().getDefaultDisplay();

        int width = display.getWidth();
        int height = display.getHeight();

        return width < height;
    }


}
