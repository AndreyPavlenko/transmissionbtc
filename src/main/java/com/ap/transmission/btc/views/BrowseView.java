package com.ap.transmission.btc.views;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.TypedArray;
import android.support.annotation.RequiresApi;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.ap.transmission.btc.Adapters;
import com.ap.transmission.btc.Prefs;
import com.ap.transmission.btc.R;
import com.ap.transmission.btc.Utils;
import com.ap.transmission.btc.activities.ActivityBase;
import com.ap.transmission.btc.activities.ActivityResultHandler;
import com.ap.transmission.btc.activities.SelectFileActivity;

import java.io.File;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.LOLLIPOP;
import static android.os.Build.VERSION_CODES.N;
import static com.ap.transmission.btc.Utils.getRealDirPath;

/**
 * @author Andrey Pavlenko
 */
public class BrowseView extends RelativeLayout implements ActivityResultHandler {
  private static final int REQ_FILE = 10;
  private static byte isBrowseSupported;
  private boolean selectDir;
  private boolean selectFile;
  private boolean checkWritable;
  private Prefs.K pref;
  private int prefIndex = -1;

  public BrowseView(Context context, AttributeSet attrs) {
    super(context, attrs);

    TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.BrowseView, 0, 0);
    String titleAttr = a.getString(R.styleable.BrowseView_title);
    String pathAttr = a.getString(R.styleable.BrowseView_path);
    boolean editable = a.getBoolean(R.styleable.BrowseView_editable, true);
    selectDir = a.getBoolean(R.styleable.BrowseView_select_dir, false);
    selectFile = a.getBoolean(R.styleable.BrowseView_select_file, false);
    checkWritable = a.getBoolean(R.styleable.BrowseView_writable, selectDir);
    a.recycle();

    LayoutInflater i = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    if (i == null) throw new RuntimeException("Inflater is null");
    i.inflate(R.layout.browse_view, this, true);
    TextView title = getTitle();
    EditText path = getPath();

    title.setText(titleAttr);
    path.setText(pathAttr);

    if (!editable) {
      path.setKeyListener(null);
      path.setFocusable(false);
      path.setClickable(false);
    }
  }

  public TextView getTitle() {
    return (TextView) getChildAt(0);
  }

  public ImageView getLeftButton() {
    return (ImageView) getChildAt(1);
  }

  public ImageView getBrowseButton() {
    return (ImageView) getChildAt(2);
  }

  public void setTitle(String title) {
    getTitle().setText(title);
  }

  public EditText getPath() {
    return (EditText) getChildAt(3);
  }

  public void setPath(String path) {
    EditText t = getPath();
    String current = t.getText().toString();
    if (!current.equals(path)) t.setText(path);
  }

  public void setPref(Prefs.K pref) {
    this.pref = pref;
    Adapters.editTextPrefAdapter(getPath(), pref);
    setListener();
  }

  public void setPref(Prefs.K pref, int prefIndex) {
    this.pref = pref;
    this.prefIndex = prefIndex;
    Adapters.editTextPrefAdapter(getPath(), pref, prefIndex);
    setListener();
  }

  private void setListener() {
    getBrowseButton().setOnClickListener(new View.OnClickListener() {
      @Override
      @RequiresApi(api = LOLLIPOP)
      public void onClick(View v) {
        ActivityBase a = Utils.getActivity(v);
        File current = new File(getPath().getText().toString());
        Intent intent = new Intent(v.getContext(), SelectFileActivity.class);
        intent.putExtra(SelectFileActivity.REQUEST_INITIAL, current);
        intent.putExtra(SelectFileActivity.REQUEST_DIR, selectDir);
        intent.putExtra(SelectFileActivity.REQUEST_FILE, selectFile);
        intent.putExtra(SelectFileActivity.REQUEST_WRITABLE, checkWritable);
        a.setActivityResultHandler(BrowseView.this);
        a.startActivityForResult(intent, REQ_FILE);
      }
    });
  }

  @Override
  public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
    if ((data != null) && (pref != null)) {
      final File f;

      if (requestCode == REQ_FILE) {
        if (resultCode != SelectFileActivity.RESULT_OK) return true;
        f = (File) data.getSerializableExtra(SelectFileActivity.RESULT_FILE);
      } else {
        return false;
      }

      setResult(f);
    }

    return false;
  }

  private void setResult(File f) {
    setPath(f);
  }

  private void setPath(File f) {
    Prefs p = Utils.getActivity(this).getPrefs();
    p.set(pref, f.getAbsolutePath(), prefIndex);
  }

  @SuppressWarnings("unused")
  private boolean isBrowseSupported() {
    byte b = isBrowseSupported;

    if (b == 0) {
      if ((SDK_INT >= LOLLIPOP) && (SDK_INT < N)) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        PackageManager pm = Utils.getActivity(this).getApplicationContext().getPackageManager();
        ResolveInfo info = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        isBrowseSupported = b = (info != null) ? (byte) 1 : (byte) 2;
      } else {
        isBrowseSupported = b = 2;
      }
    }
    return b == 1;
  }
}
