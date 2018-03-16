package com.ap.transmission.btc.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.GridLayout;
import android.widget.TextView;

import com.ap.transmission.btc.Adapters;
import com.ap.transmission.btc.Prefs;
import com.ap.transmission.btc.R;

/**
 * @author Andrey Pavlenko
 */
public class CheckBoxView extends GridLayout {
  private OnClickListener clickListener;

  public CheckBoxView(Context context, AttributeSet attrs) {
    super(context, attrs);
    setColumnCount(2);

    LayoutInflater i = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    if (i == null) throw new RuntimeException("Inflater is null");
    i.inflate(R.layout.checkbox_view, this, true);

    TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.CheckBoxView, 0, 0);
    String text = a.getString(R.styleable.CheckBoxView_text);
    a.recycle();
    getText().setText(text);
    setClickable(true);

    super.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        getCheckBox().performClick();
        if (clickListener != null) clickListener.onClick(getCheckBox());
      }
    });
  }

  public TextView getText() {
    return (TextView) getChildAt(0);
  }

  public CheckBox getCheckBox() {
    return (CheckBox) getChildAt(1);
  }

  public void setPref(Prefs.K pref) {
    Adapters.checkBoxPropAdapter(getCheckBox(), pref);
  }

  public void setOnClick(@Nullable OnClickListener l) {
    clickListener = l;
    if (l != null) l.onClick(getCheckBox());
  }
}
