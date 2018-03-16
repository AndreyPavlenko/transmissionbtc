package com.ap.transmission.btc.activities;

import android.databinding.DataBindingUtil;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.ap.transmission.btc.BindingHelper;
import com.ap.transmission.btc.Native;
import com.ap.transmission.btc.Prefs;
import com.ap.transmission.btc.R;
import com.ap.transmission.btc.Utils;
import com.ap.transmission.btc.databinding.DownloadTorrentBinding;
import com.ap.transmission.btc.databinding.PathViewBinding;
import com.ap.transmission.btc.func.Consumer;
import com.ap.transmission.btc.func.Function;
import com.ap.transmission.btc.func.Promise;
import com.ap.transmission.btc.services.TransmissionService;
import com.ap.transmission.btc.torrent.DuplicateTorrentException;
import com.ap.transmission.btc.torrent.Torrent;
import com.ap.transmission.btc.torrent.Transmission.AddTorrentResult;
import com.ap.transmission.btc.views.BrowseView;
import com.ap.transmission.btc.views.PathItem;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.ap.transmission.btc.Utils.curl;
import static com.ap.transmission.btc.Utils.err;
import static com.ap.transmission.btc.Utils.showErr;
import static com.ap.transmission.btc.Utils.showMsg;
import static com.ap.transmission.btc.Utils.toPx;
import static com.ap.transmission.btc.services.TransmissionService.getTransmission;

/**
 * @author Andrey Pavlenko
 */
@SuppressWarnings("ResultOfMethodCallIgnored")
public class DownloadTorrentActivity extends ActivityBase {
  private File torrentFile;
  private AsyncTask<Void, Integer, Throwable> loadTorrent;

  public boolean isSequential() {
    return getPrefs().isSeqDownloadEnabled();
  }

  public boolean isIgnoreDuplicate() {
    return false;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    Prefs prefs = getPrefs();
    final DownloadTorrentBinding b = DataBindingUtil.setContentView(this, R.layout.download_torrent);
    BindingHelper h = new BindingHelper(this, b);
    b.setH(h);
    b.setP(prefs);
    setTitle(R.string.app_name);

    final Uri torrentUri = getIntent().getData();

    if (torrentUri == null) {
      showErr(b.getRoot(), R.string.err_no_torrent_file);
      finishWithDelay();
      return;
    }

    final boolean isMagnet = "magnet".equals(torrentUri.getScheme());

    try {
      if (torrentFile != null) torrentFile.delete();
      torrentFile = File.createTempFile("tmp_", ".torrent");
      torrentFile.deleteOnExit();
    } catch (Exception ex) {
      Log.e(getClass().getName(), "Failed to open torrent file: " + torrentUri, ex);
      showErr(b.getRoot(), R.string.err_failed_to_open_torrent_file, torrentUri);
      finishWithDelay();
      return;
    }

    final ProgressBar pb = findViewById(R.id.progress_bar);
    final Button downloadButton = findViewById(R.id.button_download);
    final boolean[] downloadPressed = new boolean[]{false};
    downloadButton.setText((getClass() == DownloadTorrentActivity.class) ?
        R.string.download : R.string.open);
    pb.setVisibility(View.VISIBLE);
    final Consumer<Throwable> callback = new Consumer<Throwable>() {
      @Override
      public void accept(Throwable ex) {
        pb.setVisibility(View.GONE);

        if (ex == null) {
          if (init(b, torrentFile)) {
            downloadButton.setEnabled(true);
            loadTorrent = null;
            b.invalidateAll();
            if (downloadPressed[0]) downloadButton.performClick();
          }
        } else if (ex instanceof DuplicateTorrentException) {
          downloadButton.setEnabled(false);
          showErr(b.getRoot(), R.string.err_torrent_already_added);
          finishWithDelay();
        } else if ((loadTorrent != null) && !loadTorrent.isCancelled()) {
          int msg = isMagnet ? R.string.err_failed_to_load_magnet : R.string.err_failed_to_load_torrent;
          err(getClass().getName(), ex, "Failed to load %s", torrentUri);
          showErr(b.getRoot(), msg, ex.getLocalizedMessage());
          finishWithDelay();
        }
      }
    };

    Button cancelButton = findViewById(R.id.button_cancel);
    cancelButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        cancel();
      }
    });

    if (isMagnet) {
      if (getClass() == DownloadTorrentActivity.class) {
        downloadButton.setText(R.string.enqueue);
        downloadButton.setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            downloadPressed[0] = true;
            if (loadTorrent != null) ((LoadMagnet) loadTorrent).enqueue();
            DownloadTorrentActivity.this.finish();
          }
        });
      } else {
        downloadButton.setEnabled(false);
      }
      TransmissionService.start(getApplicationContext(), new Runnable() {
        @Override
        public void run() {
          loadTorrent = new LoadMagnet(torrentUri, torrentFile, callback, downloadPressed[0]);
          loadTorrent.execute((Void) null);
          if (downloadPressed[0]) ((LoadMagnet) loadTorrent).enqueue();
        }
      });
    } else {
      downloadButton.setEnabled(false);
      loadTorrent = curl(getApplicationContext(), torrentUri, torrentFile, callback);
      loadTorrent.execute((Void) null);
    }
  }

  protected boolean init(DownloadTorrentBinding b, File torrentFile) {
    String[] files;

    try {
      files = Torrent.ListFilesFromFile(torrentFile.getAbsolutePath());
    } catch (IOException ex) {
      Log.e(getClass().getName(), "torrentListFiles() failed", ex);
      showErr(b.getRoot(), R.string.err_failed_to_parse_torrent_file);
      finishWithDelay();
      return false;
    }

    if (files.length == 0) {
      showErr(b.getRoot(), R.string.msg_no_files_in_torrent);
      finishWithDelay();
      return false;
    }

    List<PathItem> items = PathItem.ls(PathItem.split(files));
    ViewGroup list = findViewById(R.id.list_files);
    LayoutInflater lif = LayoutInflater.from(list.getContext());

    for (PathItem item : items) {
      PathViewBinding ib = DataBindingUtil.inflate(lif, R.layout.path_view, list, true);
      View v = ib.getRoot();
      TextView label = v.findViewById(R.id.label);
      label.setPadding(toPx(10 * item.getLevel()), 0, 0, 0);
      item.setBinding(ib);
    }

    BrowseView downloadDir = findViewById(R.id.download_dir);
    Button downloadButton = findViewById(R.id.button_download);
    initButtons(torrentFile, items, downloadDir, downloadButton);
    return true;
  }

  protected void initButtons(final File torrentFile, final List<PathItem> items,
                             final BrowseView downloadDir, final Button downloadButton) {
    downloadButton.setText((getClass() == DownloadTorrentActivity.class) ?
        R.string.download : R.string.open);
    downloadButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        File dir = new File(downloadDir.getPath().getText().toString());
        download(downloadButton, torrentFile, dir, items, new Function<String, Void>() {
          @Override
          public Void apply(String hash) {
            if (hash != null) finish(hash, items);
            return null;
          }
        });
      }
    });
  }

  protected void download(final View v, final File torrentFile, final File downloadDir,
                          final List<PathItem> items, final Function<String, Void> onFinish) {
    boolean hasWanted = false;
    final List<PathItem> unwanted = new ArrayList<>(items.size());
    v.setEnabled(false);

    for (PathItem i : items) {
      if (!i.isDir()) {
        if (i.isChecked()) hasWanted = true;
        else unwanted.add(i);
      }
    }

    if (!hasWanted) {
      showMsg(v, R.string.msg_select_file);
      onFinish.apply(null);
      return;
    }

    TransmissionService.start(getApplicationContext(), new Runnable() {
      @Override
      public void run() {
        if (!downloadDir.exists() && !downloadDir.mkdirs()) {
          showErr(v, R.string.err_create_dir, downloadDir);
          onFinish.apply(null);
          return;
        }

        byte[] hash = new byte[Native.hashLength()];
        AddTorrentResult result = AddTorrentResult.OK;
        int[] indexes = new int[unwanted.size()];
        for (int i = 0; i < indexes.length; i++) indexes[i] = unwanted.get(i).getIndex();

        try {
          result = getTransmission().addTorrent(torrentFile, downloadDir, indexes,
              hash, true, isSequential(), 0, 0);
        } catch (InterruptedException ignore) {}

        switch (result) {
          case OK:
          case OK_DELETE:
            torrentFile.delete();
            break;
          case PARSE_ERR:
            showErr(v, R.string.err_failed_to_parse_torrent_file);
            hash = null;
            break;
          case DUPLICATE:
            if (isIgnoreDuplicate()) {
              try {
                hash = Native.hashGetTorrentHash(torrentFile.getAbsolutePath());
              } catch (IOException ex) {
                hash = null;
                Log.e(getClass().getName(), "Failed to get torrent hash", ex);
                showErr(v, R.string.err_failed_to_parse_torrent_file);
              }
            } else {
              showErr(v, R.string.err_torrent_already_added);
              hash = null;
            }

            break;
          case NOT_STARTED:
            showErr(v, R.string.err_service_not_started);
            hash = null;
            break;
        }

        onFinish.apply((hash == null) ? null : Torrent.hashBytesToString(hash));
      }
    });
  }

  @Override
  public void finish() {
    if (loadTorrent instanceof LoadMagnet) ((LoadMagnet) loadTorrent).cancelTask();
    else if (loadTorrent != null) loadTorrent.cancel(true);
    if (torrentFile != null) torrentFile.delete();
    super.finish();
  }

  protected void finish(String hash, List<PathItem> items) {
    finish();
  }

  protected void cancel() {
    finish();
  }

  protected void finishWithDelay() {
    Utils.runWithDelay(new Runnable() {
      @Override
      public void run() {
        finish();
      }
    }, 5);
  }

  private static final class LoadMagnet extends AsyncTask<Void, Integer, Throwable> {
    private final Consumer<Throwable> callback;
    private final Promise<Void> promise;
    private final boolean[] enqueue = new boolean[1];

    LoadMagnet(Uri torrentUri, File torrentFile, Consumer<Throwable> callback, boolean enqueue) {
      this.callback = callback;
      this.enqueue[0] = enqueue;
      promise = getTransmission().magnetToTorrent(torrentUri, torrentFile, 300, this.enqueue);
    }

    @Override
    protected Throwable doInBackground(Void... params) {
      try {
        promise.get();
        return null;
      } catch (Throwable ex) {
        return ex;
      }
    }

    synchronized void enqueue() {
      enqueue[0] = true;
      cancelTask();
    }

    @Override
    protected void onPostExecute(Throwable ex) {
      callback.accept(ex);
    }

    void cancelTask() {
      promise.cancel();
      cancel(false);
    }
  }
}
