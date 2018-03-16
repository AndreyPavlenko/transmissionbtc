package com.ap.transmission.btc.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ImageView;

import com.ap.transmission.btc.Prefs;
import com.ap.transmission.btc.R;
import com.ap.transmission.btc.Utils;

/**
 * @author Andrey Pavlenko
 */
public class WatchItemView extends GridLayout {
  private int index = -1;

  public WatchItemView(Context context, AttributeSet attrs) {
    super(context, attrs);
    setColumnCount(1);

    LayoutInflater i = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    if (i == null) throw new RuntimeException("Inflater is null");
    i.inflate(R.layout.watch_view, this, true);

    ViewGroup l = (ViewGroup) getChildAt(0);
    final BrowseView watch = (BrowseView) l.getChildAt(0);
    final BrowseView download = (BrowseView) l.getChildAt(1);
    final ImageView remove = watch.getLeftButton();
    remove.setVisibility(View.VISIBLE);
    remove.setImageDrawable(getResources().getDrawable(R.drawable.trash));
    remove.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        Prefs prefs = Utils.getActivity(v).getPrefs();
        watch.getPath().setOnFocusChangeListener(null);
        download.getPath().setOnFocusChangeListener(null);
        prefs.removeString(Prefs.K.WATCH_DIR, index);
        prefs.removeString(Prefs.K.DOWNLOAD_DIR, index);
      }
    });
  }

  public void setIndex(int index) {
    this.index = index;
    Prefs prefs = Utils.getActivity(this).getPrefs();
    ViewGroup l = (ViewGroup) getChildAt(0);
    BrowseView w = (BrowseView) l.getChildAt(0);
    BrowseView d = (BrowseView) l.getChildAt(1);
    w.setPath(prefs.getString(Prefs.K.WATCH_DIR, index, ""));
    w.setPref(Prefs.K.WATCH_DIR, index);
    d.setPath(prefs.getString(Prefs.K.DOWNLOAD_DIR, index, ""));
    d.setPref(Prefs.K.DOWNLOAD_DIR, index);
  }

  public void update(Prefs prefs) {
    ViewGroup l = (ViewGroup) getChildAt(0);
    BrowseView w = (BrowseView) l.getChildAt(0);
    BrowseView d = (BrowseView) l.getChildAt(1);
    w.setPath(prefs.getString(Prefs.K.WATCH_DIR, index, ""));
    d.setPath(prefs.getString(Prefs.K.DOWNLOAD_DIR, index, ""));
  }
}
