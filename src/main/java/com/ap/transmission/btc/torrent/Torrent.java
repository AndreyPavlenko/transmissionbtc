package com.ap.transmission.btc.torrent;

import android.app.Activity;
import android.net.Uri;
import android.view.View;

import com.ap.transmission.btc.Native;
import com.ap.transmission.btc.R;
import com.ap.transmission.btc.Utils;
import com.ap.transmission.btc.http.HttpServer;
import com.ap.transmission.btc.http.handlers.torrent.PlaylistHandler;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;

import static com.ap.transmission.btc.Utils.debug;
import static com.ap.transmission.btc.Utils.isDebugEnabled;
import static com.ap.transmission.btc.Utils.showErr;

/**
 * @author Andrey Pavlenko
 */
public class Torrent implements TorrentItemContainer {
  private final TorrentFs fs;
  private final int torrentId;
  private final String hashString;
  private final String name;
  private byte[] hash;
  private List<TorrentDir> dirIndex;
  private List<TorrentFile> fileIndex;
  private TorrentStat stat;

  Torrent(TorrentFs fs, int torrentId, String hashString, String name) {
    this.fs = fs;
    this.torrentId = torrentId;
    this.hashString = hashString;
    this.name = name;
  }

  @Override
  public List<TorrentItem> ls() {
    try {
      List<TorrentItem> dirs = index(false);
      List<TorrentItem> files = index(true);
      List<TorrentItem> ls = new ArrayList<>(files.size());

      for (TorrentItem i : dirs) {
        if (i.getParent() == this) ls.add(i);
      }

      for (TorrentItem i : files) {
        if (i.getParent() == this) ls.add(i);
      }

      return ls;
    } catch (NoSuchTorrentException ex) {
      getFs().reportNoSuchTorrent(ex);
      return Collections.emptyList();
    }
  }

  public boolean hasFiles() throws IllegalStateException, NoSuchTorrentException {
    try {
      return !index(true).isEmpty();
    } catch (NoSuchTorrentException ex) {
      getFs().reportNoSuchTorrent(ex);
      throw ex;
    }
  }

  public boolean hasMediaFiles() throws IllegalStateException, NoSuchTorrentException {
    for (TorrentFile f : this.<TorrentFile>index(true)) {
      if (f.isVideo() || f.isAudio()) return true;
    }
    return false;
  }

  public TorrentStat getStat(boolean update, int timeout) {
    TorrentStat s;

    if (update || ((s = stat) == null)) {
      readLock().lock();
      try {
        checkValid();
        Future<TorrentStat> f = getTransmission().getExecutor().submit(new Callable<TorrentStat>() {
          @Override
          public TorrentStat call() {
            getFs().updateStat();
            return stat;
          }
        });

        return stat = f.get(timeout, TimeUnit.SECONDS);
      } catch (InterruptedException ex) {
        return null;
      } catch (ExecutionException ex) {
        Throwable t = ex.getCause();

        if (t instanceof IllegalStateException) {
          throw (IllegalStateException) t;
        } else {
          throw new RuntimeException("Unexpected exception", t);
        }
      } catch (TimeoutException ex) {
        Utils.warn(getClass().getName(), "getStat() timed out: timeout=%d seconds", timeout);
        return null;
      } finally {
        readLock().unlock();
      }
    } else {
      return s;
    }
  }

  public TorrentStat getStat(boolean update) {
    if (update || (stat == null)) getFs().updateStat();
    return stat;
  }

  TorrentStat getStat() {
    return stat;
  }

  void setStat(TorrentStat stat) {
    this.stat = stat;
  }

  @SuppressWarnings("unchecked")
  public List<TorrentFile> lsFiles() throws IllegalStateException, NoSuchTorrentException {
    return (List) index(true);
  }

  public boolean preloadIndex(int timeout) {
    return preloadIndex(timeout, false);
  }

  public boolean preloadIndexAndFileStat(int timeout) {
    return preloadIndex(timeout, true);
  }

  private boolean preloadIndex(int timeout, final boolean fileStat) {
    List<TorrentFile> files = fileIndex;

    if (files != null) {
      if (!fileStat) return true;
      boolean loaded = true;

      for (TorrentFile file : files) {
        if (!file.statLoaded()) {
          loaded = false;
          break;
        }
      }

      if (loaded) return true;
    }

    readLock().lock();
    try {
      checkValid();
      Future<List[]> f = getTransmission().getExecutor().submit(new Callable<List[]>() {
        @Override
        public List[] call() throws Exception {
          List<TorrentFile> files = index(true);
          List[] l = new List[]{files, index(false)};
          if (fileStat) for (TorrentFile file : files) file.isDnd();
          return l;
        }
      });

      List[] l = f.get(timeout, TimeUnit.SECONDS);
      //noinspection unchecked
      fileIndex = l[0].isEmpty() ? null : l[0];
      //noinspection unchecked
      dirIndex = l[1].isEmpty() ? null : l[1];
      return true;
    } catch (InterruptedException ex) {
      return false;
    } catch (IllegalStateException ex) {
      Utils.err(getClass().getName(), ex, "preloadIndex() failed");
      return false;
    } catch (ExecutionException ex) {
      Throwable t = ex.getCause();

      if (t instanceof NoSuchTorrentException) {
        getFs().reportNoSuchTorrent((NoSuchTorrentException) t);
        return false;
      } else if (t instanceof IllegalStateException) {
        throw (IllegalStateException) t;
      } else {
        throw new RuntimeException("Unexpected exception", t);
      }
    } catch (TimeoutException ex) {
      Utils.warn(getClass().getName(), "preloadIndex() timed out: timeout=%d seconds", timeout);
      return false;
    } finally {
      readLock().unlock();
    }
  }

  @SuppressWarnings("unchecked")
  private <T> List<T> index(boolean isFile) throws IllegalStateException, NoSuchTorrentException {
    List<T> idx = (List) (isFile ? fileIndex : dirIndex);

    if (idx == null) {
      String[] files;
      readLock().lock();
      try {
        checkValid();
        files = Native.torrentListFiles(getSessionId(), getTorrentId());
      } catch (NoSuchTorrentException ex) { // Torrent removed?
        getFs().reportNoSuchTorrent(ex);
        throw ex;
      } finally {
        readLock().unlock();
      }

      if (files == null) return Collections.emptyList();

      Map<String, TorrentDir> dirs = new HashMap<>();
      ArrayList<TorrentDir> dirIndex = new ArrayList<>(files.length);
      ArrayList<TorrentFile> fileIndex = new ArrayList<>(files.length);
      StringBuilder sb = new StringBuilder();

      for (int i = 0; i < files.length; i++) {
        String f = files[i];
        TorrentItemContainer parent = this;
        sb.setLength(0);

        for (StringTokenizer st = new StringTokenizer(f, "/"); st.hasMoreTokens(); ) {
          if (sb.length() != 0) sb.append('/');
          String name = st.nextToken();
          String path = sb.append(name).toString();

          if (st.hasMoreTokens()) {
            TorrentDir dir = dirs.get(path);

            if (dir == null) {
              dir = new TorrentDir(parent, name, path, dirIndex.size());
              dirs.put(path, dir);
              dirIndex.add(dir);
              if (parent instanceof TorrentDir) ((TorrentDir) parent).addChild(dir);
            }

            parent = dir;
          } else {
            TorrentFile file = new TorrentFile(this, parent, i, name, path);
            fileIndex.add(file);
            if (parent instanceof TorrentDir) ((TorrentDir) parent).addChild(file);
          }
        }
      }

      for (TorrentDir d : dirIndex) d.compactChildren();
      dirIndex.trimToSize();
      fileIndex.trimToSize();
      this.dirIndex = dirIndex.isEmpty() ? null : Collections.unmodifiableList(dirIndex);
      this.fileIndex = fileIndex.isEmpty() ? null : Collections.unmodifiableList(fileIndex);
      idx = (List) (isFile ? this.fileIndex : this.dirIndex);
    }

    return (idx == null) ? Collections.<T>emptyList() : idx;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getId() {
    return getHashString();
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

  @Override
  public TorrentItemContainer getParent() {
    return getFs();
  }

  @Override
  public TorrentFs getFs() {
    return fs;
  }

  public Transmission getTransmission() {
    return getFs().getTransmission();
  }

  public int getTorrentId() {
    return torrentId;
  }

  public byte[] getHash() throws IllegalStateException, NoSuchTorrentException {
    byte[] h = hash;

    if (h == null) {
      h = new byte[Native.hashLength()];

      readLock().lock();
      try {
        checkValid();
        Native.torrentGetHash(getSessionId(), getTorrentId(), h);
        hash = h;
      } finally {
        readLock().unlock();
      }
    }

    return h.clone();
  }

  public Future<Void> remove(final boolean removeLocalData) throws IllegalStateException {
    readLock().lock();
    try {
      checkValid();
      fs.removed(Torrent.this);
      return getTransmission().getExecutor().submit(new Callable<Void>() {

        @Override
        public Void call() throws Exception {
          readLock().lock();
          try {
            checkValid();
            Native.torrentRemove(getSessionId(), getTorrentId(), removeLocalData);
            return null;
          } catch (NoSuchTorrentException ex) {
            getFs().reportNoSuchTorrent(ex);
            throw ex;
          } finally {
            readLock().unlock();
          }
        }
      });
    } finally {
      readLock().unlock();
    }
  }

  public Future<Void> stop() throws IllegalStateException {
    readLock().lock();
    try {
      checkValid();
      return getTransmission().getExecutor().submit(new Callable<Void>() {

        @Override
        public Void call() throws Exception {
          readLock().lock();
          try {
            checkValid();
            Native.torrentStop(getSessionId(), getTorrentId());
            return null;
          } catch (NoSuchTorrentException ex) {
            getFs().reportNoSuchTorrent(ex);
            throw ex;
          } finally {
            readLock().unlock();
          }
        }
      });
    } finally {
      readLock().unlock();
    }
  }

  public Future<Void> start() throws IllegalStateException {
    readLock().lock();
    try {
      checkValid();
      return getTransmission().getExecutor().submit(new Callable<Void>() {

        @Override
        public Void call() throws Exception {
          readLock().lock();
          try {
            checkValid();
            Native.torrentStart(getSessionId(), getTorrentId());
            return null;
          } catch (NoSuchTorrentException ex) {
            getFs().reportNoSuchTorrent(ex);
            throw ex;
          } finally {
            readLock().unlock();
          }
        }
      });
    } finally {
      readLock().unlock();
    }
  }

  public Future<Void> verify() throws IllegalStateException {
    readLock().lock();
    try {
      checkValid();
      return getTransmission().getExecutor().submit(new Callable<Void>() {

        @Override
        public Void call() throws Exception {
          readLock().lock();
          try {
            checkValid();
            Native.torrentVerify(getSessionId(), getTorrentId());
            return null;
          } catch (NoSuchTorrentException ex) {
            getFs().reportNoSuchTorrent(ex);
            throw ex;
          } finally {
            readLock().unlock();
          }
        }
      });
    } finally {
      readLock().unlock();
    }
  }

  public String getHashString() {
    return hashString;
  }

  public static String[] ListFilesFromFile(String torrentFilePath) throws IOException {
    return Native.torrentListFilesFromFile(torrentFilePath);
  }

  public static byte[] hashStringToBytes(String hash) {
    return Native.hashStringToBytes(hash);
  }

  public static String hashBytesToString(byte[] hash) {
    return Native.hashBytesToString(hash);
  }

  public void getPieceHash(long piece, byte[] pieceHash)
      throws IllegalStateException, NoSuchTorrentException {
    readLock().lock();
    try {
      checkValid();
      Native.torrentGetPieceHash(getSessionId(), getTorrentId(), piece, pieceHash);
    } finally {
      readLock().unlock();
    }
  }

  public void getPiece(long pieceIndex, byte[] dst,
                       int offset, int len)
      throws IllegalStateException, NoSuchTorrentException, IOException {
    readLock().lock();
    try {
      checkValid();
      Native.torrentGetPiece(getSessionId(), getTorrentId(), pieceIndex, dst, offset, len);
    } finally {
      readLock().unlock();
    }
  }

  public void setPiecesHiPri(long firstPiece, long lastPiece)
      throws IllegalStateException, NoSuchTorrentException {
    readLock().lock();
    try {
      checkValid();
      if (isDebugEnabled()) {
        debug(getClass().getName(), "Increasing priority of pieces: %d-%d",
            firstPiece, lastPiece);
      }
      Native.torrentSetPiecesHiPri(getSessionId(), getTorrentId(), firstPiece, lastPiece);
    } finally {
      readLock().unlock();
    }
  }

  public Future<Void> setLocation(final File dir) throws IllegalStateException {
    readLock().lock();
    try {
      checkValid();
      return getTransmission().getExecutor().submit(new Callable<Void>() {

        @Override
        public Void call() throws Exception {
          readLock().lock();
          try {
            checkValid();
            Native.torrentSetLocation(getSessionId(), getTorrentId(), dir.getAbsolutePath());
            return null;
          } catch (NoSuchTorrentException ex) {
            getFs().reportNoSuchTorrent(ex);
            throw ex;
          } finally {
            readLock().unlock();
          }
        }
      });
    } finally {
      readLock().unlock();
    }
  }

  public TorrentFile getFile(int index) throws IllegalStateException, NoSuchTorrentException {
    return this.<TorrentFile>index(true).get(index);
  }

  public TorrentDir getDir(int index) throws IllegalStateException, NoSuchTorrentException {
    List<TorrentDir> dirs = index(false);
    if (index >= dirs.size()) throw new IllegalArgumentException("No such dir: " + index);
    TorrentDir d = dirs.get(index);
    if (d.getIndex() != index) throw new IllegalStateException("Unexpected dir index");
    return d;
  }

  public Uri getPlaylistUri() {
    try {
      HttpServer http = getFs().getTransmission().getHttpServer();
      return PlaylistHandler.createUri(http.getHostName(), http.getPort(), getHashString(), -1);
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

  @Override
  public String toString() {
    if (name != null) {
      return "Torrent: name=" + name + ", session=" + getSessionId();
    } else if (hashString != null) {
      return "Torrent: hash=" + hashString + ", session=" + getSessionId();
    } else {
      return "Torrent: id=" + torrentId + ", session=" + getSessionId();
    }
  }

  Lock readLock() {
    return getFs().readLock();
  }

  long getSessionId() {
    return getFs().getSessionId();
  }

  void checkValid() throws IllegalStateException {
    getFs().checkValid();
  }
}
