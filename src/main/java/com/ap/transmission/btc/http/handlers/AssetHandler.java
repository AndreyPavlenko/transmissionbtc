package com.ap.transmission.btc.http.handlers;

import android.content.res.AssetManager;

import com.ap.transmission.btc.http.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;

/**
 * @author Andrey Pavlenko
 */
public class AssetHandler extends StaticResourceHandler {
  private final AssetManager amgr;
  private final String path;
  private final String contentType;
  private final String logTag;

  public AssetHandler(AssetManager amgr, String path, String contentType) {
    this.amgr = amgr;
    this.path = path;
    this.contentType = contentType;
    logTag = "File: " + path;
  }

  @Override
  protected Handler createHandler(HttpServer server, Socket socket) {
    return new Handler(server, socket);
  }

  private final class Handler extends StaticResourceHandler.Handler {

    protected Handler(HttpServer server, Socket socket) {
      super(logTag, server, socket);
    }

    @Override
    protected InputStream open() throws IOException {
      return amgr.open(path);
    }

    @Override
    protected String getContentType() {
      return contentType;
    }
  }
}
