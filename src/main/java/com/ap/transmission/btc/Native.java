package com.ap.transmission.btc;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.support.annotation.Keep;
import android.support.annotation.Nullable;
import android.util.Log;

import com.ap.transmission.btc.torrent.DuplicateTorrentException;
import com.ap.transmission.btc.torrent.NoSuchTorrentException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author Andrey Pavlenko
 */
public class Native {
  private static volatile Runnable torrentAddedOrChangedCallback;
  private static volatile Runnable torrentStoppedCallback;
  private static volatile Runnable sessionChangedCallback;
  private static volatile Runnable scheduledAltSpeedCallback;

  static {
    Init.init(null);
  }

  public static native String transmissionVersion();

  public static native long transmissionStart(String configDir, String downloadsDir,
                                              int encrMode,
                                              boolean enableRpc, int rpcPort,
                                              boolean enableAuth, String username, String password,
                                              boolean enableRpcWhitelist, String rpcWhitelist,
                                              boolean loadConfig, boolean enableSequential,
                                              boolean paused) throws IOException;

  public static native void transmissionStop(long session, String configDir);

  public static native void transmissionSuspend(long session, boolean suspend);

  public static native boolean transmissionHasDownloadingTorrents(long session);

  public static native String[] transmissionListTorrentNames(long session);

  public static native int transmissionGetEncryptionMode(long session);

  public static void transmissionSetRpcCallbacks(Runnable torrentAddedOrChanged,
                                                 Runnable torrentStopped,
                                                 Runnable sessionChanged,
                                                 Runnable scheduledAltSpeed) {
    torrentAddedOrChangedCallback = torrentAddedOrChanged;
    torrentStoppedCallback = torrentStopped;
    sessionChangedCallback = sessionChanged;
    scheduledAltSpeedCallback = scheduledAltSpeed;
  }

  /**
   * @return Returns:<br/>
   * 0 - OK
   * 1 - PARSE_ERR
   * 2 - DUPLICATE
   * 3 - OK_DELETE
   */
  public static native int torrentAdd(long session, String path, String downloadDir,
                                      boolean delete, boolean sequential,
                                      @Nullable int[] unwantedIndexes,
                                      @Nullable byte[] returnMeTorrentHash);

  public static native void torrentRemove(long session, int torrentId, boolean removeLocalData)
      throws NoSuchTorrentException;

  public static native void torrentStop(long session, int torrentId) throws NoSuchTorrentException;

  public static native void torrentStart(long session, int torrentId) throws NoSuchTorrentException;

  public static native void torrentVerify(long session, int torrentId) throws NoSuchTorrentException;

  public static native String[] torrentListFilesFromFile(String torrent) throws IOException;

  public static native String[] torrentListFiles(long session, int torrentId) throws NoSuchTorrentException;

  public static native void torrentMagnetToTorrentFile(long session, long sem, String magnet,
                                                       String path, int timeout, boolean[] enqueue)
      throws IOException, DuplicateTorrentException;

  public static native int torrentFindByHash(long session, byte[] torrentHash)
      throws NoSuchTorrentException;

  public static native void torrentSetPiecesHiPri(long session, int torrentId,
                                                  long firstPiece, long lastPiece)
      throws NoSuchTorrentException;

  public static native String torrentGetName(long session, int torrentId)
      throws NoSuchTorrentException;

  public static native void torrentGetHash(long session, int torrentId, byte[] torrentHash)
      throws NoSuchTorrentException;

  public static native void torrentGetPieceHash(long session, int torrentId,
                                                long piece, byte[] pieceHash)
      throws NoSuchTorrentException;

  public static native void torrentSetDnd(long session, int torrentId, int[] files, boolean dnd)
      throws NoSuchTorrentException;

  public static native String torrentFindFile(long session, int torrentId, int fileIndex)
      throws NoSuchTorrentException;

  public static native String torrentGetFileName(long session, int torrentId, int fileIndex)
      throws NoSuchTorrentException;

  public static native long[] torrentGetFileStat(long session, int torrentId, int fileIndex,
                                                 long[] stat) throws NoSuchTorrentException;

  public static native void torrentGetPiece(long session, int torrentId, long pieceIndex, byte[] dst,
                                            int offset, int len) throws NoSuchTorrentException, IOException;

  public static native void torrentSetLocation(long session, int torrentId, String path)
      throws NoSuchTorrentException;

  public static native long[] torrentStatBrief(long session, long[] stat);

  public static native String torrentGetError(long session, int torrentId);

  public static native void envSet(String name, String value);

  public static native void envUnset(String name);

  public static native long semCreate();

  public static native void semDestroy(long sem);

  public static native void semPost(long sem);

  public static native void stdRedirect();

  public static native void curl(String url, String dst, int timeout) throws IOException;

  public static native int hashLength();

  public static native String hashBytesToString(byte[] hash);

  public static native byte[] hashStringToBytes(String hashString);

  public static native byte[] hashGetTorrentHash(String torrentPath) throws IOException;

  public static native void nativeToJavaInit();

  @Keep
  @SuppressWarnings("unused")
  private static void torrentAddedOrChangedCallback() {
    Runnable r = torrentAddedOrChangedCallback;
    if (r != null) r.run();
  }

  @Keep
  @SuppressWarnings("unused")
  private static void torrentStoppedCallback() {
    Runnable r = torrentStoppedCallback;
    if (r != null) r.run();
  }

  @Keep
  @SuppressWarnings("unused")
  private static void sessionChangedCallback() {
    Runnable r = sessionChangedCallback;
    if (r != null) r.run();
  }

  @Keep
  @SuppressWarnings("unused")
  private static void scheduledAltSpeedCallback() {
    Runnable r = scheduledAltSpeedCallback;
    if (r != null) r.run();
  }

  public static final class Init {
    private static final String LIB_NAME = "transmissionbtc";
    private static boolean initialized;

    @SuppressLint("UnsafeDynamicallyLoadedCode")
    public static synchronized void init(Context ctx) {
      if (initialized) return;
      initialized = true;
      StorageAccess.init(ctx);

      try {
        System.loadLibrary(LIB_NAME);
        initNativeContext();
      } catch (UnsatisfiedLinkError err) {
        if (ctx == null) throw err;
        String libName = System.mapLibraryName(LIB_NAME);
        File libDir = new File(ctx.getApplicationInfo().dataDir, "lib");
        File libFile = new File(libDir, libName);

        if (!libFile.isFile()) {
          ZipFile zip = null;
          InputStream in = null;
          OutputStream out = null;

          try {
            ApplicationInfo appInfo = ctx.getApplicationInfo();
            zip = new ZipFile(new File(appInfo.sourceDir), ZipFile.OPEN_READ);

            for (String a : getSupportedAbis()) {
              String path = "lib/" + a + '/' + libName;
              ZipEntry e = zip.getEntry(path);
              if (e == null) continue;
              Log.i(Init.class.getName(), "Extracting native library " + path + " to " + libDir);
              in = zip.getInputStream(e);
              //noinspection ResultOfMethodCallIgnored
              libDir.mkdirs();
              out = new FileOutputStream(libFile);
              byte[] buf = new byte[4096];
              for (int i = in.read(buf); i != -1; i = in.read(buf)) out.write(buf, 0, i);
              System.load(libFile.getAbsolutePath());
              initNativeContext();
              return;
            }
          } catch (IOException e) {
            Log.e(Init.class.getName(), "Failed to extract libs", e);
            throw err;
          } finally {
            if (in != null) try { in.close(); } catch (IOException ignored) {}
            if (out != null) try { out.close(); } catch (IOException ignored) {}
            if (zip != null) try { zip.close(); } catch (IOException ignored) {}
          }
        }

        throw err;
      }
    }

    @SuppressLint("ObsoleteSdkInt")
    @SuppressWarnings("deprecation")
    private static String[] getSupportedAbis() {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
        return new String[]{Build.CPU_ABI, Build.CPU_ABI2};
      } else {
        return Build.SUPPORTED_ABIS;
      }
    }

    private static void initNativeContext() {
      nativeToJavaInit();
      envSet("TR_CURL_SSL_NO_VERIFY", "true");
      Native.envSet("TR_CURL_PROXY_SSL_NO_VERIFY", "true");

      //noinspection ConstantConditions
      if (BuildConfig.BUILD_TYPE.startsWith("debug")) {
        // envSet("TR_CURL_VERBOSE", "1");
        stdRedirect();
      }
    }
  }
}
