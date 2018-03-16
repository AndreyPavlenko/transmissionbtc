package com.ap.transmission.btc.http;

import android.util.Log;

/**
 * @author Andrey Pavlenko
 */
public enum Method {
  GET, POST, HEAD; // Supported methods

  public static Method fromString(String s) {
    switch (s) {
      case "GET":
        return GET;
      case "POST":
        return POST;
      case "HEAD":
        return HEAD;
      default:
        Log.w(Method.class.getName(), "Unsupported method: " + s);
        return null;
    }
  }
}
