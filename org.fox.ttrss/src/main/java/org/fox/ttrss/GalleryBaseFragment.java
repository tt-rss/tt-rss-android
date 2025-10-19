package org.fox.ttrss;

import android.app.Activity;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.View;

public class GalleryBaseFragment extends androidx.fragment.app.Fragment {
    private static final String TAG = GalleryBaseFragment.class.getSimpleName();
    protected GalleryActivity m_activity;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRetainInstance(true);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        //m_prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
        m_activity = (GalleryActivity) activity;

    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
                                    ContextMenu.ContextMenuInfo menuInfo) {

        m_activity.getMenuInflater().inflate(R.menu.content_gallery_entry, menu);

        super.onCreateContextMenu(menu, v, menuInfo);
    }
}
