package com.ap.transmission.btc.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;

import com.ap.transmission.btc.Prefs;
import com.ap.transmission.btc.Utils;
import com.ap.transmission.btc.services.TransmissionService;
import com.ap.transmission.btc.torrent.Transmission;

/**
 * @author Andrey Pavlenko
 */
public class ConnectivityChangeReceiver extends BroadcastReceiver {
  private static ConnectivityChangeReceiver instance;

  public static synchronized void register(Context context) {
    if (instance != null) return;
    IntentFilter intentFilter = new IntentFilter();
    intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
    context.registerReceiver(instance = new ConnectivityChangeReceiver(), intentFilter);
  }

  public static synchronized void unregister(Context context) {
    if (instance == null) return;
    context.unregisterReceiver(instance);
    instance = null;
  }

  @Override
  public void onReceive(Context context, Intent intent) {
    Transmission tr = TransmissionService.getTransmission();
    if ((tr == null) || !tr.isRunning()) return;
    Prefs prefs = new Prefs(context);

    if (prefs.isWifiEthOnly() && !tr.isSuspendedByUser()) {
      tr.suspend(!Utils.isWifiEthActive(prefs.getContext(), prefs.getWifiSsid()), false, null);
    }
  }
}
