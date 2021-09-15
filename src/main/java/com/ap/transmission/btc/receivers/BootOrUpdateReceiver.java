package com.ap.transmission.btc.receivers;

import static android.content.Intent.ACTION_BOOT_COMPLETED;
import static android.content.Intent.ACTION_MY_PACKAGE_REPLACED;
import static com.ap.transmission.btc.Utils.err;
import static com.ap.transmission.btc.services.TransmissionService.start;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;

import com.ap.transmission.btc.Prefs;

/**
 * @author Andrey Pavlenko
 */
public class BootOrUpdateReceiver extends BroadcastReceiver {

  @Override
  public void onReceive(Context ctx, Intent intent) {
    String a = intent.getAction();
    Prefs p = new Prefs(ctx.getApplicationContext());
    onReceive(ctx, a, p);
  }

  private void onReceive(Context ctx, String a, Prefs p) {
    try {
      handle(ctx, a, p);
    } catch (IllegalStateException ex) {
      err(getClass().getName(), ex, "Failed to handle action %s - retrying in 10 seconds", a);
      new Handler(ctx.getMainLooper()).postDelayed(() -> onReceive(ctx, a, p), 10000L);
    }
  }

  private static void handle(Context context, String a, Prefs p) {
    if (ACTION_BOOT_COMPLETED.equals(a)) {
      if (!p.isStartOnBoot()) return;
      int delay = p.getBootDelay();

      if (delay > 0) {
        Log.i(BootOrUpdateReceiver.class.getName(), "Waiting " + delay + " seconds before start");
        new Handler(context.getMainLooper()).postDelayed(() -> start(context, null), delay * 1000L);
      } else {
        start(context, null);
      }
    } else if (ACTION_MY_PACKAGE_REPLACED.equals(a)) {
      if (p.isStartOnBoot()) start(context, null);
    }
  }
}
