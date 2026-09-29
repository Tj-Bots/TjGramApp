package org.telegram.messenger;

import android.app.Activity;
import android.view.ViewGroup;

import org.telegram.messenger.regular.BuildConfig;
import org.telegram.ui.Components.TjUpdateLayout;
import org.telegram.ui.IUpdateLayout;

public class ApplicationLoaderImpl extends ApplicationLoader {
    @Override
    protected String onGetApplicationId() {
        return BuildConfig.APPLICATION_ID;
    }

    @Override
    public IUpdateLayout takeUpdateLayout(Activity activity, ViewGroup sideMenuContainer) {
        return new TjUpdateLayout(activity, sideMenuContainer);
    }
}
