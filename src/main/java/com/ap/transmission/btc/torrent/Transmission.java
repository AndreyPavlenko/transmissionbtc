package com.ap.transmission.btc.torrent;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.FileObserver;
import android.support.annotation.Nullable;

import com.ap.transmission.btc.EncrMode;
import com.ap.transmission.btc.Native;
import com.ap.transmission.btc.PowerLock;
import com.ap.transmission.btc.Prefs;
import com.ap.transmission.btc.Utils;
import com.ap.transmission.btc.func.Promise;
import com.ap.transmission.btc.http.HttpServer;
import com.ap.transmission.btc.http.SimpleHttpServer;
import com.ap.transmission.btc.http.handlers.upnp.DescriptorHandler;
import com.ap.transmission.btc.receivers.ConnectivityChangeReceiver;
import com.ap.transmission.btc.services.TransmissionService;
import com.ap.transmission.btc.ssdp.SsdpServer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.ap.transmission.btc.Utils.configureProxy;
import static com.ap.transmission.btc.Utils.copyAssets;
import static com.ap.transmission.btc.Utils.debug;
import static com.ap.transmission.btc.Utils.err;
import static com.ap.transmission.btc.Utils.info;
import static com.ap.transmission.btc.Utils.mkdirs;
import static com.ap.transmission.btc.torrent.Transmission.AddTorrentResult.DUPLICATE;
import static com.ap.transmission.btc.torrent.Transmission.AddTorrentResult.NOT_STARTED;
import static com.ap.transmission.btc.torrent.Transmission.AddTorrentResult.OK;
import static com.ap.transmission.btc.torrent.Transmission.AddTorrentResult.OK_DELETE;
import static com.ap.transmission.btc.torrent.Transmission.AddTorrentResult.PARSE_ERR;

/**
 * @author Andrey Pavlenko
 */
public class Transmission {
  private static final byte STATE_STOPPED = 0;
  private static final byte STATE_STARTING = -1;
  private static final byte STATE_STOPPING = -2;
  private static final String TAG = Transmission.class.getName();
  private static final String SETTINGS_FILE = "settings.json";
  private final ReadWriteLock lock = new ReentrantReadWriteLock();
  private final Prefs prefs;
  private List<Watcher> watchers;
  private PowerLock powerLock;
  private SsdpServer ssdpServer;
  private volatile List<Long> semaphores;
  private volatile HttpServer httpServer;
  private volatile TorrentFs torrentFs;
  private volatile ExecutorService executor;
  private volatile ScheduledExecutorService scheduler;
  private volatile long session = STATE_STOPPED;
  private volatile byte suspended;

  public Transmission(Prefs prefs) {
    this.prefs = prefs;
  }

  public static String getVersion() {
    return Native.transmissionVersion();
  }

  public Prefs getPrefs() {
    return prefs;
  }

  public Context getContext() {
    return getPrefs().getContext();
  }

  public Lock readLock() {
    return lock.readLock();
  }

  public Lock writeLock() {
    return lock.writeLock();
  }

  long getSession() {
    return session;
  }

  public TorrentFs getTorrentFs() {
    TorrentFs fs = torrentFs;

    if (fs == null) {
      throw new IllegalStateException("Transmission is not running");
    }

    return fs;
  }

  public HttpServer getHttpServer() throws IllegalStateException, IOException {
    HttpServer s = httpServer;

    if (s == null) {
      writeLock().lock();
      try {
        if ((s = httpServer) == null) {
          checkRunning();
          httpServer = s = new SimpleHttpServer(this);
          s.start();
        }
      } finally {
        writeLock().unlock();
      }
    }

    return s;
  }

  public void start() throws IOException {
    if (isRunning()) return;

    writeLock().lock();
    session = STATE_STARTING;
    boolean ok = false;

    try {
      Context ctx = getContext();
      boolean suspend = false;
      File dataDir = new File(ctx.getApplicationInfo().dataDir);
      File configDir = new File(prefs.getSettingsDir());
      File downloadDir = new File(prefs.getDownloadDir());
      File webDir = new File(dataDir, "web");
      File settings = new File(configDir, SETTINGS_FILE);
      File tmp = new File(dataDir, "tmp");
      mkdirs(configDir, downloadDir, tmp);
      copyAssets(ctx.getAssets(), "web", dataDir, true);

      if (prefs.isWifiEthOnly()) {
        suspend = !Utils.isWifiEthActive(ctx, prefs.getWifiSsid());
        ConnectivityChangeReceiver.register(ctx);
      }

      Native.envSet("TMP", tmp.getAbsolutePath());
      Native.envSet("TRANSMISSION_WEB_HOME", webDir.getAbsolutePath());
      increaseSoBuf();
      configureProxy(prefs);

      session = Native.transmissionStart(configDir.getAbsolutePath(),
          downloadDir.getAbsolutePath(), prefs.getEncryptionMode().ordinal(),
          prefs.isRpcEnabled(), prefs.getRpcPort(),
          prefs.isRpcAuthEnabled(), prefs.getRpcUsername(), prefs.getRpcPassword(),
          prefs.isRpcWhitelistEnabled(), prefs.getRpcWhitelist(), settings.exists(),
          prefs.isSeqDownloadEnabled(), suspend);
      suspended = (byte) (suspend ? 1 : 0);
      debug(TAG, "Session created: %d", session);
      torrentFs = new TorrentFs(this, session);

      startWatchers();
      startUpnp();
      if (hasDownloadingTorrents()) wakeLock();

      // Handle callbacks in a separate thread to avoid dead locks
      Native.transmissionSetRpcCallbacks(new Runnable() {
        @Override
        public void run() {
          getExecutor().submit(new Runnable() {
            @Override
            public void run() {
              torrentAddedOrChanged();
            }
          });
        }
      }, new Runnable() {
        @Override
        public void run() {
          getExecutor().submit(new Runnable() {
            @Override
            public void run() {
              torrentRemoved();
            }
          });
        }
      }, new Runnable() {
        @Override
        public void run() {
          getExecutor().submit(new Runnable() {
            @Override
            public void run() {
              sessionChanged();
            }
          });
        }
      }, new Runnable() {
        @Override
        public void run() {
          getExecutor().submit(new Runnable() {
            @Override
            public void run() {
              cheduledAltSpeed();
            }
          });
        }
      });

      ok = true;
    } finally {
      writeLock().unlock();
      if (!ok) stop();
    }
  }

  private void startWatchers() {
    if (prefs.isWatchDirEnabled()) {
      int interval = prefs.getWatchInterval();
      watchers = new ArrayList<>();

      for (Map.Entry<String, String> e : prefs.getWatchDirs().entrySet()) {
        startWatcher(e.getKey(), e.getValue());
      }

      if (interval > 0) {
        ScheduledExecutorService sched = getScheduler();
        sched.scheduleWithFixedDelay(new Runnable() {
          @Override
          public void run() {
            readLock().lock();
            try {
              if (!isRunning() || (watchers == null)) return;
              for (Watcher w : watchers) w.scan();
            } finally {
              readLock().unlock();
            }
          }
        }, interval, interval, TimeUnit.SECONDS);
      }
    }
  }

  private void startWatcher(String watchDir, String downloadDir) {
    File wd = new File(watchDir);
    mkdirs(wd);
    Watcher w = new Watcher(wd, new File(downloadDir));
    w.startWatching();
    watchers.add(w);
  }

  private void startUpnp() {
    if (!prefs.isUpnpEnabled()) return;

    try {
      getHttpServer();
    } catch (IOException ex) {
      err(TAG, ex, "Failed to start HTTP Server");
      return;
    }

    try {
      ssdpServer = new SsdpServer(this,
          httpServer.getAddress() + DescriptorHandler.PATH);
      ssdpServer.start();
    } catch (IOException ex) {
      err(TAG, ex, "Failed to start SSDP server, SSDP NOTIFY will be sent every 60 seconds");
    }

    getScheduler().scheduleWithFixedDelay(new Runnable() {
      @Override
      public void run() {
        ssdpServer.sendNotify();
      }
    }, 0, 1, TimeUnit.MINUTES);
  }

  public void stop() {
    long s = session;
    if (s <= 0) return;

    writeLock().lock();
    try {
      if ((s = session) <= 0) return;
      session = STATE_STOPPING;

      try {
        if (semaphores != null) for (Long sem : semaphores) Native.semPost(sem);
        if (watchers != null) for (Watcher w : watchers) w.stopWatching();
        ConnectivityChangeReceiver.unregister(getContext());
        Native.transmissionSetRpcCallbacks(null, null, null, null);
        Utils.close(httpServer, ssdpServer);
        stopExecutor();
        stopSheduler();

        debug(TAG, "Closing session: %d", s);
        File configDir = new File(prefs.getSettingsDir());
        mkdirs(configDir);
        Native.transmissionStop(s, configDir.getAbsolutePath());
      } finally {
        session = STATE_STOPPED;
        torrentFs = null;
        httpServer = null;
        ssdpServer = null;
        watchers = null;
        executor = null;
        scheduler = null;
        semaphores = null;
        suspended = 0;
        wakeUnlock();
      }
    } finally {
      writeLock().unlock();
    }
  }

  public boolean isRunning() {
    return session > 0;
  }

  @SuppressWarnings("unused")
  public boolean isStarting() {
    return session == STATE_STARTING;
  }

  @SuppressWarnings("unused")
  public boolean isStopping() {
    return session == STATE_STOPPING;
  }

  public boolean isStopped() {
    return session == STATE_STOPPED;
  }

  @SuppressLint("StaticFieldLeak")
  public void suspend(final boolean suspend, final boolean byUser, final Runnable callback) {
    checkRunning();
    new AsyncTask<Void, Integer, Void>() {

      @Override
      protected Void doInBackground(Void... voids) {
        writeLock().lock();
        try {
          if (!isRunning()) return null;
          debug(TAG, "Suspending: %s, by user: %s", suspend, byUser);
          Native.transmissionSuspend(session, suspend);
          suspended = (byte) (!suspend ? 0 : byUser ? 2 : 1);
        } finally {
          writeLock().unlock();
        }

        return null;
      }

      @Override
      protected void onPostExecute(Void aVoid) {
        TransmissionService.updateNotification();
        if (callback != null) callback.run();
      }
    }.execute();
  }

  public boolean isSuspended() {
    return suspended != 0;
  }

  public boolean isSuspendedByUser() {
    return suspended == 2;
  }

  public boolean hasDownloadingTorrents() {
    readLock().lock();
    try {
      return isRunning() && Native.transmissionHasDownloadingTorrents(session);
    } finally {
      readLock().unlock();
    }
  }

  public AddTorrentResult addTorrent(File torrentFile, File downloadDir,
                                     @Nullable int[] unwantedIndexes,
                                     @Nullable byte[] returnMeTorrentHash,
                                     boolean delete, boolean sequential,
                                     int retries, int delay) throws InterruptedException {
    if (!isRunning()) return NOT_STARTED;
    String path = torrentFile.getAbsolutePath();
    String downloadPath = downloadDir.getAbsolutePath();
    info(TAG, "Adding new torrent file: %s", path);
    mkdirs(downloadDir);

    for (int i = 0; i < retries + 1; i++) {
      int result;

      readLock().lock();
      try {
        if (!isRunning()) {
          info(TAG, "Transmission is not running - ignoring: %s", path);
          return NOT_STARTED;
        }

        result = Native.torrentAdd(session, path, downloadPath, delete, sequential,
            unwantedIndexes, returnMeTorrentHash);
      } finally {
        readLock().unlock();
      }

      switch (result) {
        case 2:
          info(TAG, "Duplicate torrent - ignoring: %s", torrentFile);
          torrentAddedOrChanged();
          return DUPLICATE;
        case 0:
          torrentAddedOrChanged();
          return OK;
        case 3:
          torrentAddedOrChanged();
          return OK_DELETE;
        case 1:
          Thread.sleep(delay);
      }
    }

    err(TAG, "Failed to parse torrent file: %s", torrentFile);
    return PARSE_ERR;
  }

  private void torrentAddedOrChanged() {
    debug(TAG, "torrentAddedOrChanged()");
    TorrentFs fs = torrentFs;
    if (fs != null) fs.reset();
    wakeLock();
  }

  private void torrentRemoved() {
    debug(TAG, "torrentAddedOrChanged()");
    TorrentFs fs = torrentFs;
    if (fs != null) fs.reset();
  }

  private void sessionChanged() {
    debug(TAG, "torrentAddedOrChanged()");
    readLock().lock();
    try {
      if (!isRunning()) return;
      int encrMode = Native.transmissionGetEncryptionMode(session);

      if (encrMode != prefs.getEncryptionMode().ordinal()) {
        prefs.setEncryptionMode(EncrMode.get(encrMode));
      }
    } finally {
      readLock().unlock();
    }
  }

  private void cheduledAltSpeed() {
    debug(TAG, "Alt speed changed by timer");
    wakeLock();
  }

  public Promise<Void> magnetToTorrent(final Uri magnetLink, final File destTorrentPath,
                                       final int timeout, final boolean[] enqueue) {
    return new Promise<Void>() {
      private final long sem = Native.semCreate();

      {
        List<Long> semaphores = Transmission.this.semaphores;

        if (semaphores == null) {
          writeLock().lock();
          try {
            if ((semaphores = Transmission.this.semaphores) == null) {
              Transmission.this.semaphores = semaphores = new Vector<>();
            }
          } finally {
            writeLock().unlock();
          }
        }

        semaphores.add(sem);
      }

      @Override
      public Void get() throws Throwable {
        readLock().lock();
        try {
          checkRunning();
          TorrentFs fs = torrentFs;
          if (fs != null) fs.reset();
          Native.torrentMagnetToTorrentFile(session, sem, magnetLink.toString(),
              destTorrentPath.getAbsolutePath(), timeout, enqueue);
          return null;
        } finally {
          readLock().unlock();
        }
      }

      @Override
      public synchronized void cancel() {
        Native.semPost(sem);
        List<Long> semaphores = Transmission.this.semaphores;
        if (semaphores != null) semaphores.remove(sem);
      }

      @Override
      protected void finalize() {
        List<Long> semaphores = Transmission.this.semaphores;
        if (semaphores != null) semaphores.remove(sem);
        Native.semDestroy(sem);
      }
    };
  }

  private void increaseSoBuf() {
    if (!prefs.isIncreaseSoBuf()) return;
    String file = "scripts/set_so_buf.sh";
    debug(TAG, "Executing su -c %s", file);
    AssetManager amgr = getContext().getAssets();
    InputStream in = null;

    try {
      in = amgr.open(file, AssetManager.ACCESS_STREAMING);
      int status = Utils.su(3000, in);
      if (status != 0) err(TAG, "su -c %s failed with exit code %d", file, status);
    } catch (IOException ex) {
      err(TAG, ex, "Failed to open asset: %s", file);
    } finally {
      if (in != null) try { in.close(); } catch (IOException ignore) {}
    }
  }

  public ExecutorService getExecutor() {
    ExecutorService exec = executor;

    if (exec == null) {
      writeLock().lock();
      try {
        if ((exec = executor) == null) {
          checkRunning();
          executor = exec = new ThreadPoolExecutor(0,
              30, 60L, TimeUnit.SECONDS,
              new SynchronousQueue<Runnable>());
        }
      } finally {
        writeLock().unlock();
      }
    }

    return exec;
  }

  private void stopExecutor() {
    ExecutorService exec = executor;
    if (exec == null) return;
    executor = null;

    try {
      exec.shutdownNow();
      exec.awaitTermination(30, TimeUnit.SECONDS);
    } catch (InterruptedException ignore) {}
  }

  public ScheduledExecutorService getScheduler() {
    ScheduledExecutorService sched = scheduler;

    if (sched == null) {
      writeLock().lock();
      try {
        if ((sched = scheduler) == null) {
          checkRunning();
          scheduler = sched = Executors.newScheduledThreadPool(1);
        }
      } finally {
        writeLock().unlock();
      }
    }

    return sched;
  }

  private void stopSheduler() {
    ScheduledExecutorService sched = scheduler;
    if (sched == null) return;
    scheduler = null;

    try {
      sched.shutdownNow();
      sched.awaitTermination(30, TimeUnit.SECONDS);
    } catch (InterruptedException ignore) {}
  }

  @SuppressLint("WakelockTimeout")
  private void wakeLock() {
    writeLock().lock();
    try {
      if (powerLock != null || !isRunning()) return;
      PowerLock pl = PowerLock.newLock(getContext());
      if (pl == null) return;
      pl.acquire();
      powerLock = pl;
      debug(TAG, "WakeLock acquired");

      final ScheduledFuture<?> f[] = new ScheduledFuture[1];
      f[0] = getScheduler().scheduleWithFixedDelay(new Runnable() {
        @Override
        public void run() {
          if (!hasDownloadingTorrents()) {
            writeLock().lock();
            try {
              if (!isRunning()) {
                wakeUnlock();
                f[0].cancel(false);
              } else if (!Native.transmissionHasDownloadingTorrents(session)) {
                debug(TAG, "No active downloads - releasing WakeLock");
                wakeUnlock();
                f[0].cancel(false);
              }
            } finally {
              writeLock().unlock();
            }
          }
        }
      }, 1, 1, TimeUnit.MINUTES);
    } finally {
      writeLock().unlock();
    }
  }

  private void wakeUnlock() {
    writeLock().lock();
    try {
      if (powerLock == null) return;
      powerLock.release();
      powerLock = null;
      debug(TAG, "WakeLock released");
    } finally {
      writeLock().unlock();
    }
  }

  private final class Watcher extends FileObserver {
    private final File dir;
    private final File downloadDir;

    private Watcher(File dir, File downloadDir) {
      super(dir.getAbsolutePath(), FileObserver.CREATE);
      this.dir = dir;
      this.downloadDir = downloadDir;
    }

    @Override
    public void startWatching() {
      info(TAG, "Start watching directory: %s", dir);
      mkdirs(dir);
      scan();
      super.startWatching();
    }

    void scan() {
      String[] files = dir.list();
      if (files != null) for (String f : files) add(f);
    }

    @Override
    public void onEvent(int event, @Nullable String path) {
      add(path);
    }

    private void add(String path) {
      if ((path == null) || !path.endsWith(".torrent")) return;
      File f = new File(dir, path);
      AddTorrentResult result;

      try {
        result = addTorrent(f, downloadDir, null, null,
            false, prefs.isSeqDownloadEnabled(), 10, 1000);
      } catch (InterruptedException ex) {
        err(TAG, ex, "Failed to add torrent file: %s", f);
        return;
      }

      if (result == OK) {
        File renameTo = new File(dir, path + ".added");
        if (!f.renameTo(renameTo)) err(TAG, "Failed to rename file to: %s", renameTo);
      } else if (result != NOT_STARTED) {
        if (!f.delete()) err(TAG, "Failed to delete file: %s", f);
      }
    }
  }

  void checkRunning() {
    if (!isRunning()) {
      throw new IllegalStateException("Transmission is not running");
    }
  }

  public enum AddTorrentResult {
    OK, PARSE_ERR, DUPLICATE, OK_DELETE, NOT_STARTED
  }
}
