package com.ap.transmission.btc.torrent;

import android.app.Activity;
import android.net.Uri;
import android.view.View;

import com.ap.transmission.btc.CompletedFuture;
import com.ap.transmission.btc.Native;
import com.ap.transmission.btc.R;
import com.ap.transmission.btc.Utils;
import com.ap.transmission.btc.http.HttpServer;
import com.ap.transmission.btc.http.handlers.torrent.PlaylistHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import static com.ap.transmission.btc.Utils.showErr;

/**
 * @author Andrey Pavlenko
 */
public class TorrentDir implements TorrentItemContainer {
  private final TorrentItemContainer parent;
  private final String name;
  private final String fullName;
  private final int index;
  private String strId;
  private List<TorrentItem> children = new ArrayList<>();

  public TorrentDir(TorrentItemContainer parent, String name, String fullName, int index) {
    this.parent = parent;
    this.name = name;
    this.fullName = fullName;
    this.index = index;
  }

  public Torrent getTorrent() {
    return (parent instanceof Torrent) ? (Torrent) parent : ((TorrentDir) parent).getTorrent();
  }

  @Override
  public List<TorrentItem> ls() {
    return children;
  }

  public List<TorrentFile> lsFiles() {
    List<TorrentItem> ls = ls();
    List<TorrentFile> files = new ArrayList<>(ls.size());
    for (TorrentItem i : ls) {
      if (i instanceof TorrentFile) files.add((TorrentFile) i);
    }
    return files;
  }

  @Override
  public String getName() {
    return name;
  }

  @SuppressWarnings("unused")
  public String getFullName() {
    return fullName;
  }

  public int getIndex() {
    return index;
  }

  @Override
  public String getId() {
    String s = strId;
    if (s == null) strId = s = getTorrent().getHashString() + "-d" + getIndex();
    return s;
  }

  @Override
  public boolean isComplete() {
    for (TorrentItem i : ls()) {
      if (!i.isComplete()) return false;
    }
    return true;
  }

  @Override
  public boolean isDnd() {
    for (TorrentItem i : ls()) {
      if (!i.isDnd()) return false;
    }
    return true;
  }

  public Future<Void> setDnd(final boolean dnd) throws IllegalStateException, NoSuchTorrentException {
    final Torrent tor = getTorrent();
    List<TorrentFile> files = tor.lsFiles();
    int[] idx = new int[files.size()];
    int count = 0;

    for (TorrentFile f : files) {
      if (findFile(f.getIndex())) {
        idx[count++] = f.getIndex();
      }
    }

    if (count == 0) return CompletedFuture.VOID;
    if (count != idx.length) idx = Arrays.copyOf(idx, count);
    Future<Void> future;

    tor.readLock().lock();
    try {
      tor.checkValid();
      final int[] indexes = idx;
      future = tor.getTransmission().getExecutor().submit(new Callable<Void>() {

        @Override
        public Void call() throws Exception {
          tor.readLock().lock();
          try {
            tor.checkValid();
            getTorrent().checkValid();
            Native.torrentSetDnd(tor.getSessionId(), tor.getTorrentId(), indexes, dnd);
            return null;
          } catch (NoSuchTorrentException ex) {
            getFs().reportNoSuchTorrent(ex);
            throw ex;
          } finally {
            tor.readLock().unlock();
          }
        }
      });

      for (TorrentFile f : files) {
        int fidx = f.getIndex();
        for (int i : idx) {
          if (i == fidx) {
            f.setDndStat(dnd);
            break;
          }
        }
      }
    } finally {
      tor.readLock().unlock();
    }

    return future;
  }

  public boolean hasMediaFiles() {
    for (TorrentItem i : ls()) {
      if (i instanceof TorrentFile) {
        TorrentFile f = (TorrentFile) i;
        if (f.isVideo() || f.isAudio()) return true;
      }
    }
    return false;
  }

  public Uri getPlaylistUri() {
    try {
      Torrent tor = getTorrent();
      HttpServer http = tor.getTransmission().getHttpServer();
      return PlaylistHandler.createUri(http.getHostName(), http.getPort(), tor.getHashString(), getIndex());
    } catch (Exception ex) {
      Utils.err(getClass().getName(), ex, "Failed to create playlist uri", this);
      return null;
    }
  }

  public boolean play(Activity a, View v) {
    Uri uri = getPlaylistUri();

    if (uri == null) {
      showErr(v, R.string.err_failed_to_open_playlist);
      return false;
    } else {
      Utils.openUri(a, uri, PlaylistHandler.MIME_TYPE);
      return true;
    }
  }

  private boolean findFile(int idx) {
    for (TorrentItem i : ls()) {
      if (i instanceof TorrentFile) {
        if (((TorrentFile) i).getIndex() == idx) return true;
      } else if (((TorrentDir) i).findFile(idx)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public TorrentItemContainer getParent() {
    return parent;
  }

  @Override
  public TorrentFs getFs() {
    return getParent().getFs();
  }

  @Override
  public String toString() {
    return getName();
  }

  void addChild(TorrentItem c) {
    children.add(c);
  }

  void compactChildren() {
    if (children.isEmpty()) {
      children = Collections.emptyList();
    } else {
      ((ArrayList) children).trimToSize();
      children = Collections.unmodifiableList(children);
    }
  }
}
