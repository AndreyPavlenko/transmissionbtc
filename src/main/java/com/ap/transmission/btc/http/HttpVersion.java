package com.ap.transmission.btc.http;

import android.util.Log;

/**
 * @author Andrey Pavlenko
 */
public enum HttpVersion {
  HTTP_1_0, HTTP_1_1; // Supported versions

  public static HttpVersion fromString(String s) {
    switch (s) {
      case "HTTP/1.0":
        return HTTP_1_0;
      case "HTTP/1.1":
        return HTTP_1_1;
      default:
        Log.w(HttpVersion.class.getName(), "Unsupported HTTP version: " + s);
        return null;
    }
  }

  @Override
  public String toString() {
    return "HTTP/" + name().substring(5).replace('_', '.');
  }
}
