package com.ap.transmission.btc.activities;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.ap.transmission.btc.Native;
import com.ap.transmission.btc.Prefs;

/**
 * @author Andrey Pavlenko
 */
public abstract class ActivityBase extends AppCompatActivity {
  private Prefs prefs;
  private ActivityResultHandler activityResultHandler;

  protected void onCreate(Bundle savedInstanceState) {
    Native.Init.init(getApplicationContext());
    prefs = new Prefs(getApplicationContext());
    super.onCreate(savedInstanceState);
  }

  public Prefs getPrefs() {
    return prefs;
  }

  public void setActivityResultHandler(ActivityResultHandler h) {
    activityResultHandler = h;
  }

  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    ActivityResultHandler h = activityResultHandler;

    if (h != null) {
      activityResultHandler = null;
      if (h.onActivityResult(requestCode, resultCode, data)) return;
    }

    super.onActivityResult(requestCode, resultCode, data);
  }
}
