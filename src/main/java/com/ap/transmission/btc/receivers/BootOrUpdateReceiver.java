package com.ap.transmission.btc.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;

import com.ap.transmission.btc.Prefs;

import static android.content.Intent.ACTION_BOOT_COMPLETED;
import static android.content.Intent.ACTION_MY_PACKAGE_REPLACED;
import static com.ap.transmission.btc.Utils.err;
import static com.ap.transmission.btc.services.TransmissionService.start;

/**
 * @author Andrey Pavlenko
 */
public class BootOrUpdateReceiver extends BroadcastReceiver {
  @Override
  public void onReceive(Context context, Intent intent) {
    String a = intent.getAction();
    Prefs p = new Prefs(context.getApplicationContext());

    try {
      handle(context, a, p);
    } catch (IllegalStateException ex) {
      err(getClass().getName(), ex, "Failed to handle action %s - retry in 10 seconds", a);
      new Retry(a, p).execute((Void) null);
    }
  }

  private static void handle(Context context, String a, Prefs p) {
    if (ACTION_BOOT_COMPLETED.equals(a)) {
      if (p.isStartOnBoot()) start(context, null, p.getBootDelay());
    } else if (ACTION_MY_PACKAGE_REPLACED.equals(a)) {
      if (p.isStartOnBoot()) start(context, null);
    }
  }

  private static final class Retry extends AsyncTask<Void, Integer, Void> {
    final String a;
    final Prefs p;

    Retry(String a, Prefs p) {
      this.a = a;
      this.p = p;
    }

    @Override
    protected Void doInBackground(Void... args) {
      try {
        Thread.sleep(10000);
      } catch (InterruptedException ex) {
        err(getClass().getName(), ex, "Retry task interrupted");
      }
      return null;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
      try {
        handle(p.getContext(), a, p);
      } catch (IllegalStateException ex) {
        err(getClass().getName(), ex, "Retry failed to handle action %s", a);
      }
    }
  }
}
