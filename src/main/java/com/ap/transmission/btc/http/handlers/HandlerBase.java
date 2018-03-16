package com.ap.transmission.btc.http.handlers;

import android.util.Log;

import com.ap.transmission.btc.Utils;
import com.ap.transmission.btc.http.HttpServer;
import com.ap.transmission.btc.http.Range;
import com.ap.transmission.btc.http.Request;
import com.ap.transmission.btc.http.Response;
import com.ap.transmission.btc.http.Response.ServerError;
import com.ap.transmission.btc.torrent.Transmission;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;

import static com.ap.transmission.btc.Utils.ASCII;
import static com.ap.transmission.btc.Utils.isDebugEnabled;

/**
 * @author Andrey Pavlenko
 */
public abstract class HandlerBase {
  protected final String TAG;
  protected final HttpServer server;
  protected final Socket socket;

  protected HandlerBase(String logTag, HttpServer server, Socket socket) {
    TAG = "transmission." + logTag + " (" + socket.getRemoteSocketAddress() + ')';
    this.server = server;
    this.socket = socket;
  }

  public HttpServer getHttpServer() {
    return server;
  }

  public Socket getSocket() {
    return socket;
  }

  protected abstract void doHandle(Request req) throws Throwable;

  public void handle(Request req) {
    try {
      doHandle(req);
    } catch (SocketException ignore) {
      // Socket closed?
    } catch (IOException ex) {
      debug("Failed to handle request: %s", ex);
    } catch (Throwable ex) {
      fail(ServerError.instance, ex, "Handler failed: %s", this);
    } finally {
      Utils.close(socket);
    }
  }

  protected OutputStream responseOk(String contentType, long contentLen,
                                    @SuppressWarnings("SameParameterValue") boolean acceptRanges)
      throws IOException {
    OutputStream out = getOutputStream();
    out.write(Ok.data);
    out.write(Long.toString(contentLen).getBytes(ASCII));

    if (contentType != null) {
      out.write(ContentType.data);
      out.write(contentType.getBytes(ASCII));
    }

    if (acceptRanges) {
      out.write(AcceptRanges.data);
    }

    out.write(EOH.data);
    return flushOutputStream(out);
  }

  protected OutputStream responsePartial(String contentType, Range range, long totalLength)
      throws IOException {
    OutputStream out = getOutputStream();
    out.write(Partial.data);
    out.write(Long.toString(range.getLength()).getBytes(ASCII));

    out.write(ContentRange.data);
    out.write(Long.toString(range.getStart()).getBytes(ASCII));
    out.write('-');
    out.write(Long.toString(range.getEnd()).getBytes(ASCII));
    out.write('/');
    out.write(Long.toString(totalLength).getBytes(ASCII));

    if (contentType != null) {
      out.write(ContentType.data);
      out.write(contentType.getBytes(ASCII));
    }

    out.write(EOH.data);
    return flushOutputStream(out);
  }

  protected void responseNotSatisfiable(Range range, long contentLen)
      throws IOException {
    warn("Range is not satisfiable: range=%s, length=%d", range, contentLen);
    OutputStream out = getOutputStream();
    out.write(RangeNotSatisfiable.data);
    out.write(Long.toString(contentLen).getBytes(ASCII));
    out.write(EOH.data);
    flushOutputStream(out);
  }

  protected Transmission getTransmission() {
    return server.getTransmission();
  }

  protected void fail(Response resp, String msg, Object... args) {
    fail(resp, null, msg, args);
  }

  protected void fail(Response resp, Throwable err, String msg, Object... args) {
    if (msg != null) {
      if (err == null) Log.w(TAG, format(msg, args));
      else Log.w(TAG, format(msg, args), err);
    }

    if (resp != null) {
      try {
        OutputStream out = getOutputStream();
        resp.write(out);
        Utils.close(flushOutputStream(out));
      } catch (Throwable ex) {
        if (isDebugEnabled()) Log.d(TAG, "Failed to send response", ex);
      }
    }
  }

  public String getServerHost(Request req) {
    String host = req.getHost();
    if (host == null) return getHttpServer().getHostName();
    int idx = host.lastIndexOf(':');
    return (idx == -1) ? host : host.substring(0, idx);
  }

  public void debug(String msg, Object... args) {
    Utils.debug(TAG, msg, args);
  }

  public void debug(Throwable ex, String msg, Object... args) {
    Utils.debug(TAG, ex, msg, args);
  }

  public void warn(String msg, Object... args) {
    Utils.warn(TAG, msg, args);
  }

  public void warn(Throwable ex, String msg, Object... args) {
    Utils.warn(TAG, ex, msg, args);
  }

  private static String format(String msg, Object... args) {
    return (args != null) && (args.length > 0) ? String.format(msg, args) : msg;
  }

  private OutputStream getOutputStream() throws IOException {
    return isDebugEnabled() ? new ByteArrayOutputStream(1024) : socket.getOutputStream();
  }

  private OutputStream flushOutputStream(OutputStream out) throws IOException {
    if (isDebugEnabled()) {
      ByteArrayOutputStream baos = (ByteArrayOutputStream) out;
      byte[] bytes = baos.toByteArray();
      debug("Writing response:\n" + new String(bytes));
      out = socket.getOutputStream();
      out.write(bytes);
      return out;
    } else {
      return out;
    }
  }

  private static final class Ok {
    static final byte[] data = ("HTTP/1.1 200 OK\r\nConnection: close\r\n"
        + "Content-Length: ").getBytes(ASCII);
  }

  private static final class Partial {
    static final byte[] data = ("HTTP/1.1 206 Partial Content\r\nConnection: close\r\n"
        + "Content-Length: ").getBytes(ASCII);
  }

  private static final class RangeNotSatisfiable {
    static final byte[] data = ("HTTP/1.1 416 Range Not Satisfiable\r\nConnection: close\r\n" +
        "Content-Range: bytes */").getBytes(ASCII);
  }

  private static final class AcceptRanges {
    static final byte[] data = "\r\nAccept-Ranges: bytes".getBytes(ASCII);
  }

  private static final class ContentType {
    static final byte[] data = "\r\nContent-Type: ".getBytes(ASCII);
  }

  private static final class ContentRange {
    static final byte[] data = "\r\nContent-Range: bytes ".getBytes(ASCII);
  }

  private static final class EOH {
    static final byte[] data = "\r\n\r\n".getBytes(ASCII);
  }
}
