package com.ap.transmission.btc.http.handlers.torrent;

import android.net.Uri;

import com.ap.transmission.btc.NaturalOrderComparator;
import com.ap.transmission.btc.Utils;
import com.ap.transmission.btc.http.HttpServer;
import com.ap.transmission.btc.http.Request;
import com.ap.transmission.btc.http.RequestHandler;
import com.ap.transmission.btc.http.Response.BadRequest;
import com.ap.transmission.btc.http.Response.NotFound;
import com.ap.transmission.btc.http.Response.ServiceUnavailable;
import com.ap.transmission.btc.http.handlers.HandlerBase;
import com.ap.transmission.btc.torrent.MediaInfo;
import com.ap.transmission.btc.torrent.NoSuchTorrentException;
import com.ap.transmission.btc.torrent.Torrent;
import com.ap.transmission.btc.torrent.TorrentFile;
import com.ap.transmission.btc.torrent.TorrentItem;
import com.ap.transmission.btc.torrent.Transmission;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author Andrey Pavlenko
 */
public class PlaylistHandler implements RequestHandler {
  public static final String PATH = "/playlist";
  public static final String MIME_TYPE = "audio/mpegurl";

  public static Uri createUri(String host, int port, String hash, int folderIdx) {
    StringBuilder sb = new StringBuilder(128);
    return Uri.parse(createUri(host, port, hash, folderIdx, sb).toString());
  }

  public static StringBuilder createUri(String host, int port, String hash, int folderIdx,
                                        StringBuilder sb) {
    sb.append("http://");
    if (host.indexOf(':') >= 0 && !host.startsWith("[")) sb.append('[').append(host).append(']');
    else sb.append(host);
    sb.append(':').append(port).append(PATH).append('/').append(hash);
    if (folderIdx != -1) sb.append('/').append(folderIdx);
    return sb.append(".m3u");
  }

  @Override
  public void handle(HttpServer server, Request req, Socket socket) {
    new Handler(server, socket).handle(req);
  }

  private static final class Handler extends HandlerBase {

    protected Handler(HttpServer server, Socket socket) {
      super("PlaylistHandler", server, socket);
    }

    @Override
    protected void doHandle(Request req) throws Throwable {
      String hash;
      int dirIdx;
      String[] s = req.splitPath(3);
      Transmission tr = getTransmission();
      String host = getServerHost(req);
      int port = getHttpServer().getPort();
      List<TorrentFile> files;

      if (s.length == 2) {
        int idx = s[1].indexOf('.');
        dirIdx = -1;
        hash = (idx == -1) ? s[1] : s[1].substring(0, idx);
      } else if (s.length == 3) {
        int idx = s[2].indexOf('.');
        String dirIdxStr = (idx == -1) ? s[2] : s[2].substring(0, idx);

        try {
          dirIdx = Integer.parseInt(dirIdxStr);
        } catch (NumberFormatException ex) {
          fail(BadRequest.instance, "Invalid dir index: %s, req: %s", dirIdxStr, req.getPath());
          return;
        }

        hash = s[1];
      } else {
        generatePlaylists(tr, req, host, port);
        return;
      }

      try {
        Torrent tor = tr.getTorrentFs().findTorrent(hash);

        if (tor == null) {
          fail(NotFound.instance, "No such torrent: %s", hash);
          return;
        }

        if (dirIdx == -1) {
          files = tor.lsFiles();
        } else {
          files = tor.getDir(dirIdx).lsFiles();
        }
      } catch (IllegalStateException ex) {
        fail(ServiceUnavailable.instance, "Transmission is not running");
        return;
      } catch (IllegalArgumentException ex) {
        fail(NotFound.instance, "Invalid request: %s", req.getPath());
        return;
      }

      List<TorrentFile> mediaFiles = new ArrayList<>(files.size());

      for (TorrentFile f : files) {
        if (f.isMedia()) mediaFiles.add(f);
      }

      final NaturalOrderComparator cmp = new NaturalOrderComparator();
      final boolean byFullName = (dirIdx == -1);
      Collections.sort(mediaFiles, new Comparator<TorrentFile>() {
        @Override
        public int compare(TorrentFile f1, TorrentFile f2) {
          if (byFullName) return cmp.compare(f1.getFullName(), f2.getFullName());
          else return cmp.compare(f1.getName(), f2.getName());
        }
      });

      StringBuilder sb = new StringBuilder(4096);
      sb.append("#EXTM3U\r\n");

      for (TorrentFile f : mediaFiles) {
        String name = f.getName();
        String ext = Utils.getFileExtension(name);
        MediaInfo inf = f.getMediaInfo();
        String title = null;
        if (inf != null) title = inf.getTitle();
        if (title == null) title = name;
        sb.append("#EXTINF:-1,").append(title).append("\r\n");
        TorrentHandler.createUri(host, port, hash, f.getIndex(), ext, sb).append("\r\n");
      }

      write(sb);
    }

    private void generatePlaylists(Transmission tr, Request req, String host, int port) throws IOException {
      List<TorrentItem> torrents;

      try {
        torrents = tr.getTorrentFs().ls();
      } catch (IllegalStateException ex) {
        fail(ServiceUnavailable.instance, "Transmission is not running");
        return;
      } catch (IllegalArgumentException ex) {
        fail(NotFound.instance, "Invalid request: %s", req.getPath());
        return;
      }

      List<Torrent> mediaTorrents = new ArrayList<>(torrents.size());

      for (TorrentItem i : torrents) {
        Torrent t = (Torrent) i;

        try {
          if (t.hasMediaFiles()) mediaTorrents.add(t);
        } catch (NoSuchTorrentException ex) {
          t.getFs().reportNoSuchTorrent(ex);
        }
      }

      final NaturalOrderComparator cmp = new NaturalOrderComparator();
      Collections.sort(mediaTorrents, new Comparator<Torrent>() {
        @Override
        public int compare(Torrent t1, Torrent t2) {
          return cmp.compare(t1.getName(), t2.getName());
        }
      });

      StringBuilder sb = new StringBuilder(512).append("#EXTM3U\r\n");

      for (Torrent t : mediaTorrents) {
        sb.append("#EXTINF:-1,").append(t.getName()).append("\r\n");
        createUri(host, port, t.getHashString(), -1, sb).append("\r\n");
      }

      write(sb);
    }

    private void write(StringBuilder sb) throws IOException {
      byte[] content = sb.toString().getBytes(Utils.UTF8);
      OutputStream out = responseOk(MIME_TYPE, content.length, false);
      out.write(content);
      out.close();
    }
  }
}
