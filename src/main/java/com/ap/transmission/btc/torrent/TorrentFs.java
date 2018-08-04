package com.ap.transmission.btc.torrent;

import com.ap.transmission.btc.Native;
import com.ap.transmission.btc.NaturalOrderComparator;
import com.ap.transmission.btc.Utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;

import static com.ap.transmission.btc.Native.torrentGetError;
import static com.ap.transmission.btc.torrent.TorrentStat.Status.ERROR;

/**
 * @author Andrey Pavlenko
 */
public class TorrentFs implements TorrentItemContainer {
  private final Transmission transmission;
  private final long sessionId;
  private final ConcurrentHashMap<String, Torrent> cache = new ConcurrentHashMap<>();
  private final AtomicInteger updateId = new AtomicInteger(Math.abs(new Random().nextInt()));
  private volatile List<TorrentItem> torrents;
  private volatile long[] stat;

  public TorrentFs(Transmission transmission, long sessionId) {
    this.transmission = transmission;
    this.sessionId = sessionId;
  }

  public Transmission getTransmission() {
    return transmission;
  }

  public static List<TorrentItem> sortByName(Collection<TorrentItem> items, boolean skipDnd) {
    List<TorrentItem> l = new ArrayList<>(items.size());

    for (TorrentItem i : items) {
      if (skipDnd && i.isDnd()) continue;
      l.add(i);
    }

    final NaturalOrderComparator cmp = new NaturalOrderComparator();
    Collections.sort(l, new Comparator<TorrentItem>() {
      @Override
      public int compare(TorrentItem i1, TorrentItem i2) {
        if (i1 instanceof TorrentItemContainer) {
          if (!(i2 instanceof TorrentItemContainer)) {
            return -1;
          }
        } else if (i2 instanceof TorrentItemContainer) {
          return 1;
        }

        return cmp.compare(i1.getName(), i2.getName());
      }
    });
    return l;
  }

  @Override
  public List<TorrentItem> ls() {
    List<TorrentItem> ls = torrents;

    if (ls == null) {
      String[] names;

      readLock().lock();
      try {
        if (!isValid()) return Collections.emptyList();
        names = Native.transmissionListTorrentNames(getSessionId());
      } finally {
        readLock().unlock();
      }

      if (names == null) {
        ls = Collections.emptyList();
      } else {
        ls = new ArrayList<>(names.length);

        for (String n : names) {
          String[] s = n.split(" ", 3);
          Torrent tor = new Torrent(this, Integer.parseInt(s[0]), s[1], s[2]);
          ls.add(tor);
          cache.put(tor.getHashString(), tor);
        }

        ls = Collections.unmodifiableList(ls);
      }

      for (int i = updateId.get(); ; i = updateId.get()) {
        int n = i + 1;
        if (n <= 0) n = 1;
        if (updateId.compareAndSet(i, n)) break;
      }

      torrents = ls;
    }

    return ls;
  }

  @Override
  public String getName() {
    return "Transmission BTC";
  }

  @Override
  public String getId() {
    return "0";
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
    return null;
  }

  @Override
  public TorrentFs getFs() {
    return this;
  }

  public int getUpdateId() {
    return updateId.get();
  }

  void updateStat() {
    long[] s = stat;
    readLock().lock();
    try {
      checkValid();
      @SuppressWarnings("unchecked") List<Torrent> torrents = (List) ls();
      long sid = getSessionId();
      stat = s = Native.torrentStatBrief(sid, s);

      for (int i = 0; i < s.length; i += 10) {
        for (Torrent t : torrents) {
          if (t.getTorrentId() == s[i]) {
            TorrentStat ts = t.getStat();
            if (ts == null) t.setStat(ts = new TorrentStat(s, i, null));
            else ts.update(s, i, null);
            if (ts.getStatus() == ERROR)
              ts.update(s, i, torrentGetError(sid, t.getTorrentId()));
            break;
          }
        }
      }
    } finally {
      readLock().unlock();
    }
  }

  void reset() {
    cache.clear();
    torrents = null;
    stat = null;
  }

  public void reportNoSuchTorrent(NoSuchTorrentException ex) {
    if (Utils.isDebugEnabled()) Utils.debug(getClass().getName(), ex, "NoSuchTorrent reported");
    reset();
  }

  @SuppressWarnings("unused")
  void removed(Torrent tor) {
    reset();
  }

  long getSessionId() {
    return sessionId;
  }

  public Torrent findTorrent(String torrentHashString)
      throws IllegalStateException, NoSuchTorrentException {
    if (!isHashString(torrentHashString)) return null;
    Torrent tor = cache.get(torrentHashString);

    if (tor == null) {
      tor = findTorrent(Torrent.hashStringToBytes(torrentHashString), torrentHashString);
      Torrent t = cache.putIfAbsent(torrentHashString, tor);
      return (t == null) ? tor : t;
    }

    return tor;
  }

  private Torrent findTorrent(byte[] torrentHash, String torrentHashString)
      throws IllegalStateException, NoSuchTorrentException {
    readLock().lock();
    try {
      checkValid();
      int id = Native.torrentFindByHash(getSessionId(), torrentHash);
      String name = Native.torrentGetName(getSessionId(), id);
      return new Torrent(this, id, torrentHashString, name);
    } finally {
      readLock().unlock();
    }
  }

  public TorrentItem findItem(String id) {
    TorrentItem item = null;

    try {
      String[] s = id.split("-");

      if (s.length == 1) {
        return findTorrent(s[0]);
      } else if (s.length == 2) {
        Torrent tor = findTorrent(s[0]);
        boolean f = s[1].startsWith("f");
        int idx = Integer.parseInt(s[1].substring(1));
        item = f ? tor.getFile(idx) : tor.getDir(idx);
      }
    } catch (NoSuchTorrentException ex) {
      reportNoSuchTorrent(ex);
    }

    return item;
  }

  Lock readLock() {
    return getTransmission().readLock();
  }

  boolean isValid() {
    Transmission tr = getTransmission();
    return (sessionId == tr.getSession()) && tr.isRunning();
  }

  void checkValid() throws IllegalStateException {
    if (!isValid()) throw new IllegalStateException("Session is not valid");
  }

  private static boolean isHashString(String s) {
    return (s.length() == Native.hashLength() * 2);
  }
}
