package com.ap.transmission.btc.ssdp;

import com.ap.transmission.btc.Prefs;
import com.ap.transmission.btc.torrent.Transmission;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketException;

import static com.ap.transmission.btc.Utils.ASCII;
import static com.ap.transmission.btc.Utils.debug;
import static com.ap.transmission.btc.Utils.err;

/**
 * @author Andrey Pavlenko
 */
public class SsdpServer implements Closeable {
  private final String TAG = getClass().getName();
  private final byte[] ok;
  private final byte[] notify;
  private final String uuid;
  private volatile MulticastSocket socket;
  private final Transmission transmission;

  public SsdpServer(Transmission transmission, String locationUrl) {
    String okStr = "HTTP/1.1 200 OK\r\n" +
        "Cache-Control: max-age=1800\r\n" +
        "Ext:\r\n" +
        "Location: %s\r\n" +
        "Server: %s\r\n" +
        "ST: urn:schemas-upnp-org:device:MediaServer:1\r\n" +
        "USN: uuid:%s::urn:schemas-upnp-org:device:MediaServer:1\r\n\r\n";
    String notifyStr = "NOTIFY * HTTP/1.1\r\n" +
        "Host: 239.255.255.250:1900\r\n" +
        "Cache-Control: max-age=1800\r\n" +
        "Location: %s\r\n" +
        "Server: %s\r\n" +
        "NTS: ssdp:alive\r\n" +
        "USN: uuid:%s::urn:schemas-upnp-org:device:MediaServer:1\r\n" +
        "NT: urn:schemas-upnp-org:device:MediaServer:1\r\n\r\n";
    String serverName = "Transmission BTC UPnP/1.0";
    Prefs prefs = transmission.getPrefs();
    uuid = prefs.getUUID();
    ok = String.format(okStr, locationUrl, serverName, uuid).getBytes(ASCII);
    notify = String.format(notifyStr, locationUrl, serverName, uuid).getBytes(ASCII);
    this.transmission = transmission;
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
    if (transmission.isSuspended()) return;
    sendMsg(notify);
  }

  private void sendByebye() {
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
    if (transmission.isSuspended()) return;

    if (isDebugEnabled()) {
      String req = new String(pkt.getData(), pkt.getOffset(), pkt.getLength(), ASCII);
      String resp = new String(ok, ASCII);
      debug(TAG, "SSDP request received:\n%s", req);
      debug(TAG, "Sending SSDP response:\n%s", resp);
    }

    try {
      DatagramPacket resp = new DatagramPacket(ok, ok.length, pkt.getAddress(), pkt.getPort());
      socket.send(resp);
    } catch (IOException ex) {
      err(TAG, ex, "Failed to send SSDP response");
    }
  }

  private boolean isDebugEnabled() {
    return false;
  }
}
