package com.ap.transmission.btc;

import android.content.res.Resources;
import android.databinding.BindingAdapter;
import android.databinding.BindingConversion;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static com.ap.transmission.btc.Utils.getActivity;

/**
 * @author Andrey Pavlenko
 */
public class Adapters {

  @BindingAdapter("app:pref")
  public static void editTextPrefAdapter(final EditText view, final Prefs.K k) {
    editTextPrefAdapter(view, k, -1);
  }

  @BindingAdapter({"app:pref", "app:pref_index"})
  public static void editTextPrefAdapter(final EditText view, final Prefs.K k, final int index) {
    final Prefs p = getPrefs(view);
    Object value = p.get(k, index);
    String valueText = (value == null) ? "" : value.toString();
    String currentText = view.getText().toString();
    if (!currentText.equals(valueText)) view.setText(valueText);

    if (view.getTag(R.id.listener_tag) == null) {
      TextListener tl = new TextListener() {
        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
          p.set(k, s, index);
        }
      };
      OnFocusChangeListener fl = new OnFocusChangeListener() {
        @Override
        public void onFocusChange(View v, boolean hasFocus) {
          if (!hasFocus) {
            String current = view.getText().toString();

            if (current.isEmpty()) {
              final Object def = k.def(p, index);
              view.setText((def == null) ? "" : String.valueOf(def));
            }
          }
        }
      };

      final Object def = k.def(p, index);
      final String defText = (def == null) ? null : String.valueOf(def);
      if ((defText != null) && !defText.isEmpty()) view.setHint(defText);

      view.setTag(R.id.listener_tag, tl);
      view.addTextChangedListener(tl);
      view.setOnFocusChangeListener(fl);
    }
  }

  @BindingAdapter("app:pref")
  public static void checkBoxPropAdapter(final CheckBox view, final Prefs.K k) {
    final Prefs p = getPrefs(view);
    Boolean value = p.get(k);
    if (view.isChecked() != value) view.setChecked(value);

    if (view.getTag(R.id.listener_tag) == null) {
      CompoundButton.OnCheckedChangeListener l = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton c, boolean isChecked) {
          p.set(k, isChecked);
        }
      };
      view.setTag(R.id.listener_tag, l);
      view.setOnCheckedChangeListener(l);
    }
  }

  @BindingAdapter("app:pref")
  public static void spinnerPropAdapter(final Spinner s, final Prefs.K k) {
    Resources res = s.getContext().getResources();
    final Prefs p = getPrefs(s);
    Object current = p.get(k);
    Set all = EnumSet.allOf((Class) current.getClass());
    List<SpinnerItem> items = new ArrayList<>(all.size());
    int idx = 0;
    int i = 0;

    for (Object o : all) {
      String v = res.getText(((Localizable) o).getResourceId()).toString();
      if (o.equals(current)) idx = i;
      items.add(new SpinnerItem(o, v));
      i++;
    }

    ArrayAdapter<SpinnerItem> adapter = new ArrayAdapter<>(s.getContext(),
        android.R.layout.simple_spinner_item, items);
    s.setAdapter(adapter);
    if (idx != s.getSelectedItemPosition()) s.setSelection(idx);

    if (s.getTag(R.id.listener_tag) == null) {
      AdapterView.OnItemSelectedListener l = new AdapterView.OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
          p.set(k, ((SpinnerItem) s.getSelectedItem()).key);
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
        }
      };
      s.setTag(R.id.listener_tag, l);
      s.setOnItemSelectedListener(l);
    }
  }

  @BindingAdapter("app:html")
  public static void toHtml(TextView view, String html) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      view.setText(Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY));
    } else {
      view.setText(Html.fromHtml(html));
    }

    view.setClickable(true);
    view.setMovementMethod(LinkMovementMethod.getInstance());
  }

  @BindingAdapter("app:icon")
  public static void textIcon(TextView view, int res) {
    if (res < 0) return;

    Integer current = (Integer) view.getTag(R.id.icon_tag);
    if ((current != null) && (current == res)) return;

    Drawable d = view.getResources().getDrawable(res);
    int size = (int) (view.getTextSize() * view.getTextScaleX());
    d.setBounds(0, 0, size, size);
    view.setCompoundDrawables(d, null, null, null);
    view.setTag(R.id.icon_tag, res);
  }

  @BindingConversion
  public static int visibilityAdapter(boolean visible) {
    return visible ? View.VISIBLE : View.GONE;
  }

  private static Prefs getPrefs(View view) {
    return getActivity(view).getPrefs();
  }

  private static abstract class TextListener implements TextWatcher {

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void afterTextChanged(Editable s) {
    }
  }

  private static final class SpinnerItem {
    final Object key;
    final String value;

    SpinnerItem(Object key, String value) {
      this.key = key;
      this.value = value;
    }

    @Override
    public String toString() {
      return value;
    }
  }
}
