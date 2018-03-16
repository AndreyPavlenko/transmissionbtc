package com.ap.transmission.btc;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.provider.DocumentFile;
import android.system.ErrnoException;
import android.system.Os;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.PopupMenu;

import com.ap.transmission.btc.activities.ActivityBase;
import com.ap.transmission.btc.func.Consumer;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import static android.content.Context.CONNECTIVITY_SERVICE;
import static android.content.Context.WIFI_SERVICE;
import static android.net.ConnectivityManager.TYPE_ETHERNET;
import static android.net.ConnectivityManager.TYPE_WIFI;
import static com.ap.transmission.btc.BuildConfig.BUILD_TYPE;
import static java.net.NetworkInterface.getNetworkInterfaces;

/**
 * @author Andrey Pavlenko
 */
public class Utils {
  public static final Charset ASCII = Charset.forName("US-ASCII");
  public static final Charset UTF8 = Charset.forName("UTF-8");
  private static final String TAG = Utils.class.getName();
  @SuppressWarnings("ConstantConditions")
  private static final boolean isPro = BUILD_TYPE.endsWith("pro");
  private static final boolean isBasic = BUILD_TYPE.endsWith("basic");
  private static final boolean isDebugEnabled = BUILD_TYPE.startsWith("debug");

  public static String getIPAddress(Context context) {
    InetAddress addr = getInterfaceAddress(context);
    return (addr == null) ? null : addr.getHostAddress();
  }

  public static InetAddress getInterfaceAddress(Context context) {
    Context ctx = context.getApplicationContext();
    ConnectivityManager cmgr = (ConnectivityManager) ctx.getSystemService(CONNECTIVITY_SERVICE);
    InetAddress result = null;

    if (cmgr != null) {
      NetworkInfo inf = cmgr.getActiveNetworkInfo();

      if (inf != null) {
        if (!inf.isConnected()) return null;

        switch (inf.getType()) {
          case TYPE_WIFI:
            result = getWiFiAddr(ctx);
            break;
          case TYPE_ETHERNET:
            result = getEthAddr();
        }
      }
    }

    if (result != null) return result;
    InetAddress ethResult = null;

    try {
      main:
      for (Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
           ifs.hasMoreElements(); ) {
        NetworkInterface i = ifs.nextElement();
        if (i.isLoopback() || i.isVirtual() || !i.isUp()) continue;
        boolean isEth = i.getName().startsWith("eth");

        for (Enumeration<InetAddress> addrs = i.getInetAddresses(); addrs.hasMoreElements(); ) {
          InetAddress addr = addrs.nextElement();

          if (isEth) {
            if (addr instanceof Inet6Address) {
              if (ethResult == null) ethResult = addr;
            } else {
              ethResult = addr;
              break main;
            }
          } else {
            if (addr instanceof Inet6Address) {
              if (result == null) result = addr;
            } else {
              result = addr;
            }
          }
        }
      }
    } catch (Exception ex) {
      Log.d(TAG, ex.getMessage(), ex);
    }

    return (ethResult != null) ? ethResult : result;
  }

  @SuppressLint("WifiManagerPotentialLeak")
  private static InetAddress getWiFiAddr(Context ctx) {
    WifiManager wmgr = (WifiManager) ctx.getSystemService(WIFI_SERVICE);

    if ((wmgr != null) && wmgr.isWifiEnabled()) {
      WifiInfo info = wmgr.getConnectionInfo();

      if (info.getNetworkId() != -1) {
        int ip = info.getIpAddress();

        if (ip != 0) {
          try {
            ByteBuffer buf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
            return InetAddress.getByAddress(null, buf.putInt(ip).array());
          } catch (UnknownHostException ex) {
            err(TAG, ex, "Failed to create InetAddress from int=%s", ip);
          }
        }
      }
    }

    return null;
  }

  private static InetAddress getEthAddr() {
    InetAddress ip6 = null;

    try {
      for (Enumeration<NetworkInterface> ifs = getNetworkInterfaces(); ifs.hasMoreElements(); ) {
        NetworkInterface i = ifs.nextElement();

        if (i.isLoopback() || i.isVirtual() || !i.isUp() || !i.getName().startsWith("eth")) {
          continue;
        }

        for (Enumeration<InetAddress> addrs = i.getInetAddresses(); addrs.hasMoreElements(); ) {
          InetAddress addr = addrs.nextElement();
          if (addr instanceof Inet6Address) ip6 = addr;
          else return addr;
        }
      }
    } catch (Exception ex) {
      Log.d(TAG, ex.getMessage(), ex);
    }

    return ip6;
  }

  @Nullable
  @SuppressLint("ObsoleteSdkInt")
  @TargetApi(VERSION_CODES.LOLLIPOP)
  public static String getRealDirPath(Context ctx, Uri dirUri) {
    if (VERSION.SDK_INT < VERSION_CODES.LOLLIPOP) return null;
    ParcelFileDescriptor pfd = null;
    DocumentFile dir = DocumentFile.fromTreeUri(ctx, dirUri);
    String rndName = "transmissionbtc-" + UUID.randomUUID() + ".tmp";
    DocumentFile f = dir.createFile("octet/stream", rndName);

    if ((f == null) || !f.exists()) {
      err(TAG, "getRealDirPath: failed to create temporary file");
      return null;
    }

    try {
      ContentResolver r = ctx.getContentResolver();
      pfd = r.openFileDescriptor(f.getUri(), "r");

      if (pfd != null) {
        String path = getDescriptorPath(pfd);
        File parent = new File(path).getParentFile();

        if (parent == null) err(TAG, "getRealDirPath: parent is null");
        else return parent.getAbsolutePath();
      } else {
        err(TAG, "getRealDirPath: pfd is null");
      }
    } catch (IOException | ErrnoException ex) {
      Log.e(TAG, "Failed to resolve real path: " + dirUri, ex);
    } finally {
      if (pfd != null) try { pfd.close(); } catch (IOException ignored) {}
      f.delete();
    }

    return null;
  }

  @TargetApi(VERSION_CODES.LOLLIPOP)
  public static String getDescriptorPath(ParcelFileDescriptor fd) throws ErrnoException {
    String path = Os.readlink("/proc/self/fd/" + fd.getFd());

    if (path.startsWith("/mnt/media_rw/")) {
      String p = "/storage" + path.substring(13);
      if (new File(p).exists()) path = p;
    }

    return path;
  }

  public static ActivityBase getActivity(View view) {
    return getActivity(view, ActivityBase.class);
  }

  public static <A extends Activity> A getActivity(View view, Class<A> type) {
    for (Context context = view.getContext(); ; ) {
      if (type.isInstance(context)) {
        return type.cast(context);
      } else if (context instanceof ContextWrapper) {
        context = ((ContextWrapper) context).getBaseContext();
      } else {
        Log.e(TAG, "Activity not found for: " + view);
        return null;
      }
    }
  }

  public static boolean hasWritePerms(File dir) {
    File f = null;

    try {
      f = File.createTempFile("transmission", ".tmp", dir);
      return true;
    } catch (IOException | SecurityException e) {
      return false;
    } finally {
      if (f != null) //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
  }

  public static boolean isPro() {
    return isPro;
  }

  public static boolean isBasic() {
    return isBasic;
  }

  public static boolean isDebugEnabled() {
    return isDebugEnabled;
  }

  public static void debug(String tag, String msg, Object... args) {
    if (isDebugEnabled()) Log.d(tag, format(msg, args));
  }

  public static void debug(String tag, Throwable ex, String msg, Object... args) {
    if (isDebugEnabled()) Log.d(tag, format(msg, args), ex);
  }

  public static void warn(String tag, String msg, Object... args) {
    Log.w(tag, format(msg, args));
  }

  public static void warn(String tag, Throwable ex, String msg, Object... args) {
    Log.w(tag, format(msg, args), ex);
  }

  public static void info(String tag, String msg, Object... args) {
    Log.w(tag, format(msg, args));
  }

  public static void info(String tag, Throwable ex, String msg, Object... args) {
    Log.w(tag, format(msg, args), ex);
  }

  public static void err(String tag, String msg, Object... args) {
    Log.e(tag, format(msg, args));
  }

  public static void err(String tag, Throwable ex, String msg, Object... args) {
    Log.e(tag, format(msg, args), ex);
  }

  public static String format(String msg, Object... args) {
    return (args != null) && (args.length > 0) ? String.format(msg, args) : msg;
  }

  public static void showMsg(View v, int msgId, Object... formatArgs) {
    String msg = v.getContext().getResources().getString(msgId, formatArgs);
    showMsg(v, msg);
  }

  public static void showMsg(View v, String msg) {
    Snackbar.make(v, msg, Snackbar.LENGTH_LONG).show();
  }

  public static void showErr(View v, int msgId, Object... formatArgs) {
    if (v == null) {
      err(TAG, new IllegalArgumentException(), "View is null");
      return;
    }

    Resources res = v.getContext().getResources();
    String msg = res.getString(msgId, formatArgs);
    int color = res.getColor(R.color.error);
    Snackbar.make(v, msg, Snackbar.LENGTH_LONG).setActionTextColor(color).show();
  }

  public static void mkdirs(File... dirs) {
    for (File dir : dirs) {
      if (!dir.exists() && !dir.mkdirs()) {
        Log.e(TAG, "Failed to create directory:" + dir.getAbsolutePath());
      }
    }
  }

  public static void rm(File fileOrDir) {
    if (fileOrDir.isDirectory()) {
      File[] files = fileOrDir.listFiles();
      if (files != null) for (File f : files) rm(f);
    } else {
      //noinspection ResultOfMethodCallIgnored
      fileOrDir.delete();
    }
  }

  public static void copyAssets(AssetManager amgr, String src, File dstDir) throws IOException {
    copyAssets(amgr, src, dstDir, false);
  }

  public static void copyAssets(AssetManager amgr, String src, File dstDir, boolean checkSum)
      throws IOException {
    if (checkSum) {
      String csFileName = src + '/' + "checksum.sha1";
      File csFile = new File(dstDir, csFileName);

      if (csFile.isFile()) {
        InputStream in1 = null;
        InputStream in2 = null;

        try {
          in1 = amgr.open(csFileName);
          in2 = new FileInputStream(csFile);
          Baos baos1 = new Baos(50);
          Baos baos2 = new Baos(50);
          transfer(in1, baos1);
          transfer(in2, baos2);

          if (Arrays.equals(baos1.buf(), baos2.buf())) {
            debug(TAG, "Assets are already copied: %s", src);
            return;
          } else {

          }
        } catch (IOException ex) {
          err(TAG, ex, "Failed to read asset %s", csFileName);
        } finally {
          close(in1, in2);
        }
      }
    }

    boolean ok = false;

    try {
      copyAssets(amgr, src, dstDir, new byte[4096]);
      ok = true;
    } finally {
      if (!ok) rm(dstDir);
    }
  }

  private static void copyAssets(AssetManager amgr, String src, File dstDir, byte[] buf)
      throws IOException {
    String ls[] = amgr.list(src);

    if (ls.length > 0) {
      for (String f : ls) {
        copyAssets(amgr, src + '/' + f, dstDir, buf);
      }
    } else {
      File dst = new File(dstDir, src);
      debug(TAG, "Copying assets %s -> %s", src, dst);
      mkdirs(dst.getParentFile());
      InputStream in = null;
      OutputStream out = null;

      try {
        in = amgr.open(src);
        out = new FileOutputStream(dst);

        for (int i = in.read(buf); i != -1; i = in.read(buf)) {
          out.write(buf, 0, i);
        }
      } finally {
        if (in != null) in.close();
        if (out != null) out.close();
      }
    }
  }

  public static int su(long timeout, final String script) throws IOException {
    return su(timeout, new ByteArrayInputStream(script.getBytes()));
  }

  public static int su(long timeout, final InputStream script) throws IOException {
    return exec(timeout, script, "su");
  }

  public static int exec(long timeout, final InputStream script, String... cmd) throws IOException {
    return exec(timeout, script, Arrays.asList(cmd));
  }

  public static int exec(long timeout, final InputStream script, List<String> cmd) throws IOException {
    ProcessBuilder pb = new ProcessBuilder(cmd);
    debug(TAG, "Executing %s", pb.command());
    final Process p = pb.start();
    Thread t = new Thread() {
      @Override
      public void run() {
        if (script != null) {
          OutputStream out = p.getOutputStream();
          byte[] buf = new byte[1024];

          try {
            for (int i = script.read(buf); i != -1; i = script.read(buf)) out.write(buf, 0, i);
          } catch (IOException ex) {
            Log.e(TAG, ex.getMessage(), ex);
          } finally {
            try { out.close(); } catch (IOException ignore) {}
          }
        }

        try {
          p.waitFor();
        } catch (InterruptedException ignore) {}
      }
    };

    try {
      t.start();
      t.join(timeout);

      if (t.isAlive()) {
        Log.e(TAG, "Command " + pb.command() + " not completed in " + timeout
            + " milliseconds - interrupting");
        t.interrupt();
        return -1;
      } else {
        int status = p.exitValue();
        if (status != 0)
          warn(TAG, "Command %s completed with exit code %d", pb.command(), status);
        return status;
      }
    } catch (InterruptedException ex) {
      Log.e(TAG, ex.getMessage(), ex);
      return -1;
    } finally {
      if (VERSION.SDK_INT >= VERSION_CODES.O) p.destroyForcibly();
      else p.destroy();
    }
  }

  @SuppressWarnings("unchecked")
  public static Collection<File> splitPath(File path, boolean readable) {
    Deque q = new LinkedList();
    q.addFirst(path);

    for (File p = path.getParentFile(); p != null; p = p.getParentFile()) {
      if (readable && !p.canRead()) break;
      q.addFirst(p);
    }

    return q;
  }

  public static void close(Socket... close) {
    if (close != null) {
      for (Socket c : close) {
        if (c != null) {
          try {
            c.close();
          } catch (IOException ignore) {}
        }
      }
    }
  }

  public static void close(Closeable... close) {
    if (close != null) {
      for (Closeable c : close) {
        if (c != null) {
          try {
            c.close();
          } catch (IOException ignore) {}
        }
      }
    }
  }

  public static void configureProxy(Prefs prefs) {
    if (prefs.isProxyEnabled()) {
      Native.envSet("all_proxy", prefs.getString(Prefs.K.PROXY_ALL));
      Native.envSet("http_proxy", prefs.getString(Prefs.K.PROXY_HTTP));
      Native.envSet("https_proxy", prefs.getString(Prefs.K.PROXY_HTTPS));
      Native.envSet("no_proxy", prefs.getString(Prefs.K.PROXY_NO));
    } else {
      Native.envUnset("all_proxy");
      Native.envUnset("http_proxy");
      Native.envUnset("https_proxy");
    }
  }

  public static AsyncTask<Void, Integer, Throwable> curl(final Context ctx, final Uri uri,
                                                         final File toFile, final Consumer<Throwable> callback) {
    String scheme = uri.getScheme();

    if (scheme != null) {
      switch (scheme) {
        case "http":
        case "https":
        case "ftp":
        case "ftps":
        case "smb":
        case "smbs":
          return new AsyncTask<Void, Integer, Throwable>() {

            @Override
            protected Throwable doInBackground(Void... params) {
              try {
                configureProxy(new Prefs(ctx));
                Native.curl(uri.toString(), toFile.getAbsolutePath(), 30);
                return null;
              } catch (Throwable ex) {
                return ex;
              }
            }

            @Override
            protected void onPostExecute(Throwable ex) {
              callback.accept(ex);
            }
          };
      }
    }

    final ContentResolver resolver = ctx.getContentResolver();
    return new AsyncTask<Void, Integer, Throwable>() {

      @Override
      protected Throwable doInBackground(Void... params) {
        InputStream in = null;
        OutputStream out = null;

        try {
          in = resolver.openInputStream(uri);
          if (in == null) return new IOException("Failed to open input stream: " + uri);
          out = new FileOutputStream(toFile);
          byte[] buf = new byte[4096];

          for (int i = in.read(buf); i != -1; i = in.read(buf)) {
            out.write(buf, 0, i);
          }

          return null;
        } catch (Throwable ex) {
          return ex;
        } finally {
          close(in, out);
        }
      }

      @Override
      protected void onPostExecute(Throwable ex) {
        callback.accept(ex);
      }
    };
  }

  public static int toPx(int dp) {
    DisplayMetrics metrics = Resources.getSystem().getDisplayMetrics();
    float px = dp * (metrics.densityDpi / 160f);
    return Math.round(px);
  }

  public static void runWithDelay(Runnable run, int delay) {
    final Handler handler = new Handler();
    handler.postDelayed(run, delay * 1000);
  }

  public static String getFileExtension(String fileName) {
    int idx = fileName.lastIndexOf('.');
    return ((idx == -1) || (idx == (fileName.length() - 1))) ? null : fileName.substring(idx + 1);
  }

  public static String getMimeTypeFromFileName(String fileName) {
    return getMimeTypeFromExtension(getFileExtension(fileName));
  }

  public static String getMimeTypeFromExtension(String fileExt) {
    return (fileExt == null) ? null : MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExt);
  }

  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  public static boolean isWifiEthActive(Context context, String allowedNetworks) {
    Context ctx = context.getApplicationContext();
    ConnectivityManager cmgr = (ConnectivityManager) ctx.getSystemService(CONNECTIVITY_SERVICE);
    if (cmgr == null) return true;
    NetworkInfo inf = cmgr.getActiveNetworkInfo();
    if ((inf == null) || !inf.isConnected()) return false;

    switch (inf.getType()) {
      case TYPE_WIFI:
        if ((allowedNetworks == null) || allowedNetworks.isEmpty()) return true;
        WifiManager wmgr = (WifiManager) ctx.getSystemService(WIFI_SERVICE);
        if (wmgr == null) return true;
        String ssid = wmgr.getConnectionInfo().getSSID();

        if ((ssid.length() > 2) && ssid.startsWith("\"") && ssid.endsWith("\"")) {
          ssid = ssid.substring(1, ssid.length() - 1);
        }

        for (String n : allowedNetworks.split(",")) {
          if (ssid.equals(n.trim())) return true;
        }

        return false;
      case TYPE_ETHERNET:
        return true;
      default:
        return false;
    }
  }

  @SuppressWarnings("unused")
  public static void transfer(InputStream in, OutputStream out) throws IOException {
    transfer(in, out, 0, Long.MAX_VALUE);
  }

  public static void transfer(InputStream in, OutputStream out, long off, long len) throws IOException {
    byte[] buf = new byte[(int) Math.min(len, 8192)];
    while (off > 0) off -= in.skip(off);

    for (int i = in.read(buf, 0, (int) Math.min(buf.length, len)); i != -1; ) {
      out.write(buf, 0, i);
      len -= i;
      if (len == 0) break;
      i = in.read(buf, 0, (int) Math.min(buf.length, len));
    }
  }

  public static void transfer(FileDescriptor src, FileDescriptor dst) throws IOException {
    FileInputStream fis = null;
    FileOutputStream fos = null;
    FileChannel sch = null;
    FileChannel och = null;

    try {
      fis = new FileInputStream(src);
      fos = new FileOutputStream(dst);
      sch = fis.getChannel();
      och = fos.getChannel();

      for (long off = 0, len = sch.size(); len > 0; ) {
        long n = sch.transferTo(off, len, och);
        off += n;
        len -= n;
      }
    } finally {
      close(fis, fos, sch, och);
    }
  }

  public static ByteBuffer readAll(InputStream in, int avgLen, int maxLen) throws IOException {
    byte[] buf = new byte[Math.min(avgLen, maxLen)];
    int off = 0;

    for (int i = in.read(buf, off, buf.length - off); i != -1; ) {
      off += i;
      if (off == maxLen) break;
      if (off == buf.length) buf = Arrays.copyOf(buf, Math.min(buf.length * 2, maxLen));
      i = in.read(buf, off, buf.length - off);
    }

    return ByteBuffer.wrap(buf, 0, off);
  }

  public static Document readXml(ByteBuffer xml) throws ParserConfigurationException,
      IOException, SAXException {
    return readXml(new ByteArrayInputStream(xml.array(), xml.position(), xml.limit()));
  }

  public static Document readXml(InputStream in) throws ParserConfigurationException,
      IOException, SAXException {
    DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
    f.setNamespaceAware(true);
    f.setExpandEntityReferences(true);
    DocumentBuilder b = f.newDocumentBuilder();
    return b.parse(in);
  }

  public static void writeXml(Node node, OutputStream out) throws TransformerException, IOException {
    Writer w = new OutputStreamWriter(out, UTF8);
    writeXml(node, w);
    w.flush();
  }

  public static void writeXml(Node node, Writer w) throws TransformerException {
    writeXml(node, w, false);
  }

  public static void writeXml(Node node, Writer w, boolean omitXmlDecl) throws TransformerException {
    Transformer t = TransformerFactory.newInstance().newTransformer();
    t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
    t.setOutputProperty(OutputKeys.INDENT, "yes");
    if (omitXmlDecl) t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
    t.transform(new DOMSource(node), new StreamResult(w));
  }

  public static String nodeToString(Node node) {
    StringWriter sw = new StringWriter();

    try {
      writeXml(node, sw, true);
    } catch (Exception ex) {
      err(TAG, ex, "nodeToString() failed");
    }

    return sw.toString();
  }

  public static StringBuilder bytesToString(long bytes, StringBuilder sb) {
    if (bytes < 1000) {
      return sb.append(bytes).append(" B");
    } else {
      String units = "kMGTPE";
      double div;
      int unit;

      if (bytes < 1000000L) {
        div = 10D;
        unit = 0;
      } else if (bytes < 1000000000L) {
        div = 10000D;
        unit = 1;
      } else if (bytes < 1000000000000L) {
        div = 10000000D;
        unit = 2;
      } else if (bytes < 1000000000000000L) {
        div = 10000000000D;
        unit = 3;
      } else if (bytes < 1000000000000000000L) {
        div = 10000000000000D;
        unit = 4;
      } else {
        div = 10000000000000000D;
        unit = 5;
      }

      long rnd = Math.round(bytes / div);

      if ((rnd % 100) == 0) {
        rnd = rnd / 100;

        if (rnd == 1000) {
          sb.append("1 ").append(units.charAt(unit + 1));
        } else {
          sb.append(rnd).append(' ').append(units.charAt(unit));
        }
      } else {
        sb.append(rnd / 100D).append(' ').append(units.charAt(unit));
      }

      return sb.append('B');
    }
  }

  public static void enableMenuIcons(PopupMenu popup) {
    try {
      Field f = PopupMenu.class.getDeclaredField("mPopup");
      f.setAccessible(true);
      Object h = f.get(popup);
      h.getClass().getDeclaredMethod("setForceShowIcon", boolean.class).invoke(h, true);
    } catch (Exception ignore) {ignore.printStackTrace();}
  }

  public static void openUri(Activity a, Uri uri, String mime) {
    debug(TAG, "Opening uri: %s, mime-type: %s", uri, mime);
    Intent intent = new Intent(Intent.ACTION_VIEW);
    intent.setDataAndType(uri, mime);
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    a.startActivity(Intent.createChooser(intent, a.getResources().getString(R.string.open_with)));
  }
}
