package com.ap.transmission.btc.activities;

import android.content.Intent;

/**
 * @author Andrey Pavlenko
 */
public interface ActivityResultHandler {
  boolean onActivityResult(int requestCode, int resultCode, Intent data);
}
