package com.ap.transmission.btc.http;

import com.ap.transmission.btc.Prefs;
import com.ap.transmission.btc.Utils;
import com.ap.transmission.btc.http.handlers.torrent.PlaylistHandler;
import com.ap.transmission.btc.http.handlers.torrent.TorrentHandler;
import com.ap.transmission.btc.http.handlers.upnp.DescriptorHandler;
import com.ap.transmission.btc.torrent.Transmission;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

import static com.ap.transmission.btc.Utils.debug;
import static com.ap.transmission.btc.Utils.err;
import static com.ap.transmission.btc.Utils.isDebugEnabled;
import static com.ap.transmission.btc.Utils.warn;


/**
 * @author Andrey Pavlenko
 */
public class SimpleHttpServer implements HttpServer {
  private static final String TAG = SimpleHttpServer.class.getName();
  private final Transmission transmission;
  private final Map<String, RequestHandler> handlers;
  private final Map<String, RequestHandler> rootHandlers;
  private volatile ServerSocket serverSocket;
  private volatile String hostName;

  public SimpleHttpServer(Transmission transmission) {
    this.transmission = transmission;
    handlers = new HashMap<>(8);
    rootHandlers = new HashMap<>(8);
    rootHandlers.put(TorrentHandler.PATH, new TorrentHandler());
    rootHandlers.put(PlaylistHandler.PATH, new PlaylistHandler());
    DescriptorHandler.addHandlers(handlers, transmission);
  }

  public void start() throws IOException {
    transmission.writeLock().lock();
    try {
      if (isRunning()) throw new IllegalStateException("Transmission is not running");
      Prefs prefs = transmission.getPrefs();
      final ServerSocket ss;

      if (prefs.isUpnpEnabled()) {
        serverSocket = ss = new ServerSocket(prefs.getHttpServerPort());
        hostName = null;
      } else {
        InetAddress addr = InetAddress.getByAddress("localhost", new byte[]{127, 0, 0, 1});
        serverSocket = ss = new ServerSocket(0, 0, addr);
        hostName = addr.getHostAddress();
      }

      final ExecutorService threadPool = transmission.getExecutor();
      threadPool.submit(new Runnable() {
        @Override
        public void run() {
          debug(TAG, "HttpServer started: %s", SimpleHttpServer.this);

          while (!ss.isClosed()) {
            try {
              Socket s = ss.accept();
              handle(s, threadPool);
            } catch (SocketException ignore) {
              // The server stopped
              break;
            } catch (SocketTimeoutException ignore) {
            } catch (Throwable ex) {
              err(TAG, ex, "Web server error");
            }
          }

          stop();
          debug(TAG, "HttpServer stopped: %s", SimpleHttpServer.this);
        }
      });
    } finally {
      transmission.writeLock().unlock();
    }
  }

  public void stop() {
    synchronized (transmission) {
      ServerSocket s = serverSocket;
      if (s != null) try { s.close(); } catch (IOException ignore) {}
      serverSocket = null;
    }
  }

  @Override
  public void close() {
    stop();
  }

  public boolean isRunning() {
    return (serverSocket != null);
  }

  @Override
  public Transmission getTransmission() {
    return transmission;
  }

  public int getPort() {
    ServerSocket s = serverSocket;
    return (s != null) ? s.getLocalPort() : 0;
  }

  @Override
  public String getHostName() {
    String hn = hostName;
    if (hn != null) return hn;
    hn = Utils.getIPAddress(transmission.getPrefs().getContext());
    return (hn != null) ? hn : "localhost";
  }

  @Override
  public String getAddress() {
    String host = getHostName();

    try {
      return new URL("http", host, getPort(), "").toString();
    } catch (MalformedURLException ex) {
      err(TAG, ex, "Unexpected exception");
      return "http://" + host + ":" + getPort();
    }
  }

  @Override
  public String toString() {
    ServerSocket s = serverSocket;

    if (s != null) {
      return getClass().getSimpleName() + s;
    } else {
      return getClass().getSimpleName() + ": Stopped";
    }
  }

  private void handle(final Socket s, final ExecutorService threadPool) {
    try {
      threadPool.submit(new Runnable() {
        @Override
        public void run() {
          try {
            if (isDebugEnabled()) debug(TAG, "New connection: %s", s);
            doHandle(s, threadPool);
          } finally {
            if (isDebugEnabled()) debug(TAG, "Connection closed: %s", s);
          }
        }
      });
    } catch (RejectedExecutionException ex) {
      err(TAG, "Thread pool limit exceeded!");
      Utils.close(s);
    }
  }

  private void doHandle(final Socket s, final ExecutorService threadPool) {
    Request req;
    final InputStream in;
    final Thread handlerThread = Thread.currentThread();

    try {
      in = s.getInputStream();
      Object r = Request.read(in, TAG);

      if (r == null) { // Stream closed
        Utils.close(s);
        return;
      } else if (r instanceof Request) {
        req = (Request) r;
      } else {
        OutputStream out = s.getOutputStream();
        ((Response) r).write(out);
        Utils.close(out);
        Utils.close(s);
        return;
      }
    } catch (Throwable ex) {
      err(TAG, ex, "Failed to read request");
      Utils.close(s);
      return;
    }

    debug(TAG, "Request received:\n%s", req);
    RequestHandler h = getHandler(req);

    if (h == null) {
      try {
        warn(TAG, "No handler for %s", req.getPath());
        Response.NotFound.instance.write(s.getOutputStream());
      } catch (Throwable ignore) {
      } finally {
        Utils.close(s);
      }

      return;
    }

    try {
      // Connection watchdog
      threadPool.submit(new Runnable() {
        @Override
        public void run() {
          try {
            while (!s.isClosed() && (in.read() != -1)) {
              warn(TAG, "Unexpected input!");
            }
          } catch (IOException e) {
            if (!s.isClosed()) {
              debug(TAG, "Socket closed by remote peer");
              Utils.close(s);
              handlerThread.interrupt();
            }
          }
        }
      });
    } catch (RejectedExecutionException ignore) {
      debug(TAG, "Thread pool limit exceeded!");
    }

    try {
      h.handle(SimpleHttpServer.this, req, s);
    } catch (Throwable ex) {
      err(TAG, ex, "RequestHandler failed: %s", h);
    } finally {
      Utils.close(s);
    }
  }

  private RequestHandler getHandler(Request req) {
    RequestHandler h = rootHandlers.get(req.getRootPath());
    return (h == null) ? handlers.get(req.getPath()) : h;
  }
}
