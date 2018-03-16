package com.ap.transmission.btc.http;

import java.io.IOException;
import java.io.OutputStream;

import static com.ap.transmission.btc.Utils.ASCII;

/**
 * @author Andrey Pavlenko
 */
@SuppressWarnings("ALL")
public interface Response {
  void write(OutputStream out) throws IOException;

  static class StaticResponse implements Response {
    private final byte[] data;

    public StaticResponse(byte[] data) {this.data = data;}

    @Override
    public final void write(OutputStream out) throws IOException {
      out.write(data);
      out.close();
    }
  }

  static final class BadRequest {
    public static final Response instance = new StaticResponse(("HTTP/1.1 400 Bad Request\r\n" +
        "Connection: close\r\n" +
        "Content-Length: 0\r\n\r\n").getBytes(ASCII)
    );
  }

  static final class NotFound {
    public static final Response instance = new StaticResponse(("HTTP/1.1 404 Not Found\r\n" +
        "Connection: close\r\n" +
        "Content-Length: 0\r\n\r\n").getBytes(ASCII)
    );
  }

  static final class MethodNotAllowed {
    public static final Response instance = new StaticResponse(("HTTP/1.1 405 Method Not Allowed\r\n" +
        "Connection: close\r\n" +
        "Content-Length: 0\r\n\r\n").getBytes(ASCII)
    );
  }

  static final class PayloadTooLarge {
    public static final Response instance = new StaticResponse(("HTTP/1.1 413 Payload Too Large\r\n" +
        "Connection: close\r\n" +
        "Content-Length: 0\r\n\r\n").getBytes(ASCII)
    );
  }

  static final class ServerError {
    public static final Response instance = new StaticResponse(("HTTP/1.1 500 Internal Server Error\r\n" +
        "Connection: close\r\n" +
        "Content-Length: 0\r\n\r\n").getBytes(ASCII)
    );
  }

  static final class ServiceUnavailable {
    public static final Response instance = new StaticResponse(("HTTP/1.1 503 Service Unavailable\r\n" +
        "Connection: close\r\n" +
        "Content-Length: 0\r\n\r\n").getBytes(ASCII)
    );
  }

  static final class VersionNotSupported {
    public static final Response instance = new StaticResponse(("HTTP/1.1 505 HTTP Version Not Supported\r\n" +
        "Connection: close\r\n" +
        "Content-Length: 0\r\n\r\n").getBytes(ASCII)
    );
  }
}
