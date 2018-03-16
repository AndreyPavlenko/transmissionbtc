package com.ap.transmission.btc.http.handlers.upnp;

import com.ap.transmission.btc.Utils;
import com.ap.transmission.btc.http.HttpServer;
import com.ap.transmission.btc.http.handlers.SoapHandler;
import com.ap.transmission.btc.http.handlers.torrent.TorrentHandler;
import com.ap.transmission.btc.torrent.MediaInfo;
import com.ap.transmission.btc.torrent.NoSuchTorrentException;
import com.ap.transmission.btc.torrent.TorrentFile;
import com.ap.transmission.btc.torrent.TorrentFs;
import com.ap.transmission.btc.torrent.TorrentItem;
import com.ap.transmission.btc.torrent.TorrentItemContainer;
import com.ap.transmission.btc.torrent.Transmission;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static com.ap.transmission.btc.torrent.TorrentFs.sortByName;
import static javax.xml.XMLConstants.XMLNS_ATTRIBUTE_NS_URI;

/**
 * @author Andrey Pavlenko
 */
public class ContentDirectoryHandler extends SoapHandler {
  private static final String UPNP_NS = "urn:schemas-upnp-org:metadata-1-0/upnp/";
  private static final String DIDL_NS = "urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/";
  private static final String DC_NS = "http://purl.org/dc/elements/1.1/";
  public static final String PATH = "/upnp/ContentDirectory/control.xml";

  public ContentDirectoryHandler() {
    super(new HashMap<String, MessageHandler>(8), "ContentDirectory");
    handlers.put("Browse", new BrowseHandler());
  }

  protected static void setResponse(Document respDoc, Element respBody, String handlerName,
                                    String resultText, int num, int matches, int updateId) {
    Element envelop = (Element) respDoc.getFirstChild();
    Element response = respDoc.createElementNS(
        "urn:schemas-upnp-org:service:ContentDirectory:1",
        "u:" + handlerName + "Response");
    Element result = respDoc.createElement("Result");
    Element numReturned = respDoc.createElement("NumberReturned");
    Element total = respDoc.createElement("TotalMatches");
    Element updId = respDoc.createElement("UpdateID");

    envelop.setAttributeNS(SOAP_NS, "s:encodingStyle",
        "http://schemas.xmlsoap.org/soap/encoding/");
    respBody.appendChild(response);
    response.appendChild(result);
    response.appendChild(numReturned);
    response.appendChild(total);
    response.appendChild(updId);
    result.setTextContent(resultText);
    numReturned.setTextContent(String.valueOf(num));
    total.setTextContent(String.valueOf(matches));
    updId.setTextContent(String.valueOf(updateId));
  }

  private final class BrowseHandler implements MessageHandler {

    @Override
    public void handle(Handler handler, Document reqDoc, Element reqBody,
                       Document respDoc, Element respBody) {
      HttpServer server = handler.getHttpServer();
      Transmission tr = server.getTransmission();
      TorrentFs fs = tr.getTorrentFs();

      Element browse = (Element) reqBody.getFirstChild();
      Element objectId = handler.findChild(browse, "ObjectID");
      Element browseFlag = handler.findChild(browse, "BrowseFlag");
      Element startingIndex = handler.findChild(browse, "StartingIndex");
      Element requestedCount = handler.findChild(browse, "RequestedCount");
      String oid = (objectId == null) ? "0" : objectId.getTextContent();
      boolean browseChildren = (browseFlag != null) &&
          "BrowseDirectChildren".equals(browseFlag.getTextContent());
      int startIdx = (startingIndex == null) ? 0 : Integer.parseInt(startingIndex.getTextContent());
      int reqCount = (requestedCount == null) ? 0 : Integer.parseInt(requestedCount.getTextContent());
      if (reqCount == 0) reqCount = Integer.MAX_VALUE;

      Document didlDoc = docBuilder.newDocument();
      Element didl = didlDoc.createElementNS(DIDL_NS, "DIDL-Lite");
      didlDoc.appendChild(didl);
      didl.setAttributeNS(XMLNS_ATTRIBUTE_NS_URI, "xmlns:dc", DC_NS);
      didl.setAttributeNS(XMLNS_ATTRIBUTE_NS_URI, "xmlns:upnp", UPNP_NS);

      TorrentItem subjItem;
      List<TorrentItem> items;
      int updateId;

      if (oid.equals("0") || oid.equals("-1")) {
        subjItem = fs;
        fs.ls();
        updateId = fs.getUpdateId();
      } else {
        subjItem = fs.findItem(oid);
        updateId = 0;
      }

      if (subjItem instanceof TorrentItemContainer) {
        if (browseChildren) {
          items = ((TorrentItemContainer) subjItem).ls();
        } else {
          items = Collections.singletonList(subjItem);
        }
      } else if (subjItem != null) {
        items = Collections.singletonList(subjItem);
      } else {
        items = Collections.emptyList();
      }

      items = sortByName(items, true);
      int size = items.size();
      int counter = 0;
      int matches = browseChildren ? items.size() : 1;

      if ((size > 0) && (startIdx < size)) {
        int port = server.getPort();
        String host = handler.getServerHost(handler.getRequest());
        int max = Math.min(reqCount, size - startIdx);

        for (int i = startIdx; i < max; i++) {
          TorrentItem item = items.get(i);
          counter++;

          if (item instanceof TorrentFile) {
            TorrentFile f = (TorrentFile) item;
            addItem(didlDoc, didl, f, host, port);
          } else {
            addContainer(didlDoc, didl, (TorrentItemContainer) item);
          }
        }
      }

      String text = Utils.nodeToString(didlDoc);
      setResponse(respDoc, respBody, "Browse", text, counter, matches, updateId);
    }

    private void addContainer(Document didlDoc, Element didl, TorrentItemContainer item) {
      TorrentItemContainer parent = item.getParent();
      Element container = didlDoc.createElement("container");
      Element title = didlDoc.createElementNS(DC_NS, "dc:title");
      Element clas = didlDoc.createElementNS(UPNP_NS, "upnp:class");

      didl.appendChild(container);
      container.setAttribute("id", item.getId());
      container.setAttribute("parentID", (parent == null) ? "-1" : parent.getId());
      container.setAttribute("childCount", String.valueOf(item.ls().size()));
      container.setAttribute("restricted", "true");
      container.setAttribute("searchable", "false");
      container.appendChild(title);
      container.appendChild(clas);
      title.setTextContent(item.getName());
      clas.setTextContent("object.container.storageFolder");
    }

    private void addItem(Document didlDoc, Element didl, TorrentFile f, String host, int port) {
      TorrentItemContainer parent = f.getParent();
      String name = f.getName();
      String ext = Utils.getFileExtension(name);
      String uri = TorrentHandler.createUri(host, port, f.getTorrent().getHashString(),
          f.getIndex(), ext).toString();
      String[] mime = new String[]{f.getMimeType()};
      if (mime[0].isEmpty()) mime[0] = "*";

      Element item = didlDoc.createElement("item");
      Element title = didlDoc.createElementNS(DC_NS, "dc:title");
      Element res = didlDoc.createElement("res");
      String c = "object.item";

      didl.appendChild(item);
      item.setAttribute("id", f.getId());
      item.setAttribute("parentID", (parent == null) ? "-1" : parent.getId());
      item.setAttribute("restricted", "true");
      item.appendChild(title);
      item.appendChild(res);
      title.setTextContent(name);
      res.setTextContent(uri);

      try {
        res.setAttribute("size", String.valueOf(f.getLength()));
      } catch (NoSuchTorrentException ex) {
        f.getFs().reportNoSuchTorrent(ex);
        res.setAttribute("size", "0");
      }

      if (!"*".equals(mime[0])) {
        if (mime[0].startsWith("video/")) {
          c = "object.item.videoItem.movie";
          setVideoMeta(f, didlDoc, item, title, res, mime);
        } else if (mime[0].startsWith("audio/")) {
          if ("m3u".equals(ext)) {
            c = "object.item.audioItem.musicTrack";
            setAudioMeta(f, didlDoc, item, title, res, mime);
          } else {
            c = "object.item.playlist";
          }
        } else if (mime[0].startsWith("image/")) {
          c = "object.item.imageItem.photo";
        } else if (mime[0].startsWith("text/")) {
          c = "object.item.textItem";
        }
      }

      Element clas = didlDoc.createElementNS(UPNP_NS, "upnp:class");
      item.appendChild(clas);
      clas.setTextContent(c);
      res.setAttribute("protocolInfo", "http-get:*:" + mime[0] + ":DLNA.ORG_OP=01");
    }

    private void setVideoMeta(TorrentFile f, Document didlDoc, Element item, Element title,
                              Element res, String[] mime) {
      MediaInfo info = setMeta(f, didlDoc, item, title, res, mime);
      if (info == null) return;

      String v;

      if ((v = info.getResolution()) != null) {
        res.setAttribute("resolution", v);
      }
      if ((v = info.getGenre()) != null) {
        Element genre = didlDoc.createElementNS(UPNP_NS, "upnp:genre");
        genre.setTextContent(v);
        item.appendChild(genre);
      }
    }

    private void setAudioMeta(TorrentFile f, Document didlDoc, Element item, Element title,
                              Element res, String[] mime) {
      MediaInfo info = setMeta(f, didlDoc, item, title, res, mime);
      if (info == null) return;

      String v;

      if ((v = info.getAlbum()) != null) {
        Element album = didlDoc.createElementNS(UPNP_NS, "upnp:album");
        album.setTextContent(v);
        item.appendChild(album);
      }
      if ((v = info.getArtist()) != null) {
        Element artist = didlDoc.createElementNS(UPNP_NS, "upnp:artist");
        artist.setTextContent(v);
        item.appendChild(artist);
      }
    }

    private MediaInfo setMeta(TorrentFile f, Document didlDoc, Element item,
                              Element title, Element res, String[] mime) {
      MediaInfo info = f.getMediaInfo();
      if (info == null) return null;

      String v;

      if ((v = info.getMimeType()) != null) {
        mime[0] = v;
      }
      if ((v = info.getTitle()) != null) {
        title.setTextContent(v);
      }
      if ((v = info.getDate()) != null) {
        Element date = didlDoc.createElementNS(DC_NS, "dc:date");
        date.setTextContent(v);
        item.appendChild(date);
      }
      if ((v = info.getDuration()) != null) {
        res.setAttribute("duration", v);
      }

      return info;
    }
  }
}
