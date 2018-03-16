package com.ap.transmission.btc.http;

import java.net.Socket;

/**
 * @author Andrey Pavlenko
 */
public interface RequestHandler {
  void handle(HttpServer server, Request req, Socket socket) throws Throwable;
}
