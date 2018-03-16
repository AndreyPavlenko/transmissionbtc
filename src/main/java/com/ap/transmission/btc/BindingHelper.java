package com.ap.transmission.btc;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.databinding.ViewDataBinding;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;

import com.ap.transmission.btc.activities.ActivityBase;
import com.ap.transmission.btc.services.TransmissionService;
import com.ap.transmission.btc.torrent.Transmission;

import java.io.IOException;

/**
 * @author Andrey Pavlenko
 */
public class BindingHelper implements OnSharedPreferenceChangeListener, Runnable {
  private final ActivityBase activity;
  private final ViewDataBinding dataBinding;
  private boolean isServiceRunning = TransmissionService.isRunning();

  public BindingHelper(ActivityBase activity, ViewDataBinding dataBinding) {
    this.activity = activity;
    this.dataBinding = dataBinding;
    activity.getPrefs().getPrefs().registerOnSharedPreferenceChangeListener(this);
    TransmissionService.addStateChangeListener(this);
  }

  public ActivityBase getActivity() {
    return activity;
  }

  public boolean and(boolean... bools) {
    for (boolean b : bools) if (!b) return false;
    return true;
  }

  public boolean isPro() {
    return Utils.isPro();
  }

  public boolean isBasic() {
    return Utils.isBasic();
  }

  public String getIp() {
    return Utils.getIPAddress(getActivity());
  }

  public boolean isServiceRunning() {
    return isServiceRunning;
  }

  public boolean isSuspended() {
    if (isServiceRunning()) {
      Transmission tr = TransmissionService.getTransmission();
      return (tr != null) && tr.isSuspended();
    }
    return false;
  }

  public void startStopService(final View... disable) {
    final boolean starting = !isServiceRunning;
    for (View v : disable) v.setEnabled(false);
    Runnable callback = new Runnable() {
      @Override
      public void run() {
        for (View v : disable) v.setEnabled(true);
        isServiceRunning = TransmissionService.isRunning();
        invalidate();

        if (starting && !isServiceRunning) {
          Utils.showErr(disable[0], R.string.err_failed_to_start_transmission);
        }
      }
    };

    if (isServiceRunning()) {
      isServiceRunning = false;
      TransmissionService.stop(activity, callback);
    } else {
      TransmissionService.start(activity, callback);
    }
  }

  public void suspend(boolean suspend, Runnable callback) {
    Transmission tr = TransmissionService.getTransmission();
    if (tr != null) tr.suspend(suspend, true, callback);
  }

  public void openUrl(String scheme, String host, int port, String path) {
    String uri = scheme + "://" + host + ':' + port + "/" + path;
    Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));

    try {
      activity.startActivity(i);
    } catch (ActivityNotFoundException ex) {
      Utils.showErr(dataBinding.getRoot(), R.string.err_browser_not_found);
    }
  }

  public void checkRoot(View v) {
    CompoundButton cb = (CompoundButton) v;
    if (!cb.isChecked()) return;

    try {
      if (Utils.su(15000, "ls") == 0) return;
    } catch (IOException ex) {
      Log.e(getClass().getName(), "checkRoot failed", ex);
    }

    cb.setChecked(false);
    Utils.showErr(dataBinding.getRoot(), R.string.err_check_root_failed);
  }

  public void addWatchDir() {
    Prefs prefs = activity.getPrefs();
    int idx = prefs.getMaxIndex(Prefs.K.WATCH_DIR);
    prefs.set(Prefs.K.WATCH_DIR, prefs.getWatchDir(), idx);
    prefs.set(Prefs.K.DOWNLOAD_DIR, prefs.getDownloadDir(), idx);
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    invalidate();
  }

  private void invalidate() {
    dataBinding.invalidateAll();
  }

  @Override
  public void run() {
    isServiceRunning = TransmissionService.isRunning();
    dataBinding.invalidateAll();
  }
}
