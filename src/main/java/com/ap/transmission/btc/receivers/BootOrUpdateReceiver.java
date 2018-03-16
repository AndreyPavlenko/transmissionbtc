package com.ap.transmission.btc.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.ap.transmission.btc.Prefs;

import static android.content.Intent.ACTION_BOOT_COMPLETED;
import static android.content.Intent.ACTION_MY_PACKAGE_REPLACED;
import static com.ap.transmission.btc.services.TransmissionService.start;

/**
 * @author Andrey Pavlenko
 */
public class BootOrUpdateReceiver extends BroadcastReceiver {
  @Override
  public void onReceive(Context context, Intent intent) {
    String a = intent.getAction();

    if (ACTION_BOOT_COMPLETED.equals(a)) {
      Prefs p = new Prefs(context);
      if (p.isStartOnBoot()) start(context, null, p.getBootDelay());
    } else if (ACTION_MY_PACKAGE_REPLACED.equals(a)) {
      if (new Prefs(context).isStartOnBoot()) start(context, null);
    }
  }
}
