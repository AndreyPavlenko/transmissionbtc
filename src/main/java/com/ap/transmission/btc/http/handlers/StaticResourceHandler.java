package com.ap.transmission.btc.http.handlers;

import com.ap.transmission.btc.Utils;
import com.ap.transmission.btc.http.HttpServer;
import com.ap.transmission.btc.http.Method;
import com.ap.transmission.btc.http.Range;
import com.ap.transmission.btc.http.Request;
import com.ap.transmission.btc.http.RequestHandler;
import com.ap.transmission.btc.http.Response;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;

/**
 * @author Andrey Pavlenko
 */
public abstract class StaticResourceHandler implements RequestHandler {
  private int contentLen = -1;

  protected abstract Handler createHandler(HttpServer server, Socket socket);

  @Override
  public void handle(HttpServer server, Request req, Socket socket) {
    createHandler(server, socket).handle(req);
  }

  protected abstract class Handler extends HandlerBase {

    protected Handler(String logTag, HttpServer server, Socket socket) {
      super(logTag, server, socket);
    }

    protected abstract InputStream open() throws IOException;

    protected abstract String getContentType();

    @Override
    protected void doHandle(Request req) throws IOException {
      InputStream in;
      OutputStream out = null;

      try {
        in = open();
      } catch (IOException ex) {
        fail(Response.NotFound.instance, ex, "Failed to open file");
        return;
      }

      if (in == null) {
        fail(Response.NotFound.instance, "File not found");
        return;
      }

      try {
        ByteBuffer buf = null;
        int off = 0;
        int len = contentLen;
        Range range = req.getRange();

        if (contentLen == -1) {
          buf = Utils.readAll(in, 8192, Integer.MAX_VALUE);
          contentLen = len = buf.remaining();
        }

        if (range == null) {
          out = responseOk(getContentType(), len, true);
        } else {
          range.allign(len);

          if (range.isSatisfiable(len)) {
            out = responsePartial(getContentType(), range, len);
            off = (int) range.getStart();
            len = (int) (range.getEnd() - off + 1);
          } else {
            responseNotSatisfiable(range, len);
            return;
          }
        }

        if (req.getMethod() != Method.HEAD) {
          if (buf == null) {
            Utils.transfer(in, out, off, len);
          } else {
            out.write(buf.array(), off, len);
          }
        }
      } finally {
        Utils.close(in, out);
      }
    }
  }
}
