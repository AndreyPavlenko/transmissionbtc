package com.ap.transmission.btc.http;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;

import static com.ap.transmission.btc.Utils.readAll;
import static com.ap.transmission.btc.Utils.warn;

/**
 * @author Andrey Pavlenko
 */
public class Request {
  public static final int MAX_CONTENT_LEN = 512 * 1024;
  private final Method method;
  private final HttpVersion httpVersion;
  private final String path;
  private final String host;
  private final long contentLength;
  private final Range range;
  private final ByteBuffer payload;
  private String rootPath;

  public Request(Method method, HttpVersion httpVersion, String path, String host,
                 long contentLength, Range range, ByteBuffer payload) {
    this.method = method;
    this.httpVersion = httpVersion;
    this.path = path;
    this.host = host;
    this.contentLength = contentLength;
    this.range = range;
    this.payload = payload;
  }

  public Method getMethod() {
    return method;
  }

  public HttpVersion getHttpVersion() {
    return httpVersion;
  }

  public String getPath() {
    return path;
  }

  public String getRootPath() {
    if (rootPath == null) {
      if (path.startsWith("/")) {
        int idx = path.indexOf('/', 1);
        rootPath = ((idx == -1) || (idx == 1)) ? path : path.substring(0, idx);
      } else if (path.equals("/")) {
        rootPath = "/";
      } else {
        rootPath = path;
      }
    }

    return rootPath;
  }

  public ByteBuffer getPayload() {
    return payload;
  }

  public String[] splitPath(int limit) {
    String path = getPath();
    int idx = path.indexOf("?");
    path = path.substring(1, (idx == -1) ? path.length() : idx);
    return path.split("/", limit);
  }

  public String getHost() {
    return host;
  }

  public long getContentLength() {
    return contentLength;
  }

  public Range getRange() {
    return range;
  }

  @Override
  public String toString() {
    return getMethod().toString() + ' ' + getPath() + ' ' + getHttpVersion() +
        "\r\nHost: " + getHost() +
        "\r\nContent-Length: " + getContentLength() +
        "\r\nRange: " + getRange();
  }

  public static Object read(InputStream in, String logTag) throws IOException {
    StringBuilder sb = new StringBuilder(128);
    String line = readLine(in, sb, logTag);
    String[] s;
    // debug(TAG, line);

    if ((line == null) || ((s = split(line)) == null)) {
      warn(logTag, "Invalid request line: %s", line);
      return Response.BadRequest.instance;
    }

    Method method = Method.fromString(s[0]);
    if (method == null) return Response.MethodNotAllowed.instance;

    HttpVersion version = HttpVersion.fromString(s[2]);
    if (version == null) return Response.VersionNotSupported.instance;

    String path = s[1];
    if (!path.startsWith("/")) {
      warn(logTag, "Invalid path: %s", s[1]);
      return Response.BadRequest.instance;
    }

    String host = "";
    long contentLen = 0;
    Range range = null;

    for (line = readLine(in, sb, logTag); ; line = readLine(in, sb, logTag)) {
      if (line == null) return null; // Stream closed
      if (line.isEmpty()) break; // End of header
      //debug(logTag, line);
      readHeader(line, s);

      if (s[0].equalsIgnoreCase("Host")) {
        host = s[1].trim();
      } else if (s[0].equalsIgnoreCase("Content-Length")) {
        try {
          contentLen = parseLong(s[1].trim());
        } catch (NumberFormatException ex) {
          warn(logTag, "Invalid Content-Length: %s", s[1]);
          return Response.BadRequest.instance;
        }

        if (contentLen > MAX_CONTENT_LEN) {
          return Response.PayloadTooLarge.instance;
        }
      } else if (s[0].equalsIgnoreCase("Range")) {
        String rg = s[1].trim();

        if (rg.startsWith("bytes=")) {
          rg = rg.substring(6);
        } else {
          warn(logTag, "Invalid or unsupported Range: %s", s[1]);
          continue;
        }

        try {
          if (rg.startsWith("-")) {
            range = new Range(0, parseLong(rg));
          } else if (rg.endsWith("-")) {
            range = new Range(-parseLong(rg.substring(0, rg.length() - 1)), Long.MAX_VALUE);
          } else {
            int idx = rg.indexOf('-');

            if (idx == -1) {
              warn(logTag, "Invalid or unsupported Range: %s", s[1]);
            } else {
              range = new Range(parseLong(rg.substring(0, idx)), parseLong(rg.substring(idx + 1)));
            }
          }
        } catch (NumberFormatException ex) {
          warn(logTag, "Invalid or unsupported Range: %s", s[1]);
          return Response.BadRequest.instance;
        }
      }
    }

    return new Request(method, version, path, host, contentLen, range,
        readAll(in, (int) contentLen, (int) contentLen));
  }

  private static String readLine(InputStream in, StringBuilder sb, String logTag) throws IOException {
    sb.setLength(0);

    for (int i = in.read(); ; i = in.read()) {
      if (i == -1) {
        return (sb.length() == 0) ? null : sb.toString();
      }

      char c = (char) i;

      switch (c) {
        case '\r':
          if ((c = (char) in.read()) != '\n') {
            warn(logTag, "Unexpected character after \\r: %s", c);
          }
        case '\n':
          return sb.toString();
        default:
          sb.append(c);
      }
    }
  }

  private static String[] split(String line) {
    String s[] = new String[3];
    int start, end;

    if ((end = line.indexOf(' ')) == -1) return null;
    s[0] = line.substring(0, end);

    start = end + 1;
    if (((end = line.indexOf(' ', start)) == -1) || (start == end)) return null;
    s[1] = line.substring(start, end);

    start = end + 1;
    if (start >= line.length()) return null;
    s[2] = line.substring(start);

    return s;
  }

  private static void readHeader(String line, String s[]) {
    int idx = line.indexOf(':');

    if ((idx == -1) || (idx == (line.length() - 1))) {
      s[0] = s[1] = "";
    } else {
      s[0] = line.substring(0, idx).trim();
      s[1] = line.substring(idx + 1);
    }
  }

  private static long parseLong(String value) throws NumberFormatException {
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException ex) {
      BigInteger i = new BigInteger(value);
      // Valid integer, but the value is greater than signed long
      return i.compareTo(BigInteger.ZERO) < 0 ? Long.MIN_VALUE : Long.MAX_VALUE;
    }
  }
}
