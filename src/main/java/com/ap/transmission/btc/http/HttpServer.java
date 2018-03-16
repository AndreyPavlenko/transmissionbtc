package com.ap.transmission.btc.http;

import com.ap.transmission.btc.torrent.Transmission;

import java.io.Closeable;
import java.io.IOException;

/**
 * @author Andrey Pavlenko
 */
public interface HttpServer extends Closeable {

  void start() throws IOException;

  void stop();

  boolean isRunning();

  Transmission getTransmission();

  int getPort();

  String getHostName();

  String getAddress();
}
