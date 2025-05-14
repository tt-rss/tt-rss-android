package org.fox.ttrss;

import android.content.Intent;
import android.content.SharedPreferences;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.widget.SwitchCompat;

import java.net.MalformedURLException;
import java.net.URL;

public abstract class BaseFeedlistFragment extends androidx.fragment.app.Fragment {
    abstract public void refresh();

    public void initDrawerHeader(LayoutInflater inflater, View view, ListView list, final CommonActivity activity, final SharedPreferences prefs) {

        View layout = inflater.inflate(R.layout.drawer_header, list, false);
        list.addHeaderView(layout, null, false);

        TextView login = view.findViewById(R.id.drawer_header_login);
        TextView server = view.findViewById(R.id.drawer_header_server);

        login.setText(prefs.getString("login", ""));
        try {
            server.setText(new URL(prefs.getString("ttrss_url", "")).getHost());
        } catch (MalformedURLException e) {
            server.setText("");
        }

        View settings = view.findViewById(R.id.drawer_settings_btn);

        settings.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(getActivity(),
                        PreferencesActivity.class);

                startActivityForResult(intent, 0);

            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        /* deal with ~material~ footers */

        // divider
        final View footer = inflater.inflate(R.layout.drawer_divider, list, false);
        footer.setOnClickListener(v -> {
            //
        });
        list.addFooterView(footer);

        // unread only checkbox
        final View rowToggle = inflater.inflate(R.layout.feeds_row_toggle, list, false);
        list.addFooterView(rowToggle);
        TextView text = rowToggle.findViewById(R.id.title);
        text.setText(R.string.unread_only);

        ImageView icon = rowToggle.findViewById(R.id.icon);
        TypedValue tv = new TypedValue();
        getActivity().getTheme().resolveAttribute(R.attr.ic_filter_variant, tv, true);
        icon.setImageResource(tv.resourceId);

        final SwitchCompat rowSwitch = rowToggle.findViewById(R.id.row_switch);
        rowSwitch.setChecked(activity.getUnreadOnly());

        rowSwitch.setOnCheckedChangeListener((button, isChecked) -> {
            activity.setUnreadOnly(isChecked);
            refresh();
        });

        footer.setOnClickListener(v -> rowSwitch.setChecked(!rowSwitch.isChecked()));

    }

}
