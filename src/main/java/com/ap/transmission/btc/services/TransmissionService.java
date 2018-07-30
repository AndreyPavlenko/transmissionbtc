package com.ap.transmission.btc.services;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import com.ap.transmission.btc.Native;
import com.ap.transmission.btc.Prefs;
import com.ap.transmission.btc.R;
import com.ap.transmission.btc.Utils;
import com.ap.transmission.btc.activities.MainActivity;
import com.ap.transmission.btc.torrent.Transmission;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.WeakHashMap;

import static java.util.Collections.newSetFromMap;
import static java.util.Collections.synchronizedSet;

/**
 * @author Andrey Pavlenko
 */
public class TransmissionService extends Service {
  private static final int NOTIFICATION_ID = 1;
  private static final String DELAY = "delay";
  private static final String TAG = TransmissionService.class.getName();
  private static final Collection<Runnable> listeners = synchronizedSet(
      newSetFromMap(new WeakHashMap<Runnable, Boolean>()));
  private static volatile Transmission transmission;
  private static List<Runnable> runOnStart;
  private static List<Runnable> runOnStop;

  public static void start(Context context, Runnable callback) {
    start(context, callback, 0);
  }

  public static void start(Context context, Runnable callback, int delay) {
    boolean runNow = false;

    synchronized (TransmissionService.class) {
      Transmission t = transmission;

      if ((t != null) && t.isRunning()) {
        runNow = true;
      } else if (callback != null) {
        runOnStart(callback);
      }
    }

    if (runNow) {
      if (callback != null) callback.run();
    } else {
      Intent i = new Intent(context, TransmissionService.class);
      if (delay > 0) i.putExtra(DELAY, delay);
      context.startService(i);
    }
  }

  public static synchronized void stop(Context context, Runnable callback) {
    boolean runNow = false;

    synchronized (TransmissionService.class) {
      Transmission t = transmission;

      if ((t == null) || t.isStopped()) {
        runNow = true;
      } else if (callback != null) {
        if (runOnStop == null) runOnStop = new ArrayList<>(1);
        runOnStop.add(callback);
      }
    }

    if (runNow) {
      if (callback != null) callback.run();
    } else {
      context.stopService(new Intent(context, TransmissionService.class));
    }
  }

  public static boolean isRunning() {
    Transmission t = transmission;
    return (t != null) && t.isRunning();
  }

  public static Transmission getTransmission() {
    return transmission;
  }

  public static void addStateChangeListener(Runnable listener) {
    listeners.add(listener);
  }

  @SuppressWarnings("unused")
  public static void removeStateChangeListener(Runnable listener) {
    listeners.remove(listener);
  }

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  @Override
  public void onCreate() {
    synchronized (TransmissionService.class) {
      Native.Init.init(getApplicationContext());
      if (transmission == null) transmission = new Transmission(new Prefs(getApplicationContext()));
    }
  }

  @Override
  public void onDestroy() {
    new StopTask().execute((Void) null);
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (!isRunning()) {
      if (transmission.getPrefs().isForeground()) {
        runOnStart(new Runnable() {
          @Override
          public void run() {
            foreground();
          }
        });
      }

      final int delay = (intent == null) ? 0 : intent.getIntExtra(DELAY, 0);
      new StartTask(delay).execute((Void) null);
    }

    return START_STICKY;
  }

  private static void runOnStart(Runnable run) {
    if (runOnStart == null) runOnStart = new ArrayList<>(2);
    runOnStart.add(run);
  }

  @SuppressLint("ObsoleteSdkInt")
  private void foreground() {
    Notification n = buildNotification(getApplicationContext(), !transmission.isSuspended());
    startForeground(NOTIFICATION_ID, n);
  }

  public static void updateNotification() {
    Transmission tr = transmission;
    if ((tr == null) || !transmission.getPrefs().isForeground()) return;
    Context ctx = tr.getContext();
    NotificationManager nmgr = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
    if (nmgr == null) return;
    Notification n = buildNotification(ctx, !transmission.isSuspended());
    nmgr.notify(NOTIFICATION_ID, n);
  }

  @SuppressLint("ObsoleteSdkInt")
  static Notification buildNotification(Context ctx, boolean running) {
    String ip = Utils.getIPAddress(ctx);
    Intent i = new Intent(ctx, MainActivity.class);
    Notification.Builder b = new Notification.Builder(ctx)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(ctx.getText(R.string.app_name))
        .setContentIntent(PendingIntent.getActivity(ctx, 0, i, 0));

    if (ip == null) {
      b.setContentText(ctx.getText(running ? R.string.notif_running1 : R.string.notif_suspended1));
    } else {
      b.setContentText(String.format(ctx.getText(
          running ? R.string.notif_running2 : R.string.notif_suspended2).toString(), ip));
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationManager nmgr = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);

      if (nmgr != null) {
        NotificationChannel nc = new NotificationChannel("TransmissionBTC",
            "TransmissionBTC", NotificationManager.IMPORTANCE_LOW);
        nmgr.createNotificationChannel(nc);
        b.setChannelId("TransmissionBTC");
      }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      return b.build();
    } else {
      //noinspection deprecation
      return b.getNotification();
    }
  }

  private static final class StartTask extends AsyncTask<Void, Integer, List<Runnable>> {
    private final int delay;

    private StartTask(int delay) {
      this.delay = delay;
    }

    @Override
    protected List<Runnable> doInBackground(Void... params) {
      if (delay > 0) {
        try {
          Log.i(TAG, "Waiting " + delay + " seconds before start");
          Thread.sleep(delay * 1000);
        } catch (InterruptedException ignore) {}
      }

      Transmission t;
      List<Runnable> run;

      synchronized (TransmissionService.class) {
        t = transmission;
      }

      if (t != null) {
        try {
          Log.i(TAG, "Starting transmission service");
          t.start();
        } catch (Exception ex) {
          Log.e(TAG, "Failed to start transmission service", ex);
        }
      }

      synchronized (TransmissionService.class) {
        run = runOnStart;
        runOnStart = null;
      }

      return run;
    }

    @Override
    protected void onPostExecute(List<Runnable> run) {
      if (run != null) {
        for (Runnable r : run) r.run();
      }

      for (Runnable l : listeners) l.run();
    }
  }

  private static final class StopTask extends AsyncTask<Void, Integer, List<Runnable>> {

    @Override
    protected List<Runnable> doInBackground(Void... params) {
      Transmission t;
      List<Runnable> run;

      synchronized (TransmissionService.class) {
        t = transmission;
      }

      if (t != null) {
        Log.i(TAG, "Stopping transmission service");
        t.stop();
      }

      synchronized (TransmissionService.class) {
        run = runOnStop;
        runOnStop = null;
      }

      return run;
    }

    @Override
    protected void onPostExecute(List<Runnable> run) {
      if (run != null) {
        for (Runnable r : run) r.run();
      }

      for (Runnable l : listeners) l.run();
    }
  }
}
