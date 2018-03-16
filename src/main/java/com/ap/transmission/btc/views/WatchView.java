package com.ap.transmission.btc.views;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.GridLayout;

import com.ap.transmission.btc.Prefs;

/**
 * @author Andrey Pavlenko
 */
public class WatchView extends GridLayout {

  public WatchView(Context context, AttributeSet attrs) {
    super(context, attrs);
    setColumnCount(1);
  }

  public void setPrefs(Prefs prefs) {
    int max = prefs.getMaxIndex(Prefs.K.WATCH_DIR);

    if (getChildCount() == max) {
      for (int i = 0; i < max; i++) {
        WatchItemView wi = (WatchItemView) getChildAt(i);
        wi.update(prefs);
      }
    } else {
      removeAllViews();

      for (int i = 0; i < max; i++) {
        WatchItemView wi = new WatchItemView(getContext(), null);
        wi.setIndex(i);
        addView(wi);
      }
    }
  }
}
