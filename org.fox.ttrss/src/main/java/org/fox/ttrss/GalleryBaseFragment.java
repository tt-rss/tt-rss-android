package org.fox.ttrss;

import android.content.Context;
import android.view.ContextMenu;
import android.view.View;

public class GalleryBaseFragment extends androidx.fragment.app.Fragment {
    private static final String TAG = GalleryBaseFragment.class.getSimpleName();
    protected GalleryActivity m_activity;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        //m_prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
        m_activity = (GalleryActivity) context;

    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
                                    ContextMenu.ContextMenuInfo menuInfo) {

        m_activity.getMenuInflater().inflate(R.menu.content_gallery_entry, menu);

        super.onCreateContextMenu(menu, v, menuInfo);
    }
}
