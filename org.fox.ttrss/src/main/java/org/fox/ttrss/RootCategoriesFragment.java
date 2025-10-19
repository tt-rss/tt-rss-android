package org.fox.ttrss;

import androidx.lifecycle.ViewModelProvider;

public class RootCategoriesFragment extends FeedsFragment {
    private static final String TAG = RootCategoriesFragment.class.getSimpleName();

    @Override
    protected FeedsModel getModel() {
        return new ViewModelProvider(this).get(RootCategoriesModel.class);
    }
}
