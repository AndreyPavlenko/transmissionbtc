package com.ap.transmission.btc;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;

import static android.content.Context.POWER_SERVICE;
import static android.content.Context.WIFI_SERVICE;
import static android.net.wifi.WifiManager.WIFI_MODE_FULL;
import static android.os.PowerManager.PARTIAL_WAKE_LOCK;
import static com.ap.transmission.btc.Utils.debug;

/**
 * @author Andrey Pavlenko
 */
public class PowerLock {
  private static final String TAG = "TransmissionLock";
  private final WakeLock wakeLock;
  private final WifiLock wifiLock;

  private PowerLock(PowerManager.WakeLock wakeLock, WifiManager.WifiLock wifiLock) {
    this.wakeLock = wakeLock;
    this.wifiLock = wifiLock;
  }

  @SuppressLint("WakelockTimeout")
  public void acquire() {
    if (wakeLock != null) {
      debug(TAG, "Acquiring CPU lock");
      wakeLock.acquire();
    }
    if (wifiLock != null) {
      debug(TAG, "Acquiring WiFi lock");
      wifiLock.acquire();
    }
  }

  public void release() {
    if (wakeLock != null) {
      debug(TAG, "Releasing CPU lock");
      wakeLock.release();
    }
    if (wifiLock != null) {
      debug(TAG, "Releasing WiFi lock");
      wifiLock.release();
    }
  }

  public static PowerLock newLock(Context ctx) {
    PowerManager pmgr = (PowerManager) ctx.getApplicationContext().getSystemService(POWER_SERVICE);
    WifiManager wmgr = (WifiManager) ctx.getApplicationContext().getSystemService(WIFI_SERVICE);
    WakeLock wakeLock = (pmgr == null) ? null : pmgr.newWakeLock(PARTIAL_WAKE_LOCK, TAG);
    WifiLock wifiLock = (wmgr == null) ? null : wmgr.createWifiLock(WIFI_MODE_FULL, TAG);
    return ((wakeLock == null) && (wifiLock == null)) ? null : new PowerLock(wakeLock, wifiLock);
  }
}
