package com.ap.transmission.btc.activities;

import android.os.AsyncTask;
import android.view.View;
import android.widget.ProgressBar;

import com.ap.transmission.btc.R;
import com.ap.transmission.btc.Utils;
import com.ap.transmission.btc.func.Consumer;
import com.ap.transmission.btc.func.Supplier;
import com.ap.transmission.btc.http.handlers.torrent.TorrentHandler;
import com.ap.transmission.btc.torrent.TorrentFile;
import com.ap.transmission.btc.torrent.Transmission;
import com.ap.transmission.btc.views.PathItem;

import java.util.List;
import java.util.concurrent.TimeoutException;

import static com.ap.transmission.btc.Utils.showErr;
import static com.ap.transmission.btc.services.TransmissionService.getTransmission;

/**
 * @author Andrey Pavlenko
 */
public class OpenTorrentActivity extends DownloadTorrentActivity {
  private WaitForTorrent waitFor;

  @Override
  public boolean isSequential() {
    return true;
  }

  @Override
  public boolean isIgnoreDuplicate() {
    return true;
  }

  @Override
  protected void finish(final String hash, final List<PathItem> items) {
    if (hash == null) return;

    final Transmission tr = getTransmission();
    int idx = 0;

    for (PathItem i : items) {
      if (i.isChecked()) {
        idx = i.getIndex();
        break;
      }
    }

    View listFiles = findViewById(R.id.list_files);
    ProgressBar progressBar = findViewById(R.id.progress_bar);
    listFiles.setVisibility(View.GONE);
    progressBar.setVisibility(View.VISIBLE);

    final int fileIndex = idx;
    Consumer<Object> callback = new Consumer<Object>() {
      @Override
      public void accept(Object o) {
        if (o == null) {
          // Canceled by user or timed out
        } else if (o instanceof TorrentFile) {
          TorrentFile trf = (TorrentFile) o;

          if (trf.open(OpenTorrentActivity.this, findViewById(R.id.button_download))) {
            OpenTorrentActivity.super.finish(hash, items);
          } else {
            finishWithDelay();
          }
        } else if (o instanceof TimeoutException) {
          TimeoutException ex = (TimeoutException) o;
          Utils.warn("OpenTorrentActivity", ex, "Timeout exceeded");
          showErr(findViewById(R.id.button_download), R.string.err_timeout_exceeded);
        } else if (o instanceof Throwable) {
          Throwable ex = (Throwable) o;
          Utils.warn("OpenTorrentActivity", ex, "Failed to open torrent");
          showErr(findViewById(R.id.button_download),
              R.string.err_failed_to_open_torrent, ex.getLocalizedMessage());
        } else {
          Utils.err("OpenTorrentActivity", "Must never get here: %s", o);
        }
      }
    };

    waitFor = new WaitForTorrent(callback, tr, hash, fileIndex);
    waitFor.execute((Void) null);
  }

  @Override
  public void finish() {
    if (waitFor != null) waitFor.cancel();
    super.finish();
  }

  private static final class WaitForTorrent extends AsyncTask<Void, Integer, Object> {
    private final Consumer<Object> callback;
    private final Transmission tr;
    private final String hash;
    private final int fileIndex;
    volatile boolean isCanceled;

    private WaitForTorrent(Consumer<Object> callback, Transmission tr, String hash, int fileIndex) {
      this.callback = callback;
      this.tr = tr;
      this.hash = hash;
      this.fileIndex = fileIndex;
    }


    @Override
    protected Object doInBackground(Void... params) {
      try {
        return TorrentHandler.waitFor(tr, hash, fileIndex, new Supplier<Boolean>() {
          @Override
          public Boolean get() {
            return isCanceled;
          }
        }, "WaitForTorrent");
      } catch (Throwable ex) {
        return ex;
      }
    }

    @Override
    protected void onPostExecute(Object ex) {
      callback.accept(ex);
    }

    void cancel() {
      isCanceled = true;
    }
  }
}
