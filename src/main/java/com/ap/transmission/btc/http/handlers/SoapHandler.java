package com.ap.transmission.btc.http.handlers;

import com.ap.transmission.btc.Baos;
import com.ap.transmission.btc.Utils;
import com.ap.transmission.btc.http.HttpServer;
import com.ap.transmission.btc.http.Method;
import com.ap.transmission.btc.http.Request;
import com.ap.transmission.btc.http.RequestHandler;
import com.ap.transmission.btc.http.Response;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import static com.ap.transmission.btc.Utils.UTF8;
import static com.ap.transmission.btc.Utils.isDebugEnabled;
import static com.ap.transmission.btc.Utils.readXml;
import static com.ap.transmission.btc.Utils.writeXml;

/**
 * @author Andrey Pavlenko
 */
public class SoapHandler implements RequestHandler {
  public static final String SOAP_NS = "http://schemas.xmlsoap.org/soap/envelope/";
  protected final Map<String, MessageHandler> handlers;
  protected final String logTag;
  protected final DocumentBuilder docBuilder;
  private int maxLen = 64;

  public SoapHandler(Map<String, MessageHandler> handlers, String logTag) {
    this.handlers = handlers;
    this.logTag = logTag;

    DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
    try {
      docBuilder = f.newDocumentBuilder();
    } catch (ParserConfigurationException ex) {
      throw new RuntimeException(ex);
    }
  }

  protected MessageHandler getHandler(String name) {
    return handlers.get(name);
  }

  @Override
  public void handle(HttpServer server, Request req, Socket socket) {
    new Handler(server, socket).handle(req);
  }

  public interface MessageHandler {
    void handle(Handler handler, Document reqDoc, Element reqBody,
                Document respDoc, Element respBody) throws Throwable;
  }

  protected class Handler extends HandlerBase {
    private Request request;

    protected Handler(HttpServer server, Socket socket) {
      super(logTag, server, socket);
    }

    public Request getRequest() {
      return request;
    }

    public void addFault(Document respDoc, Element respBody, String msg, Throwable ex) {
      warn(ex, "Failed to handle message: %s", msg);
      Element fault = respDoc.createElementNS(SOAP_NS, "s:Fault");
      Element faultcode = respDoc.createElementNS(SOAP_NS, "s:faultcode");
      Element faultstring = respDoc.createElementNS(SOAP_NS, "s:faultstring");
      faultcode.setTextContent("Server");
      faultstring.setTextContent(msg);
      respBody.appendChild(fault);
      fault.appendChild(faultcode);
      fault.appendChild(faultstring);
    }

    public Element findChild(Node n, String name) {
      NodeList list = n.getChildNodes();
      int count = list.getLength();

      for (int i = 0; i < count; i++) {
        Node c = list.item(i);

        if (c instanceof Element) {
          if (c.getLocalName().equals(name)) {
            return (Element) c;
          }
        }
      }

      return null;
    }

    @Override
    protected void doHandle(Request req) throws IOException {
      if (req.getMethod() != Method.POST) {
        fail(Response.BadRequest.instance, "Unexpected request method: %s", req.getMethod());
        return;
      }

      ByteBuffer payload = req.getPayload();

      if (payload == null) {
        fail(Response.BadRequest.instance, "Request payload is empty");
        return;
      }

      Document doc;

      try {
        doc = readXml(payload);
      } catch (Exception ex) {
        fail(Response.BadRequest.instance, ex, "Failed to parse request message");
        return;
      }

      if (isDebugEnabled()) debug("Handling request:\n%s", Utils.nodeToString(doc));

      Element envelope = findChild(doc, "Envelope");

      if (envelope == null) {
        fail(Response.BadRequest.instance, "No <Envelope> element");
        return;
      }

      Element body = findChild(envelope, "Body");

      if (body == null) {
        fail(Response.BadRequest.instance, "No <Body> element");
        return;
      }

      if (body.getFirstChild() == null) {
        fail(Response.BadRequest.instance, "<Body> element is empty");
        return;
      }

      request = req;
      handleMessage(doc, body);
    }

    private void handleMessage(Document reqDoc, Element reqBody) throws IOException {
      Document respDoc = docBuilder.newDocument();
      Element envelope = respDoc.createElementNS(SOAP_NS, "s:Envelope");
      Element respBody = respDoc.createElementNS(SOAP_NS, "s:Body");
      respDoc.appendChild(envelope);
      envelope.appendChild(respBody);

      String handlerName = reqBody.getFirstChild().getLocalName();
      MessageHandler h = getHandler(handlerName);

      if (h == null) {
        addFault(respDoc, respBody, "No such handler: " + handlerName, null);
      } else {
        try {
          h.handle(this, reqDoc, reqBody, respDoc, respBody);
        } catch (Throwable ex) {
          addFault(respDoc, respBody, "Handler failed: " + handlerName, ex);
        }
      }

      Baos baos = new Baos(maxLen);

      try {
        writeXml(respDoc, baos);
      } catch (Exception ex) {
        fail(Response.ServerError.instance, ex, "writeXml() failed");
        return;
      }

      ByteBuffer buf = baos.byteBuf();
      maxLen = Math.max(maxLen, buf.remaining());

      if (isDebugEnabled()) {
        debug("Sending response:\n%s", new String(buf.array(),
            buf.position(), buf.remaining(), UTF8));
      }

      OutputStream out = responseOk("text/xml; charset=\"utf-8\"", buf.remaining(), false);
      out.write(buf.array(), buf.position(), buf.remaining());
    }
  }
}
