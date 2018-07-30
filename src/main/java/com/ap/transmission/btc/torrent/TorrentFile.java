package com.ap.transmission.btc.torrent;

import android.app.Activity;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.view.View;

import com.ap.transmission.btc.Native;
import com.ap.transmission.btc.R;
import com.ap.transmission.btc.Utils;
import com.ap.transmission.btc.func.Supplier;
import com.ap.transmission.btc.http.HttpServer;
import com.ap.transmission.btc.http.handlers.torrent.TorrentHandler;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;

import static com.ap.transmission.btc.Utils.getFileExtension;
import static com.ap.transmission.btc.Utils.getMimeTypeFromExtension;
import static com.ap.transmission.btc.Utils.showErr;
import static com.ap.transmission.btc.torrent.Torrent.hashBytesToString;

/**
 * @author Andrey Pavlenko
 */
public class TorrentFile implements TorrentItem {
  private static final boolean VERIFY_PIECE = false;
  private final Torrent torrent;
  private final int index;
  private final TorrentItemContainer parent;
  private final String name;
  private final String fullName;
  private String location;
  private String strId;
  private long[] stat;
  private String mimeType;
  private byte type = -1;
  private MediaInfo mediaInfo;

  TorrentFile(Torrent torrent, TorrentItemContainer parent, int index, String name, String fullName) {
    this.torrent = torrent;
    this.parent = parent;
    this.index = index;
    this.name = name;
    this.fullName = fullName;
  }

  public Torrent getTorrent() {
    return torrent;
  }

  @Override
  public String getName() {
    return name;
  }

  public String getFullName() {
    return fullName;
  }

  @Override
  public String getId() {
    String s = strId;
    if (s == null) strId = s = getTorrent().getHashString() + "-f" + getIndex();
    return s;
  }

  @Override
  public TorrentItemContainer getParent() {
    return parent;
  }

  @Override
  public TorrentFs getFs() {
    return getTorrent().getFs();
  }

  public Transmission getTransmission() {
    return getTorrent().getTransmission();
  }

  public int getIndex() {
    return index;
  }

  public String findLocation()
      throws IllegalStateException, NoSuchTorrentException {
    String s = location;

    if (s == null) {
      readLock().lock();
      try {
        checkValid();
        Torrent tor = getTorrent();
        location = s = Native.torrentFindFile(tor.getSessionId(), tor.getTorrentId(), getIndex());
      } finally {
        readLock().unlock();
      }
    }

    return s;
  }

  public long getLength()
      throws IllegalStateException, NoSuchTorrentException {
    return stat(false)[1];
  }

  public long getPieceLength()
      throws IllegalStateException, NoSuchTorrentException {
    return stat(false)[0];
  }

  public long getFirstPieceIndex() throws IllegalStateException, NoSuchTorrentException {
    return stat(false)[3];
  }

  public long getLastPieceIndex()
      throws IllegalStateException, NoSuchTorrentException {
    return stat(false)[4];
  }

  public int getProgress(boolean update) throws IllegalStateException, NoSuchTorrentException {
    long[] s = stat(false);
    if (complete(s)) return 100;
    if (update) s = stat(true);
    if (complete(s)) return 100;

    long first = s[3];
    long last = s[4];
    int count = (int) (last - first + 1);
    int completed = 0;

    for (int i = 0; i < count; i++) {
      int fieldIdx = (i / 64) + 6;
      int bitIdx = (i % 64);
      long field = s[fieldIdx];
      if (bitSet(field, bitIdx)) completed++;
    }

    return Math.round((((float) completed / (float) count)) * 100);
  }

  public boolean isComplete() {
    long[] s = stat;
    try {
      return ((s != null) && complete(s)) || complete(stat(true));
    } catch (NoSuchTorrentException ex) {
      getFs().reportNoSuchTorrent(ex);
      return true;
    }
  }

  public boolean isDnd() {
    try {
      return dnd(stat(false));
    } catch (NoSuchTorrentException ex) {
      getFs().reportNoSuchTorrent(ex);
      return true;
    }
  }

  public Future<Void> setDnd(final boolean dnd) throws IllegalStateException {
    readLock().lock();
    try {
      checkValid();
      return getTransmission().getExecutor().submit(new Callable<Void>() {

        @Override
        public Void call() throws Exception {
          readLock().lock();
          try {
            Torrent tor = getTorrent();
            Native.torrentSetDnd(tor.getSessionId(), tor.getTorrentId(), new int[]{getIndex()}, dnd);
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
      setDndStat(dnd);
      readLock().unlock();
    }
  }

  void setDndStat(final boolean dnd) {
    long[] s = stat;
    if (s != null) s[5] = dnd ? 2 : 0;
  }

  public String getMimeType() {
    String m = mimeType;

    if (m == null) {
      String name = getName();
      String ext = Utils.getFileExtension(name);
      m = getMimeTypeFromExtension(ext);
      if (m == null) m = "";
      mimeType = m;
    }

    return m;
  }

  public boolean isVideo() {
    return type() == 1;
  }

  public boolean isAudio() {
    return type() == 2;
  }

  public boolean isMedia() {
    return isVideo() || isAudio();
  }

  private byte type() {
    byte t = type;

    if (t == -1) {
      String mime = getMimeType();

      if (mime.startsWith("video/")) {
        t = 1;
      } else if (mime.startsWith("audio/")) {
        t = getName().endsWith(".m3u") ? (byte) 22 : (byte) 2;
      } else if (mime.startsWith("image/")) {
        t = 3;
      } else if (mime.startsWith("text/")) {
        t = 4;
      } else {
        String ext = Utils.getFileExtension(getFullName());

        if (ext != null) {
          switch (ext) {
            case "ogg":
            case "opus":
              t = 2;
              break;
            default:
              t = 0;
          }
        } else {
          t = 0;
        }
      }

      type = t;
    }

    return t;
  }

  public MediaInfo getMediaInfo() {
    MediaInfo i = mediaInfo;

    if ((i == null) && (isComplete())) {
      try {
        String path = findLocation();

        if (path != null) {
          MediaMetadataRetriever r = new MediaMetadataRetriever();
          r.setDataSource(path);
          mediaInfo = i = new MediaInfo(r);
        }
      } catch (NoSuchTorrentException ex) {
        getFs().reportNoSuchTorrent(ex);
      }
    }

    return i;
  }

  public Uri getHttpUri() {
    try {
      String fileName = getName();
      String fileExt = getFileExtension(fileName);
      String hash = getTorrent().getHashString();
      int idx = getIndex();
      HttpServer http = getTransmission().getHttpServer();
      return TorrentHandler.createUri(http.getHostName(), http.getPort(), hash, idx, fileExt);
    } catch (Exception ex) {
      Utils.err(getClass().getName(), ex, "Failed to create HTTP uri: %s", this);
      return null;
    }
  }

  public boolean open(Activity a, View v) {
    Uri uri = getHttpUri();

    if (uri == null) {
      showErr(v, R.string.err_failed_to_open_torrent_file, getName());
      return false;
    } else {
      Utils.openUri(a, uri, getMimeType());
      return true;
    }
  }

  public int read(byte[] dst, long off, int len, int sleep, int retries,
                  int increasePriorityMargin, Supplier<Boolean> breakCondition, String logTag)
      throws IllegalStateException, NoSuchTorrentException, TimeoutException, IOException {
    long[] stat = stat(false);
    long pieceLength = stat[0];
    long fileOffset = stat[2];
    long torrentOff = fileOffset + off;
    long pieceIdx = torrentOff / pieceLength;

    off = (torrentOff - (pieceIdx * pieceLength));
    len = Math.min(len, (int) (pieceLength - off));

    if (!waitForPiece(pieceIdx, sleep, retries, increasePriorityMargin, breakCondition, logTag)) {
      return -1;
    }

    getTorrent().getPiece(pieceIdx, dst, (int) off, len);
    return len;
  }

  public boolean waitForPiece(long pieceIdx, int sleep, int retries,
                              int increasePriorityMargin,
                              Supplier<Boolean> breakCondition, String logTag)
      throws IllegalStateException, NoSuchTorrentException, TimeoutException {
    long stat[] = stat(false);
    if (complete(stat)) return true;
    if (dnd(stat)) throw new IllegalArgumentException("File is unwanted for download: " + this);
    int idx = (int) (pieceIdx - stat[3]);
    int fieldIdx = (idx / 64) + 6;
    int bitIdx = (idx % 64);
    long field = stat[fieldIdx];
    int retry = 0;

    if (bitSet(field, bitIdx)) {
      verifyPiece(pieceIdx, logTag);
      return true;
    }

    increasePriority(pieceIdx, increasePriorityMargin, logTag);
    stat = stat(true);
    if (complete(stat)) return true;
    field = stat[fieldIdx];

    while (!bitSet(field, bitIdx)) {
      if (retry == retries) {
        throw new TimeoutException("Piece " + pieceIdx + " has not been downloaded in " +
            (sleep * retries / 1000) + " seconds. " + this);
      }

      if (breakCondition.get()) return false;

      if (Utils.isDebugEnabled()) {
        Utils.debug(logTag, "Waiting for piece %d, stat: %s", pieceIdx, Arrays.toString(stat));
      }

      try {
        Thread.sleep(sleep);
        retry++;
      } catch (InterruptedException e) {
        return false;
      }

      stat = stat(true);
      if (complete(stat)) return true;
      field = stat[fieldIdx];
    }

    verifyPiece(pieceIdx, logTag);
    return true;
  }

  private static boolean bitSet(long field, int bit) {
    return ((1L << bit) & field) != 0;
  }

  private void increasePriority(long pieceIdx, int increasePriorityMargin, String logTag)
      throws IllegalStateException, NoSuchTorrentException {
    if (increasePriorityMargin != -1) {
      long toPieceIdx = Math.min(getLastPieceIndex(), pieceIdx + increasePriorityMargin);

      if (Utils.isDebugEnabled()) {
        Utils.debug(logTag, "Increasing priority of pieces %d-%d", pieceIdx, toPieceIdx);
      }

      getTorrent().setPiecesHiPri(pieceIdx, toPieceIdx);
    }
  }

  @Override
  public String toString() {
    return "TorrentFile: torrent=" + getTorrent() + ", fileIndex=" + getIndex();
  }

  @SuppressWarnings("UnusedAssignment")
  private void verifyPiece(long pieceIdx, String logTag) throws IllegalStateException, NoSuchTorrentException {
    if (!VERIFY_PIECE || (pieceIdx == getLastPieceIndex())) return;
    MessageDigest md;

    try {
      md = MessageDigest.getInstance("SHA-1");
    } catch (NoSuchAlgorithmException e) {
      Utils.warn(logTag, e, "SHA-1 is not supported");
      return;
    }

    byte[] buf = new byte[(int) getPieceLength()];

    try {
      getTorrent().getPiece(pieceIdx, buf, 0, buf.length);
    } catch (IOException ex) {
      Utils.warn(logTag, ex, "Failed to verify piece %d, stat: %s", pieceIdx,
          Arrays.toString(stat));
      return;
    }

    md.update(buf);
    byte[] digest = md.digest();
    byte[] pieceHash = new byte[Native.hashLength()];
    getTorrent().getPieceHash(pieceIdx, pieceHash);

    if (!Arrays.equals(pieceHash, digest)) {
      Utils.warn(logTag, "Invalid hash of piece %d: expected: %s, actual: %s, stat: %s",
          pieceIdx, hashBytesToString(pieceHash), hashBytesToString(digest), Arrays.toString(stat));
    }
  }

  boolean statLoaded() {
    return stat != null;
  }

  private long[] stat(boolean force) throws IllegalStateException, NoSuchTorrentException {
    long[] s = stat;

    if (force || (s == null)) {
      readLock().lock();
      try {
        checkValid();
        Torrent tor = getTorrent();
        s = Native.torrentGetFileStat(tor.getSessionId(), tor.getTorrentId(), getIndex(), stat);
        stat = s = complete(s) ? Arrays.copyOf(s, 6) : s;
      } finally {
        readLock().unlock();
      }
    }

    return s;
  }

  private static boolean complete(long[] stat) {
    return stat[5] == 1;
  }

  private static boolean dnd(long[] stat) {
    return stat[5] == 2;
  }

  private Lock readLock() {
    return getTransmission().readLock();
  }

  private void checkValid() throws IllegalStateException {
    torrent.checkValid();
  }
}
