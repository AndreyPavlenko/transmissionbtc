package com.ap.transmission.btc.http.handlers.torrent;

import android.net.Uri;

import com.ap.transmission.btc.Utils;
import com.ap.transmission.btc.func.Supplier;
import com.ap.transmission.btc.http.HttpServer;
import com.ap.transmission.btc.http.Method;
import com.ap.transmission.btc.http.Range;
import com.ap.transmission.btc.http.Request;
import com.ap.transmission.btc.http.RequestHandler;
import com.ap.transmission.btc.http.Response.BadRequest;
import com.ap.transmission.btc.http.Response.NotFound;
import com.ap.transmission.btc.http.Response.ServiceUnavailable;
import com.ap.transmission.btc.http.handlers.HandlerBase;
import com.ap.transmission.btc.torrent.NoSuchTorrentException;
import com.ap.transmission.btc.torrent.TorrentFile;
import com.ap.transmission.btc.torrent.Transmission;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.TimeoutException;

import static com.ap.transmission.btc.Utils.isDebugEnabled;

/**
 * @author Andrey Pavlenko
 */
public class TorrentHandler implements RequestHandler {
  private static final int PIECE_MARGIN = 20 * 1024 * 1024;
  public static final String PATH = "/torrent";
  private static final int SLEEP_TIME = 1000;
  private static final int SLEEP_RETRIES = 300;

  public static Uri createUri(String host, int port, String hash, int fileIdx, String fileExt) {
    StringBuilder sb = new StringBuilder(128);
    return Uri.parse(createUri(host, port, hash, fileIdx, fileExt, sb).toString());
  }

  public static StringBuilder createUri(String host, int port, String hash, int fileIdx,
                                        String fileExt, StringBuilder sb) {
    sb.append("http://");
    if (host.indexOf(':') >= 0 && !host.startsWith("[")) sb.append('[').append(host).append(']');
    else sb.append(host);
    sb.append(':').append(port).append(PATH).append('/').append(hash).append('/').append(fileIdx);
    if ((fileExt != null) && !fileExt.isEmpty()) sb.append('.').append(fileExt);
    return sb;
  }

  public static TorrentFile waitFor(Transmission tr, String hash, int fileIdx,
                                    Supplier<Boolean> breakCondition, String logTag)
      throws IllegalStateException, NoSuchTorrentException, TimeoutException {
    TorrentFile file = waitForFile(null, tr, hash, fileIdx, breakCondition, logTag);
    if (file == null) return null;

    return (file.waitForPiece(file.getFirstPieceIndex(),
        SLEEP_TIME, SLEEP_RETRIES, -1, breakCondition, logTag)) ? file : null;
  }

  @Override
  public void handle(HttpServer server, Request req, Socket socket) {
    new Handler(server, socket).handle(req);
  }

  private static TorrentFile waitForFile(TorrentFile file, Transmission tr, String hash, int fileIdx,
                                         Supplier<Boolean> breakCondition, String logTag)
      throws TimeoutException {
    int retry = 0;

    while (true) {
      try {
        if (file == null) file = tr.getTorrentFs().findTorrent(hash).getFile(fileIdx);
        if ((file.findLocation() != null)) return file;
      } catch (NoSuchTorrentException ignore) { }

      if (retry == SLEEP_RETRIES) {
        if (hash == null) {
          throw new TimeoutException("File not found in " + (SLEEP_RETRIES * SLEEP_TIME / 1000)
              + " seconds: " + file);
        } else {
          throw new TimeoutException("File not found in " + (SLEEP_RETRIES * SLEEP_TIME / 1000)
              + " seconds: hash=" + hash + ", idx=" + fileIdx);
        }
      }

      // Wait until the file is created
      if (isDebugEnabled()) {
        if (hash == null) {
          Utils.debug(logTag, "Waiting for file: idx=%d, retry=%d", fileIdx, retry);
        } else {
          Utils.debug(logTag, "Waiting for file: hash=%s, idx=%d, retry=%d",
              hash, fileIdx, retry);
        }
      }

      try {
        Thread.sleep(SLEEP_TIME);
        retry++;
      } catch (InterruptedException e) {
        return null;
      }

      if (breakCondition.get()) return null;
    }
  }

  private static final class Handler extends HandlerBase {

    protected Handler(HttpServer server, Socket socket) {
      super("TorrentHandler", server, socket);
    }

    @Override
    protected void doHandle(Request req) throws Throwable {
      String hash;
      int fileIdx;
      long fileLen;
      String fileName;
      boolean complete;
      TorrentFile file;
      String[] s = req.splitPath(3);
      Transmission tr = getTransmission();

      if (s.length == 2) {
        int idx = s[1].indexOf('.');
        fileIdx = 0;
        hash = (idx == -1) ? s[1] : s[1].substring(0, idx);
      } else if (s.length == 3) {
        int idx = s[2].indexOf('.');
        String fileIdxStr = (idx == -1) ? s[2] : s[2].substring(0, idx);

        try {
          fileIdx = Integer.parseInt(fileIdxStr);
        } catch (NumberFormatException ex) {
          fail(BadRequest.instance, "Invalid file index: %s, path: %s", fileIdxStr, req.getPath());
          return;
        }

        hash = s[1];
      } else {
        fail(BadRequest.instance, "Invalid torrent path: %s", req.getPath());
        return;
      }

      try {
        file = tr.getTorrentFs().findTorrent(hash).getFile(fileIdx);

        if (file.isDnd()) {
          fail(NotFound.instance, "File is unwanted for download: %s", file);
          return;
        }

        fileName = file.getFullName();
        fileLen = file.getLength();
        complete = file.isComplete();
      } catch (IllegalStateException ex) {
        fail(ServiceUnavailable.instance, "Transmission is not running");
        return;
      } catch (NoSuchTorrentException ex) {
        fail(NotFound.instance, "No such torrent: hash=%s, file=%d", s[1], fileIdx);
        return;
      } catch (IndexOutOfBoundsException ex) {
        fail(NotFound.instance, "No such file: hash=%s, file=%d", s[1], fileIdx);
        return;
      }

      String mime = Utils.getMimeTypeFromFileName(fileName);
      Range range = req.getRange();
      long off = 0;
      long len = fileLen;
      OutputStream out;

      if (range == null) {
        out = responseOk(mime, fileLen, true);
      } else {
        range.allign(fileLen);

        if (range.isSatisfiable(fileLen)) {
          out = responsePartial(mime, range, fileLen);
          off = range.getStart();
          len = range.getEnd() - off + 1;
        } else {
          responseNotSatisfiable(range, fileLen);
          return;
        }
      }

      if (req.getMethod() == Method.HEAD) return;

      //noinspection UnusedAssignment
      s = null;
      //noinspection UnusedAssignment
      req = null;
      //noinspection UnusedAssignment
      range = null;

      Supplier<Boolean> breakCondition = new Supplier<Boolean>() {
        @Override
        public Boolean get() {
          return socket.isClosed();
        }
      };

      if (complete) {
        writeComplete(out, file, off, len, breakCondition);
      } else {
        writeIncomplete(out, file, off, len, breakCondition);
      }
    }

    private void writeComplete(OutputStream out,
                               TorrentFile file, long off, long len,
                               Supplier<Boolean> breakCondition)
        throws IllegalStateException, NoSuchTorrentException, IOException, TimeoutException {
      InputStream in = openFile(file, file.getIndex(), off, breakCondition);
      if (in == null) return;
      byte[] buf = new byte[socket.getSendBufferSize()];

      try {
        for (long end = off + len; (off < end) && !breakCondition.get(); ) {
          int read = in.read(buf, 0, (int) Math.min(end - off, buf.length));

          if (read == -1) {
            warn("Unexpected end of stream");
            break;
          }

          out.write(buf, 0, read);
          off += read;
        }
      } finally {
        Utils.close(in);
      }
    }

    private void writeIncomplete(OutputStream out, TorrentFile file, long off, long len,
                                 Supplier<Boolean> breakCondition)
        throws IllegalStateException, NoSuchTorrentException, IOException, TimeoutException {
      long pieceLength = file.getPieceLength();
      int margin = (int) ((PIECE_MARGIN % pieceLength) == 0 ? (PIECE_MARGIN / pieceLength) :
          (PIECE_MARGIN / pieceLength) + 1);
      byte[] buf = new byte[(int) Math.min(pieceLength, 8 * 1024 * 1024)];

      for (long end = off + len; (off < end) && !breakCondition.get(); ) {
        int read = file.read(buf, off, (int) Math.min(end - off, buf.length), SLEEP_TIME,
            SLEEP_RETRIES, margin, breakCondition, TAG);
        if (read == -1) return; // Socket closed
        out.write(buf, 0, read);
        off += read;

        if (file.isComplete()) {
          debug("File complete, switching to writeComplete()");
          writeComplete(out, file, off, end - off, breakCondition);
          return;
        }
      }
    }

    private FileInputStream openFile(TorrentFile file, int fileIdx, long skip, Supplier<Boolean> breakCondition)
        throws IllegalStateException, NoSuchTorrentException, IOException, TimeoutException {
      file = waitForFile(file, null, null, fileIdx, breakCondition, TAG);
      if (file == null) return null;

      String path = file.findLocation();
      FileInputStream fis = new FileInputStream(path);
      debug("File opened: %s", path);

      while (skip > 0) {
        skip -= fis.skip(skip);
      }

      return fis;
    }
  }
}
