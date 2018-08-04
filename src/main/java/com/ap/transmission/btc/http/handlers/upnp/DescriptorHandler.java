package com.ap.transmission.btc.http.handlers.upnp;

import android.content.res.AssetManager;

import com.ap.transmission.btc.Prefs;
import com.ap.transmission.btc.Utils;
import com.ap.transmission.btc.http.HttpServer;
import com.ap.transmission.btc.http.Request;
import com.ap.transmission.btc.http.RequestHandler;
import com.ap.transmission.btc.http.handlers.AssetHandler;
import com.ap.transmission.btc.http.handlers.HandlerBase;
import com.ap.transmission.btc.http.handlers.StaticResourceHandler;
import com.ap.transmission.btc.torrent.Transmission;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Map;

import static com.ap.transmission.btc.BuildConfig.VERSION_NAME;
import static com.ap.transmission.btc.Utils.ASCII;

/**
 * @author Andrey Pavlenko
 */
public class DescriptorHandler implements RequestHandler {
  public static final String PATH = "/upnp/descriptor.xml";
  private final Prefs prefs;
  private Content content;

  public DescriptorHandler(Prefs prefs) {
    this.prefs = prefs;
  }

  public static void addHandlers(Map<String, RequestHandler> handlers, Transmission transmission) {
    AssetManager amgr = transmission.getPrefs().getContext().getAssets();
    String contentType = "text/xml; charset=\"utf-8\"";
    StaticResourceHandler h1 = new AssetHandler(amgr, "upnp/ContentDirectoryScpd.xml", contentType);
    StaticResourceHandler h2 = new AssetHandler(amgr, "upnp/ConnectionManagerScpd.xml", contentType);
    handlers.put(PATH, new DescriptorHandler(transmission.getPrefs()));
    handlers.put("/upnp/ContentDirectory/scpd.xml", h1);
    handlers.put("/upnp/ConnectionManager/scpd.xml", h2);
    handlers.put(ContentDirectoryHandler.PATH, new ContentDirectoryHandler());
  }

  @Override
  public void handle(HttpServer server, Request req, Socket socket) {
    new HandlerBase("DescriptorHandler", server, socket) {
      @Override
      protected void doHandle(Request req) throws IOException {
        byte[] content = getContent();
        OutputStream out = responseOk("text/xml; charset=\"utf-8\"", content.length,
            false);
        out.write(content);
        out.close();
      }
    }.handle(req);
  }

  private byte[] getContent() {
    Content cnt = content;

    if(cnt == null) {
      content = cnt = new Content(prefs);
    } else {
      String ip = Utils.getIPAddress(prefs.getContext());

      if (!((ip == null) ? (cnt.ip == null) : ip.equals(cnt.ip))) {
        content = cnt = new Content(prefs);
      }
    }

    return cnt.content;
  }

  private static final class Content {
    final String ip;
    final byte[] content;

    Content(Prefs prefs) {
      String uuid = prefs.getUUID();
      String suffix = ip = Utils.getIPAddress(prefs.getContext());
      if (suffix == null) suffix = uuid;
      String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
          "<root xmlns=\"urn:schemas-upnp-org:device-1-0\" xmlns:dlna=\"urn:schemas-dlna-org:device-1-0\">\n" +
          "  <specVersion>\n" +
          "    <major>1</major>\n" +
          "    <minor>1</minor>\n" +
          "  </specVersion>\n" +
          "  <device>\n" +
          "    <deviceType>urn:schemas-upnp-org:device:MediaServer:1</deviceType>\n" +
          "    <friendlyName>Transmission BTC (" + suffix + ")</friendlyName>\n" +
          "    <manufacturer>Andrey Pavlenko</manufacturer>\n" +
          "    <manufacturerURL>http://apavlenko.com/</manufacturerURL>\n" +
          "    <modelDescription>Transmission Bit Torrent Client</modelDescription>\n" +
          "    <modelName>Transmission BTC</modelName>\n" +
          "    <modelNumber>" + VERSION_NAME + "</modelNumber>\n" +
          "    <modelURL>http://apavlenko.com/</modelURL>\n" +
          "    <UDN>uuid:" + uuid + "</UDN>\n" +
          "    <dlna:X_DLNADOC xmlns:dlna=\"urn:schemas-dlna-org:device-1-0\">DMS-1.50</dlna:X_DLNADOC>\n" +
          "    <serviceList>\n" +
          "      <service>\n" +
          "        <serviceType>urn:schemas-upnp-org:service:ContentDirectory:1</serviceType>\n" +
          "        <serviceId>urn:upnp-org:serviceId:ContentDirectory</serviceId>\n" +
          "        <SCPDURL>/upnp/ContentDirectory/scpd.xml</SCPDURL>\n" +
          "        <controlURL>/upnp/ContentDirectory/control.xml</controlURL>\n" +
          "        <eventSubURL>/upnp/ContentDirectory/event.xml</eventSubURL>\n" +
          "      </service>\n" +
          "      <service>\n" +
          "        <serviceType>urn:schemas-upnp-org:service:ConnectionManager:1</serviceType>\n" +
          "        <serviceId>urn:upnp-org:serviceId:ConnectionManager</serviceId>\n" +
          "        <SCPDURL>/upnp/ConnectionManager/scpd.xml</SCPDURL>\n" +
          "        <controlURL>/upnp/ConnectionManager/control.xml</controlURL>\n" +
          "        <eventSubURL>/upnp/ConnectionManager/event.xml</eventSubURL>\n" +
          "      </service>\n" +
          "    </serviceList>\n" +
          "  </device>\n" +
          "</root>\n";
      content = xml.getBytes(ASCII);
    }
  }
}
