package com.ap.transmission.btc.ssdp;

import com.ap.transmission.btc.Prefs;
import com.ap.transmission.btc.http.HttpServer;
import com.ap.transmission.btc.http.handlers.upnp.DescriptorHandler;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.ap.transmission.btc.Utils.ASCII;
import static com.ap.transmission.btc.Utils.debug;
import static com.ap.transmission.btc.Utils.err;

/**
 * @author Andrey Pavlenko
 */
public class SsdpServer implements Closeable {
  private static final String SERVER_NAME = "Transmission BTC UPnP/1.0";
  private final String TAG = getClass().getName();
  private final HttpServer httpServer;
  private volatile MulticastSocket socket;
  private OkContent ok;
  private NotifyContent notify;

  public SsdpServer(HttpServer httpServer) {
    this.httpServer = httpServer;
  }

  public synchronized void start() throws IOException {
    if (socket != null) return;
    final MulticastSocket socket = new MulticastSocket(1900);
    socket.joinGroup(InetAddress.getByName("239.255.255.250"));
    this.socket = socket;
    new Thread() {
      @Override
      public void run() {
        byte data[] = new byte[1024];
        debug(TAG, "SSDP Server started");

        Future<?> notif = httpServer.getTransmission().getScheduler().scheduleWithFixedDelay(
            new Runnable() {
              @Override
              public void run() {
                sendNotify();
              }
            }, 0, 1, TimeUnit.MINUTES);

        while (!socket.isClosed()) {
          try {
            DatagramPacket pkt = new DatagramPacket(data, data.length);
            socket.receive(pkt);
            handle(socket, pkt);
          } catch (SocketException ignore) { // Socket closed?
          } catch (Throwable ex) {
            err(TAG, ex, "SSDP Server failure");
          }
        }

        notif.cancel(false);
        sendByebye();
        debug(TAG, "SSDP Server stopped");
      }
    }.start();
  }

  public synchronized void stop() {
    if (socket == null) return;
    socket.close();
    socket = null;
  }

  public void sendNotify() {
    if (httpServer.getTransmission().isSuspended()) return;
    NotifyContent cnt = notify;

    if ((cnt == null) || !cnt.isValid(httpServer)) {
      if(cnt != null) sendByebye();
      cnt = notify = new NotifyContent(httpServer);
    }

    sendMsg(cnt.content);
  }

  private void sendByebye() {
    Prefs prefs = httpServer.getTransmission().getPrefs();
    String uuid = prefs.getUUID();
    String str = "NOTIFY * HTTP/1.1\r\n" +
        "Host: 239.255.255.250:1900\r\n" +
        "NTS: ssdp:byebye\r\n" +
        "USN: uuid:" + uuid + "::urn:schemas-upnp-org:device:MediaServer:1\r\n" +
        "NT: urn:schemas-upnp-org:device:MediaServer:1\r\n\r\n";
    byte[] data = String.format(str, uuid).getBytes(ASCII);
    sendMsg(data);
  }

  private void sendMsg(byte[] data) {
    try {
      if (isDebugEnabled()) {
        debug(TAG, "Sending SSDP message:\n%s", new String(data, ASCII));
      }

      final DatagramSocket socket = new DatagramSocket();
      DatagramPacket pkt = new DatagramPacket(data, data.length,
          InetAddress.getByName("239.255.255.250"), 1900);
      socket.send(pkt);
    } catch (IOException ex) {
      err(TAG, ex, "Failed to send SSDP message");
    }
  }

  @Override
  public void close() {
    stop();
  }

  public boolean isRunning() {
    MulticastSocket s = socket;
    return (s != null) && !s.isClosed();
  }

  private void handle(MulticastSocket socket, DatagramPacket pkt) {
    if (httpServer.getTransmission().isSuspended()) return;
    OkContent cnt = ok;

    if ((cnt == null) || !cnt.isValid(httpServer)) {
      cnt = ok = new OkContent(httpServer);
    }

    if (isDebugEnabled()) {
      String req = new String(pkt.getData(), pkt.getOffset(), pkt.getLength(), ASCII);
      String resp = new String(cnt.content, ASCII);
      debug(TAG, "SSDP request received:\n%s", req);
      debug(TAG, "Sending SSDP response:\n%s", resp);
    }

    try {
      DatagramPacket resp = new DatagramPacket(cnt.content, cnt.content.length, pkt.getAddress(),
          pkt.getPort());
      socket.send(resp);
    } catch (IOException ex) {
      err(TAG, ex, "Failed to send SSDP response");
    }
  }

  private boolean isDebugEnabled() {
    return false;
  }

  private static abstract class Content {
    final String host;
    final int port;
    final byte[] content;

    Content(HttpServer s) {
      host = s.getHostName();
      port = s.getPort();
      Prefs prefs = s.getTransmission().getPrefs();
      String uuid = prefs.getUUID();
      String locationUrl = s.getAddress() + DescriptorHandler.PATH;
      content = createContent(uuid, locationUrl);
    }

    abstract byte[] createContent(String uuid, String locationUrl);

    boolean isValid(HttpServer s) {
      return (port == s.getPort()) && host.equals(s.getHostName());
    }
  }

  private static final class OkContent extends Content {

    OkContent(HttpServer s) {
      super(s);
    }

    @Override
    byte[] createContent(String uuid, String locationUrl) {
      String okStr = "HTTP/1.1 200 OK\r\n" +
          "Cache-Control: max-age=1800\r\n" +
          "Ext:\r\n" +
          "Location: %s\r\n" +
          "Server: %s\r\n" +
          "ST: urn:schemas-upnp-org:device:MediaServer:1\r\n" +
          "USN: uuid:%s::urn:schemas-upnp-org:device:MediaServer:1\r\n\r\n";
      return String.format(okStr, locationUrl, SERVER_NAME, uuid).getBytes(ASCII);
    }
  }

  private static final class NotifyContent extends Content {

    NotifyContent(HttpServer s) {
      super(s);
    }

    @Override
    byte[] createContent(String uuid, String locationUrl) {
      String notifyStr = "NOTIFY * HTTP/1.1\r\n" +
          "Host: 239.255.255.250:1900\r\n" +
          "Cache-Control: max-age=1800\r\n" +
          "Location: %s\r\n" +
          "Server: %s\r\n" +
          "NTS: ssdp:alive\r\n" +
          "USN: uuid:%s::urn:schemas-upnp-org:device:MediaServer:1\r\n" +
          "NT: urn:schemas-upnp-org:device:MediaServer:1\r\n\r\n";
      return String.format(notifyStr, locationUrl, SERVER_NAME, uuid).getBytes(ASCII);
    }
  }
}
