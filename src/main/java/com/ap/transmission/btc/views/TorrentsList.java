package com.ap.transmission.btc.views;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.support.design.widget.TabLayout;
import android.util.AttributeSet;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import com.ap.transmission.btc.BindingHelper;
import com.ap.transmission.btc.R;
import com.ap.transmission.btc.Utils;
import com.ap.transmission.btc.func.Consumer;
import com.ap.transmission.btc.services.TransmissionService;
import com.ap.transmission.btc.torrent.NoSuchTorrentException;
import com.ap.transmission.btc.torrent.Torrent;
import com.ap.transmission.btc.torrent.TorrentFile;
import com.ap.transmission.btc.torrent.TorrentFs;
import com.ap.transmission.btc.torrent.Transmission;

import java.util.Collections;
import java.util.List;

import static android.os.AsyncTask.Status.FINISHED;
import static com.ap.transmission.btc.Utils.getActivity;

/**
 * @author Andrey Pavlenko
 */
public class TorrentsList extends LinearLayout {
  private List<Torrent> torrents = Collections.emptyList();
  private boolean active;
  private UpdateTask update;

  public TorrentsList(Context context, AttributeSet attrs) {
    super(context, attrs);
    setOrientation(VERTICAL);
    setFocusable(true);
    TabLayout tabLayout = getActivity(this).findViewById(R.id.tabs);
    setActive(tabLayout.getSelectedTabPosition() == 0);
  }

  public void setHelper(@SuppressWarnings("unused") BindingHelper h) {
    update();
  }

  public void setActive(boolean active) {
    if (this.active == active) return;
    this.active = active;
    update();
  }

  private void update() {
    if (update != null) return;
    Transmission tr = TransmissionService.getTransmission();

    if (!active || (tr == null) || !tr.isRunning()) {
      if (torrents != Collections.EMPTY_LIST) {
        torrents = Collections.emptyList();
        updateList();
      }
    } else {
      final UpdateTask upd = update = new UpdateTask(new Consumer<List<Torrent>>() {
        @Override
        public void accept(List<Torrent> torrents) {
          update = null;
          updateList(torrents);
        }
      });
      upd.executeOnExecutor(tr.getExecutor(), tr);

      if (getChildCount() == 0) {
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
          @Override
          public void run() {
            if (upd.getStatus() != FINISHED) addView(new ProgressBar(getContext()));
          }
        }, 1000);
      }
    }
  }

  private void updateList(List<Torrent> ls) {
    if (!active) {
      update();
      return;
    }

    if (!compare(torrents, ls)) {
      torrents = ls;
      updateList();
    }

    try {
      int count = getChildCount();

      if ((count > 0) && (getChildAt(0) instanceof ProgressBar)) {
        removeViewAt(0);
        count--;
      }

      for (int i = 0; i < count; i++) {
        ((TorrentView) getChildAt(i)).update();
      }
    } catch (IllegalArgumentException ex) { // Torrent removed?
      Utils.err(getClass().getName(), ex, "Failed to update TorrentViews");
    }

    final Handler handler = new Handler();
    handler.postDelayed(new Runnable() {
      @Override
      public void run() {
        update();
      }
    }, 1000);
  }

  private void updateList() {
    Context ctx = getContext();
    removeAllViews();
    int margin = Utils.toPx(10);

    for (Torrent tor : torrents) {
      TorrentView v = new TorrentView(ctx, null);
      v.setTorrent(tor);
      addView(v);
      ((MarginLayoutParams) v.getLayoutParams()).setMargins(0, margin, 0, 0);
    }
  }

  private static boolean compare(List<Torrent> l1, List<Torrent> l2) {
    return (l1 == l2);
  }

  private static final class UpdateTask extends AsyncTask<Transmission, Integer, List<Torrent>> {
    private final Consumer<List<Torrent>> callback;

    UpdateTask(Consumer<List<Torrent>> callback) { this.callback = callback; }

    @Override
    protected List<Torrent> doInBackground(Transmission... tr) {
      try {
        for (TorrentFs fs = tr[0].getTorrentFs(); ; ) {
          try {
            @SuppressWarnings("unchecked") List<Torrent> ls = (List) fs.ls();
            if (!ls.isEmpty()) {
              ls.get(0).getStat(true); // Update torrent stat

              for (Torrent tor : ls) {
                for (TorrentFile f : tor.lsFiles()) {
                  f.isComplete(); // Update file stat
                }
              }
            }
            return ls;
          } catch (NoSuchTorrentException ex) {
            fs.reportNoSuchTorrent(ex);
          }
        }
      } catch (IllegalStateException ex) { // Service stopped
        return Collections.emptyList();
      }
    }

    @Override
    protected void onPostExecute(List<Torrent> torrents) {
      callback.accept(torrents);
    }
  }
}
