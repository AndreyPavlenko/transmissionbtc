package com.ap.transmission.btc.activities;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.support.v4.provider.DocumentFile;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.ap.transmission.btc.R;
import com.ap.transmission.btc.StorageAccess;
import com.ap.transmission.btc.Utils;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static android.os.Build.VERSION;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES;
import static android.os.Build.VERSION_CODES.KITKAT;
import static android.os.Build.VERSION_CODES.LOLLIPOP;
import static android.os.Build.VERSION_CODES.N;
import static com.ap.transmission.btc.Utils.err;
import static com.ap.transmission.btc.Utils.hasWritePerms;
import static com.ap.transmission.btc.Utils.showErr;
import static com.ap.transmission.btc.Utils.showMsg;

/**
 * @author Andrey Pavlenko
 */
public class SelectFileActivity extends ListActivity {
  public static final String REQUEST_FILE = "file";
  public static final String REQUEST_DIR = "dir";
  public static final String REQUEST_WRITABLE = "writable";
  public static final String REQUEST_INITIAL = "init";
  public static final String REQUEST_PATTERN = "pattern";
  public static final String REQUEST_MSG = "msg";
  public static final int RESULT_OK = 1;
  public static final int RESULT_CANCEL = 2;
  public static final String RESULT_FILE = "file";
  private static final int REQ_DOC = 10;
  private static final int REQ_PERM = 11;
  private static final int REQ_PERM_MKDIR = 12;
  private static byte useOpenDocumentTree;
  private ListFiles list;
  private boolean filesOnly;
  private boolean dirsOnly;
  private boolean writable;
  private Pattern pattern;
  private File selection;
  private String newFolderName;

  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    List<File> roots = new ArrayList<>(getRoots());
    Intent i = getIntent();
    File initial = (File) i.getSerializableExtra(REQUEST_INITIAL);
    String ptrn = i.getStringExtra(REQUEST_PATTERN);
    String msg = i.getStringExtra(REQUEST_MSG);
    filesOnly = i.getBooleanExtra(REQUEST_FILE, false);
    dirsOnly = i.getBooleanExtra(REQUEST_DIR, false);
    writable = i.getBooleanExtra(REQUEST_WRITABLE, false);

    if (dirsOnly && writable && useOpenDocumentTree()) {
      Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
      startActivityForResult(intent, REQ_DOC);
      return;
    }

    pattern = (ptrn == null) ? null : Pattern.compile(ptrn);
    list = new ListFiles(roots, null, null);

    setContent(initial);
    setContentView(R.layout.select_file);

    Button ok = findViewById(R.id.button_ok);
    Button cancel = findViewById(R.id.button_cancel);
    Button newFolder = findViewById(R.id.button_new_folder);

    ok.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        ok();
      }
    });
    cancel.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        setResult(RESULT_CANCEL);
        finish();
      }
    });
    newFolder.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        newFolder();
      }
    });

    refresh();

    if (msg != null) {
      showMsg(ok, msg);
    }
  }

  private void setContent(File initial) {
    if ((initial != null) && initial.exists()) {
      File dir = initial.isFile() ? initial.getParentFile() : initial;
      selection = dirsOnly ? dir : initial;

      if (dir != null) {
        FileComparator cmp = new FileComparator();
        for (File f : Utils.splitPath(dir, true)) {
          File[] ls = f.listFiles();

          if (ls != null) {
            ls = filter(ls);
            Arrays.sort(ls, cmp);
            list = new ListFiles(Arrays.asList(ls), f, list);
          }
        }
      }
    }

    setListAdapter(new Adapter());
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.select_file_menu, menu);
    return true;
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    menu.getItem(0).setEnabled(selection != null);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == R.id.new_folder) {
      newFolder();
    }
    return true;
  }

  private void newFolder() {
    if (selection == null) return;
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    final EditText input = new EditText(this);
    input.setLines(1);
    input.setSingleLine();
    builder.setTitle(R.string.folder_name);
    builder.setView(input);

    builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        String text = input.getText().toString().trim();

        if (!text.isEmpty()) {
          File dir = selection.isDirectory() ? selection : selection.getParentFile();

          if (hasWritePerms(dir) || (SDK_INT < N)) {
            File newDir = new File(dir, text);

            if (newDir.mkdir() || StorageAccess.createDir(newDir.getAbsolutePath())) {
              setContent(selection);
            } else {
              showErr(findViewById(R.id.button_ok), R.string.err_create_dir, newFolderName);
            }
          } else {
            newFolderName = text;
            if (!requestAccess(REQ_PERM_MKDIR)) {
              showNotWritableErr(selection);
            }
          }
        }
      }
    });
    builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        dialog.cancel();
      }
    });
    builder.show();
  }

  @SuppressLint("SetTextI18n")
  private void refresh() {
    TextView title = findViewById(R.id.title);
    Button ok = findViewById(R.id.button_ok);
    Button newFolder = findViewById(R.id.button_new_folder);
    ok.setEnabled(selection != null);
    newFolder.setEnabled(list.dir != null);
    if (list.dir == null) title.setText("");
    else title.setText("> " + list.dir.getAbsolutePath());
  }

  private void ok() {
    if (writable && !Utils.hasWritePerms(selection)) {
      if (!requestAccess(REQ_PERM)) showNotWritableErr(selection);
    } else {
      setResult(selection);
    }
  }

  private File[] filter(File[] ls) {
    if ((pattern == null) && !dirsOnly) return ls;
    File[] filtered = new File[ls.length];
    int i = 0;

    for (File f : ls) {
      if (f.isDirectory()) {
        filtered[i++] = f;
      } else if (!dirsOnly && pattern.matcher(f.getName()).matches()) {
        filtered[i++] = f;
      }
    }

    return Arrays.copyOf(filtered, i);
  }

  @Override
  protected void onListItemClick(ListView l, View v, int position, long id) {
    Object item = getListAdapter().getItem(position);

    if (item instanceof ListFiles) {
      list = (ListFiles) item;
      if (filesOnly) selection = null;
      else selection = list.dir;
    } else {
      File file = (File) item;

      if (file.isDirectory()) {
        selection = filesOnly ? null : file;
      } else {
        if (!dirsOnly) {
          selection = file;
          ok();
        }

        return;
      }

      File[] ls = file.listFiles();
      List<File> files;

      if (ls == null) {
        files = Collections.emptyList();
      } else {
        ls = filter(ls);
        files = new ArrayList<>(ls.length);

        for (File f : ls) {
          if (f.isDirectory()) {
            if (f.canRead()) files.add(f);
          } else if (!dirsOnly) {
            files.add(f);
          }
        }
      }

      Collections.sort(files, new FileComparator());
      list = new ListFiles(files, file, list);
    }

    refresh();
    l.setAdapter(getListAdapter());
  }

  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    Uri uri;

    if ((data == null) || ((uri = data.getData()) == null)) {
      if (requestCode == REQ_DOC) {
        finish();
        return;
      }

      showNotWritableErr(selection);
      return;
    }

    if (requestCode == REQ_PERM) {
      if (takePersistPerm(data, uri, selection)) {
        setResult(selection);
      }
    } else if (requestCode == REQ_PERM_MKDIR) {
      File dir = selection.isDirectory() ? selection : selection.getParentFile();

      if (takePersistPerm(data, uri, dir)) {
        if (addMapping(dir, uri)) {
          if (StorageAccess.createDir(new File(dir, newFolderName).getAbsolutePath())) {
            setContent(selection);
          } else {
            showErr(findViewById(R.id.button_ok), R.string.err_create_dir, newFolderName);
          }
        } else {
          showNotWritableErr(selection);
        }
      }
    } else if (requestCode == REQ_DOC) {
      String path = Utils.getRealDirPath(getApplicationContext(), uri);

      if (path == null) {
        showErr(findViewById(R.id.button_ok), R.string.err_failed_to_get_path);
      } else {
        File f = new File(path);

        if (takePersistPerm(data, uri, f)) {
          setResult(f);
        }
      }
    }
  }

  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  private boolean requestAccess(int req) {
    if (SDK_INT >= N) {
      final Context ctx = getApplicationContext();
      StorageManager sm = (StorageManager) ctx.getSystemService(Context.STORAGE_SERVICE);

      if (sm != null) {
        StorageVolume v = sm.getStorageVolume(selection);

        if (v != null) {
          Intent i = v.createAccessIntent(null);

          if (i != null) {
            i.setFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try {
              startActivityForResult(i, req);
              return true;
            } catch (ActivityNotFoundException ex) {
              err(getClass().getName(), ex, "Failed to request access");
            }
          }
        }
      }
    }

    return false;
  }

  private boolean takePersistPerm(Intent data, Uri uri, File f) {
    if (SDK_INT < KITKAT) return false;

    try {
      int takeFlags = data.getFlags()
          & (Intent.FLAG_GRANT_READ_URI_PERMISSION
          | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
      getApplicationContext().getContentResolver().takePersistableUriPermission(uri, takeFlags);
    } catch (Exception ex) {
      showErr(findViewById(R.id.button_ok), R.string.err_no_persist_perm_arg, ex.getLocalizedMessage());
      return false;
    }

    if (addMapping(f, uri)) return true;
    showErr(findViewById(R.id.button_ok), R.string.err_no_persist_perm);
    return false;
  }

  private boolean addMapping(File fileOrDir, Uri uri) {
    File dir = fileOrDir.isDirectory() ? fileOrDir : fileOrDir.getParentFile();
    DocumentFile df = DocumentFile.fromTreeUri(getApplicationContext(), uri);
    String tmp = "transmission-" + UUID.randomUUID();
    while (df.findFile(tmp) != null) tmp = "transmission-" + UUID.randomUUID();
    DocumentFile tmpFile = df.createFile(null, tmp);

    if (tmpFile != null) {
      try {
        File d = dir;

        do {
          File f = new File(d, tmp);

          if (f.isFile()) {
            StorageAccess.addMapping(d.getAbsolutePath(), uri.toString());
            return true;
          } else {
            d = d.getParentFile();
          }
        } while (d != null);
      } finally {
        tmpFile.delete();
      }
    }

    return false;
  }

  private void showNotWritableErr(File f) {
    showErr(findViewById(R.id.button_ok), R.string.err_dir_not_writable, f.getAbsolutePath());
  }

  private void setResult(File f) {
    Intent i = new Intent();
    i.putExtra(RESULT_FILE, f);
    setResult(RESULT_OK, i);
    finish();
  }

  private boolean useOpenDocumentTree() {
    byte b = useOpenDocumentTree;

    if (b == 0) {
      if ((SDK_INT >= LOLLIPOP) && (SDK_INT < N)) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        PackageManager pm = getApplicationContext().getPackageManager();
        ResolveInfo info = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        useOpenDocumentTree = b = (info != null) ? (byte) 1 : (byte) 2;
      } else {
        useOpenDocumentTree = b = 2;
      }
    }

    return b == 1;
  }

  private final class Adapter extends BaseAdapter {
    private LayoutInflater inflater = LayoutInflater.from(SelectFileActivity.this);

    @Override
    public int getCount() {
      return (list.parent == null) ? list.list.size() : (list.list.size() + 1);
    }

    @Override
    public Object getItem(int p) {
      if (list.parent == null) {
        return list.list.get(p);
      } else if (p == 0) {
        return list.parent;
      } else {
        return list.list.get(p - 1);
      }
    }

    @Override
    public long getItemId(int position) {
      return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      TextView text;

      if (convertView == null) {
        text = (TextView) inflater.inflate(android.R.layout.simple_list_item_1, parent, false);
      } else {
        text = (TextView) convertView;
      }

      Object item = getItem(position);
      int img;
      String label = "\t";

      if (item instanceof ListFiles) {
        img = R.drawable.folder_up;
      } else if (list.parent == null) {
        File f = (File) item;
        img = R.drawable.storage;

        if (VERSION.SDK_INT >= VERSION_CODES.N) {
          StorageVolume v;
          StorageManager sm = (StorageManager) getSystemService(Context.STORAGE_SERVICE);

          if ((sm != null) && ((v = sm.getStorageVolume(f)) != null)) {
            label += v.getDescription(getApplicationContext());
          } else {
            label += f.getAbsolutePath();
          }
        } else {
          label += f.getAbsolutePath();
        }
      } else if (((File) item).isDirectory()) {
        img = R.drawable.folder;
        label += ((File) item).getName();
      } else {
        img = R.drawable.file;
        label += ((File) item).getName();
      }

      Drawable d = getResources().getDrawable(img);
      int size = (int) (text.getTextSize() * text.getTextScaleX());
      d.setBounds(0, 0, size, size);
      text.setCompoundDrawables(d, null, null, null);
      text.setText(label);
      return text;
    }
  }

  @SuppressLint("ObsoleteSdkInt")
  public Collection<File> getRoots() {
    Set<File> files = new HashSet<>();

    if (VERSION.SDK_INT >= VERSION_CODES.N) {
      StorageManager sm = (StorageManager) getSystemService(Context.STORAGE_SERVICE);

      if (sm != null) {
        Class<?> c = StorageVolume.class;

        try {
          Method m = c.getDeclaredMethod("getPathFile");
          m.setAccessible(true);
          for (StorageVolume v : sm.getStorageVolumes()) files.add((File) m.invoke(v));
          return files;
        } catch (Exception ex) {
          Log.e(getClass().getName(), "StorageVolume.getPathFile() failed", ex);
        }
      }
    }

    File root = new File("/");
    if (root.canRead()) files.add(root);
    addRoot(files, getFilesDir());
    addRoot(files, getCacheDir());

    if (VERSION.SDK_INT >= VERSION_CODES.KITKAT) {
      addRoot(files, getObbDirs());
      addRoot(files, getExternalFilesDirs(null));
    } else {
      addRoot(files, getObbDir());
      addRoot(files, getExternalFilesDir(null));
    }

    return files;
  }

  private static void addRoot(Set<File> files, File... dirs) {
    if (dirs == null) return;
    for (File dir : dirs) addRoot(files, dir);
  }

  private static void addRoot(Set<File> files, File dir) {
    if (dir == null) return;
    for (File p = dir.getParentFile(); (p != null) && p.canRead(); dir = p, p = dir.getParentFile()) {}
    files.add(dir);
  }

  private static final class ListFiles {
    final List<File> list;
    final File dir;
    final ListFiles parent;


    ListFiles(List<File> list, File dir, ListFiles parent) {
      this.list = list;
      this.dir = dir;
      this.parent = parent;
    }
  }

  private static final class FileComparator implements Comparator<File> {

    @Override
    public int compare(File f1, File f2) {
      if (f1.isDirectory()) {
        return f2.isDirectory() ? f1.getName().compareTo(f2.getName()) : -1;
      } else if (f2.isDirectory()) {
        return 1;
      } else {
        return f1.getName().compareTo(f2.getName());
      }
    }
  }
}
