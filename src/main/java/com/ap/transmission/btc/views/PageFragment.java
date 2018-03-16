package com.ap.transmission.btc.views;

import android.databinding.DataBindingUtil;
import android.databinding.ViewDataBinding;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.ap.transmission.btc.activities.ActivityBase;
import com.ap.transmission.btc.BR;
import com.ap.transmission.btc.BindingHelper;
import com.ap.transmission.btc.Prefs;
import com.ap.transmission.btc.Utils;

/**
 * @author Andrey Pavlenko
 */
public class PageFragment extends Fragment {
  private static final String TAG = PageFragment.class.getName();
  private static final String ARG_LAYOUT_ID = "layoutId";
  private int layoutId;

  static PageFragment newInstance(int layoutId) {
    PageFragment pageFragment = new PageFragment();
    Bundle arguments = new Bundle();
    arguments.putInt(ARG_LAYOUT_ID, layoutId);
    pageFragment.setArguments(arguments);
    return pageFragment;
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Bundle args = getArguments();
    layoutId = (args == null) ? 0 : args.getInt(ARG_LAYOUT_ID);
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState) {
    if (container == null) {
      Log.w(TAG, "container is null");
      return null;
    }

    ViewDataBinding b = DataBindingUtil.inflate(getLayoutInflater(), layoutId, container, false);
    ActivityBase a = Utils.getActivity(container);

    if (a == null) {
      Log.w(TAG, "Activity not found");
      return null;
    }

    Prefs prefs = a.getPrefs();
    BindingHelper h = new BindingHelper(a, b);
    b.setVariable(BR.h, h);
    b.setVariable(BR.p, prefs);
    return b.getRoot();
  }

  public static class Adapter extends FragmentPagerAdapter {
    private final TabInfo[] tabs;

    public Adapter(FragmentManager fm, TabInfo[] tabs) {
      super(fm);
      this.tabs = tabs;
    }

    @Override
    public Fragment getItem(int position) {
      return PageFragment.newInstance(tabs[position].getLayout());
    }

    @Override
    public int getCount() {
      return tabs.length;
    }
  }
}
