package com.ap.transmission.btc;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.os.StatFs;

import com.ap.transmission.btc.func.Function;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.JELLY_BEAN_MR2;
import static android.os.Build.VERSION_CODES.KITKAT;
import static com.ap.transmission.btc.Prefs.K.BOOT_DELAY;
import static com.ap.transmission.btc.Prefs.K.DOWNLOAD_DIR;
import static com.ap.transmission.btc.Prefs.K.ENABLE_PROXY;
import static com.ap.transmission.btc.Prefs.K.ENABLE_RPC;
import static com.ap.transmission.btc.Prefs.K.ENABLE_RPC_AUTH;
import static com.ap.transmission.btc.Prefs.K.ENABLE_RPC_WHITELIST;
import static com.ap.transmission.btc.Prefs.K.ENABLE_SEQ_DOWNLOAD;
import static com.ap.transmission.btc.Prefs.K.ENABLE_UPNP;
import static com.ap.transmission.btc.Prefs.K.ENABLE_WATCH_DIR;
import static com.ap.transmission.btc.Prefs.K.ENCR_MODE;
import static com.ap.transmission.btc.Prefs.K.FOREGROUND;
import static com.ap.transmission.btc.Prefs.K.HTTP_SERVER_PORT;
import static com.ap.transmission.btc.Prefs.K.INCREASE_SO_BUF;
import static com.ap.transmission.btc.Prefs.K.RPC_PASSWD;
import static com.ap.transmission.btc.Prefs.K.RPC_PORT;
import static com.ap.transmission.btc.Prefs.K.RPC_UNAME;
import static com.ap.transmission.btc.Prefs.K.RPC_WHITELIST;
import static com.ap.transmission.btc.Prefs.K.SETTINGS_DIR;
import static com.ap.transmission.btc.Prefs.K.START_ON_BOOT;
import static com.ap.transmission.btc.Prefs.K.UUID;
import static com.ap.transmission.btc.Prefs.K.WATCH_DIR;
import static com.ap.transmission.btc.Prefs.K.WATCH_INTERVAL;
import static com.ap.transmission.btc.Prefs.K.WIFI_ETH_ONLY;
import static com.ap.transmission.btc.Prefs.K.WIFI_SSID;

/**
 * @author Andrey Pavlenko
 */
public class Prefs {
  public enum K {
    SETTINGS_DIR(new Function<Prefs, Object>() {
      public Object apply(Prefs p) { return p.getDefaultSettingsDir(); }
    }),
    DOWNLOAD_DIR(new Function<Prefs, Object>() {
      public Object apply(Prefs p) { return p.getDefaultDownloadDir(); }
    }),
    PREV_DOWNLOAD_DIR(new Function<Prefs, Object>() {
      public Object apply(Prefs p) { return p.getDownloadDir(); }
    }),
    WATCH_DIR(new Function<Prefs, Object>() {
      public Object apply(Prefs p) { return p.getDefaultWatchDir(); }
    }),
    START_ON_BOOT(false),
    BOOT_DELAY(0),
    ENABLE_WATCH_DIR(false),
    WATCH_INTERVAL(0),
    ENABLE_RPC(true),
    RPC_PORT(9091),
    ENABLE_RPC_AUTH(false),
    RPC_UNAME(""),
    RPC_PASSWD(""),
    ENABLE_RPC_WHITELIST(false),
    RPC_WHITELIST("127.0.0.1,192.168.*.*"),
    INCREASE_SO_BUF(false),
    ENCR_MODE(EncrMode.Allow),
    ENABLE_PROXY(false),
    PROXY_ALL(""),
    PROXY_HTTP(""),
    PROXY_HTTPS(""),
    PROXY_NO(""),
    ENABLE_SEQ_DOWNLOAD(false),
    ENABLE_UPNP(false),
    HTTP_SERVER_PORT(9092),
    WIFI_ETH_ONLY(true),
    WIFI_SSID(""),
    FOREGROUND(true),
    UUID(new Function<Prefs, Object>() {
      public Object apply(Prefs p) {
        String uuid = java.util.UUID.randomUUID().toString();
        p.putString(UUID, uuid);
        return uuid;
      }
    });
    private final Function<Prefs, Object> func;
    private Object def;

    K(Function<Prefs, Object> func) {
      this.func = func;
    }

    K(Object def) {
      this.def = def;
      this.func = null;
    }

    public String id() {
      return name();
    }

    public String id(int index) {
      return (index == -1) ? id() : (id() + "_" + index);
    }

    @SuppressWarnings("unchecked")
    public <T> T def(Prefs p) {
      if ((def == null) && (func != null)) def = func.apply(p);
      return (T) def;
    }

    public <T> T def(Prefs p, @SuppressWarnings("unused") int index) {
      return def(p);
    }
  }

  private final Context ctx;
  private final SharedPreferences prefs;

  public Prefs(Context ctx) {
    this.ctx = ctx;
    prefs = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE);
  }

  public SharedPreferences getPrefs() {
    return prefs;
  }

  public Context getContext() {
    return ctx;
  }

  @SuppressWarnings("unchecked")
  public <T> T get(K k) {
    return (T) getObject(k);
  }

  @SuppressWarnings("unchecked")
  public <T> T get(K k, int index) {
    return (T) ((index == -1) ? get(k) : getString(k, index, null));
  }

  private Object getObject(K k) {
    switch (k) {
      case SETTINGS_DIR:
        return getSettingsDir();
      case DOWNLOAD_DIR:
        return getDownloadDir();
      case WATCH_DIR:
        return getWatchDir();
      case WATCH_INTERVAL:
        return getWatchInterval();
      case START_ON_BOOT:
        return isStartOnBoot();
      case BOOT_DELAY:
        return getBootDelay();
      case ENABLE_WATCH_DIR:
        return isWatchDirEnabled();
      case ENABLE_RPC:
        return isRpcEnabled();
      case RPC_PORT:
        return getRpcPort();
      case ENABLE_RPC_AUTH:
        return isRpcAuthEnabled();
      case RPC_UNAME:
        return getRpcUsername();
      case RPC_PASSWD:
        return getRpcPassword();
      case ENABLE_RPC_WHITELIST:
        return isRpcWhitelistEnabled();
      case RPC_WHITELIST:
        return getRpcWhitelist();
      case INCREASE_SO_BUF:
        return isIncreaseSoBuf();
      case ENCR_MODE:
        return getEncryptionMode();
      case ENABLE_PROXY:
        return isProxyEnabled();
      case ENABLE_SEQ_DOWNLOAD:
        return isSeqDownloadEnabled();
      case ENABLE_UPNP:
        return isUpnpEnabled();
      case HTTP_SERVER_PORT:
        return getHttpServerPort();
      case WIFI_ETH_ONLY:
        return isWifiEthOnly();
      case WIFI_SSID:
        return getWifiSsid();
      case FOREGROUND:
        return isForeground();
      default:
        return getString(k);
    }
  }

  public void set(K k, Object value) {
    if (value == null) {
      remove(k);
      return;
    }

    switch (k) {
      case SETTINGS_DIR:
        setSettingsDir(value.toString());
        break;
      case DOWNLOAD_DIR:
        setDownloadDir(value.toString());
        break;
      case START_ON_BOOT:
        setStartOnBoot((Boolean) value);
      case BOOT_DELAY:
        setBootDelay(value.toString());
        break;
      case ENABLE_WATCH_DIR:
        setWatchDirEnabled((Boolean) value);
        break;
      case WATCH_DIR:
        setWatchDir(value.toString());
        break;
      case WATCH_INTERVAL:
        setWatchInterval(value.toString());
        break;
      case ENABLE_RPC:
        setRpcEnabled((Boolean) value);
        break;
      case RPC_PORT:
        setRpcPort(value.toString());
        break;
      case ENABLE_RPC_AUTH:
        setRpcAuthEnabled((Boolean) value);
        break;
      case RPC_UNAME:
        setRpcUsername(value.toString());
        break;
      case RPC_PASSWD:
        setRpcPassword(value.toString());
        break;
      case ENABLE_RPC_WHITELIST:
        setRpcWhitelistEnabled((Boolean) value);
        break;
      case RPC_WHITELIST:
        setRpcWhitelist(value.toString());
        break;
      case INCREASE_SO_BUF:
        setIncreaseSoBuf((Boolean) value);
        break;
      case ENCR_MODE:
        setEncryptionMode((EncrMode) value);
        break;
      case ENABLE_PROXY:
        setProxyEnabled((Boolean) value);
        break;
      case ENABLE_SEQ_DOWNLOAD:
        setSeqDownloadEnabled((Boolean) value);
        break;
      case ENABLE_UPNP:
        setUpnpEnabled(Boolean.valueOf(value.toString()));
        break;
      case HTTP_SERVER_PORT:
        setHttpServerPort(value.toString());
        break;
      case WIFI_ETH_ONLY:
        setWifiEthOnly((Boolean) value);
        break;
      case WIFI_SSID:
        setWifiSsid(value.toString());
        break;
      case FOREGROUND:
        setForeground((Boolean) value);
        break;
      default:
        putString(k, value.toString());
    }
  }

  public void set(K k, Object value, int index) {
    if (index == -1) set(k, value);
    else if (value == null) removeString(k, index);
    else putString(k, value.toString(), index);
  }

  public void remove(K k) {
    prefs.edit().remove(k.id()).apply();
  }

  public String getSettingsDir() {
    String p = getString(SETTINGS_DIR);
    if (p == null) setSettingsDir(p = getDefaultSettingsDir());
    return p;
  }

  @SuppressLint("ObsoleteSdkInt")
  private String getDefaultSettingsDir() {
    return ctx.getApplicationInfo().dataDir + "/Config";
  }

  public void setSettingsDir(CharSequence dir) {
    putString(SETTINGS_DIR, dir);
  }

  public String getDownloadDir() {
    String p = getString(DOWNLOAD_DIR);
    if (p == null) setDownloadDir(p = getDefaultDownloadDir());
    return p;
  }

  @SuppressLint("ObsoleteSdkInt")
  private String getDefaultDownloadDir() {
    if (SDK_INT < KITKAT) {
      return ctx.getApplicationInfo().dataDir + "/Download";
    } else {
      File dir = selectMaxSize(ctx.getExternalFilesDirs(Environment.DIRECTORY_DOWNLOADS));
      return (dir == null) ? ctx.getApplicationInfo().dataDir + "/Download" : dir.getAbsolutePath();
    }
  }

  public void setDownloadDir(CharSequence dir) {
    putString(DOWNLOAD_DIR, dir);
  }

  public boolean isWatchDirEnabled() {
    if (Utils.isBasic()) {
      return getBoolean(ENABLE_WATCH_DIR);
    } else {
      return prefs.getString(K.WATCH_DIR.id(0), null) != null;
    }
  }

  public void setWatchDirEnabled(boolean enabled) {
    putBoolean(ENABLE_WATCH_DIR, enabled);
  }

  public String getWatchDir() {
    String p = getString(WATCH_DIR);
    if (p == null) setWatchDir(p = getDefaultWatchDir());
    return p;
  }

  @SuppressLint("ObsoleteSdkInt")
  private String getDefaultWatchDir() {
    String p;

    if (SDK_INT < KITKAT) {
      p = ctx.getApplicationInfo().dataDir;
    } else {
      File dir = selectMaxSize(ctx.getExternalCacheDirs());
      p = (dir == null) ? ctx.getApplicationInfo().dataDir : dir.getAbsolutePath();
    }

    return p + "/Watch";
  }


  public Map<String, String> getWatchDirs() {
    if (Utils.isBasic()) return Collections.singletonMap(getWatchDir(), getDownloadDir());
    Map<String, String> m = new HashMap<>();

    for (int i = 0; ; i++) {
      String w = prefs.getString(K.WATCH_DIR.id(i), null);

      if (w == null) {
        break;
      } else {
        m.put(w, prefs.getString(K.DOWNLOAD_DIR.id(i), null));
      }
    }

    if (m.isEmpty()) {
      String wd = prefs.getString(WATCH_DIR.id(), null);

      if (wd != null) { // Import from the basic app settings
        String dd = getDownloadDir();
        putString(WATCH_DIR, wd, 0);
        putString(DOWNLOAD_DIR, dd, 0);
        m.put(wd, dd);
      }
    }

    return m;
  }

  public void setWatchDir(CharSequence dir) {
    putString(WATCH_DIR, dir);
  }

  public int getWatchInterval() {
    return getInt(WATCH_INTERVAL);
  }

  public void setWatchInterval(CharSequence interval) {
    putInt(WATCH_INTERVAL, interval);
  }

  public boolean isIncreaseSoBuf() {
    return getBoolean(INCREASE_SO_BUF);
  }

  public void setIncreaseSoBuf(boolean enabled) {
    putBoolean(INCREASE_SO_BUF, enabled);
  }

  public boolean isStartOnBoot() {
    return getBoolean(START_ON_BOOT);
  }

  public void setStartOnBoot(boolean start) {
    putBoolean(START_ON_BOOT, start);
  }

  public int getBootDelay() {
    return getInt(BOOT_DELAY);
  }

  public void setBootDelay(CharSequence delay) {
    putInt(BOOT_DELAY, delay);
  }

  public EncrMode getEncryptionMode() {
    return EncrMode.get(prefs.getInt(ENCR_MODE.id(), 0));
  }

  public void setEncryptionMode(EncrMode mode) {
    putInt(ENCR_MODE, mode.ordinal());
  }

  public boolean isRpcEnabled() {
    return getBoolean(ENABLE_RPC);
  }

  public void setRpcEnabled(boolean enabled) {
    putBoolean(ENABLE_RPC, enabled);
  }

  public int getRpcPort() {
    return getInt(RPC_PORT);
  }

  public void setRpcPort(CharSequence port) {
    putInt(RPC_PORT, port);
  }

  public boolean isRpcAuthEnabled() {
    return getBoolean(ENABLE_RPC_AUTH);
  }

  public void setRpcAuthEnabled(boolean enabled) {
    putBoolean(ENABLE_RPC_AUTH, enabled);
  }

  public String getRpcUsername() {
    return getString(RPC_UNAME);
  }

  public void setRpcUsername(CharSequence name) {
    putString(RPC_UNAME, name);
  }

  public String getRpcPassword() {
    return getString(RPC_PASSWD);
  }

  public void setRpcPassword(CharSequence passwd) {
    putString(RPC_PASSWD, passwd);
  }

  public boolean isRpcWhitelistEnabled() {
    return getBoolean(ENABLE_RPC_WHITELIST);
  }

  public void setRpcWhitelistEnabled(boolean enabled) {
    putBoolean(ENABLE_RPC_WHITELIST, enabled);
  }

  public String getRpcWhitelist() {
    return getString(RPC_WHITELIST);
  }

  public void setRpcWhitelist(CharSequence list) {
    putString(RPC_WHITELIST, list);
  }

  public boolean isProxyEnabled() {
    return getBoolean(ENABLE_PROXY);
  }

  public void setProxyEnabled(boolean enabled) {
    putBoolean(ENABLE_PROXY, enabled);
  }

  public boolean isSeqDownloadEnabled() {
    return getBoolean(ENABLE_SEQ_DOWNLOAD);
  }

  public void setUpnpEnabled(boolean enabled) {
    putBoolean(ENABLE_UPNP, enabled);
  }

  public boolean isUpnpEnabled() {
    return getBoolean(ENABLE_UPNP);
  }

  public void setSeqDownloadEnabled(boolean enabled) {
    putBoolean(ENABLE_SEQ_DOWNLOAD, enabled);
  }

  public int getHttpServerPort() {
    return getInt(HTTP_SERVER_PORT);
  }

  public void setHttpServerPort(CharSequence port) {
    putInt(HTTP_SERVER_PORT, port);
  }

  public boolean isWifiEthOnly() {
    return getBoolean(WIFI_ETH_ONLY);
  }

  public void setWifiEthOnly(boolean wifiEthOnly) {
    putBoolean(WIFI_ETH_ONLY, wifiEthOnly);
  }

  public String getWifiSsid() {
    return getString(WIFI_SSID);
  }

  public void setWifiSsid(CharSequence list) {
    putString(WIFI_SSID, list.toString().trim());
  }

  public boolean isForeground() {
    return getBoolean(FOREGROUND);
  }

  public void setForeground(boolean foreground) {
    putBoolean(FOREGROUND, foreground);
  }

  public String getUUID() {
    return getString(UUID);
  }

  public String getString(K k) {
    String s = prefs.getString(k.id(), null);
    return (s == null) || s.isEmpty() ? k.<String>def(this) : s;
  }

  public String getString(K k, int index, String def) {
    return prefs.getString(k.id(index), def);
  }

  public void putString(K k, CharSequence s) {
    prefs.edit().putString(k.id(), s.toString()).apply();
  }

  public void putString(K k, CharSequence s, int index) {
    prefs.edit().putString(k.id(index), s.toString()).apply();
  }

  public void removeString(K k, int index) {
    SharedPreferences.Editor e = prefs.edit();
    e.remove(k.id(index));

    for (int i = index + 1; ; i++) {
      String id = k.id(i);
      String s = prefs.getString(id, null);

      if (s == null) {
        break;
      } else {
        e.remove(id);
        e.putString(k.id(i - 1), s);
      }
    }

    e.apply();
  }

  private boolean getBoolean(K k) {
    return prefs.getBoolean(k.id(), k.<Boolean>def(this));
  }

  private void putBoolean(K k, boolean b) {
    prefs.edit().putBoolean(k.id(), b).apply();
  }

  private int getInt(K k) {
    return prefs.getInt(k.id(), k.<Integer>def(this));
  }

  private void putInt(K k, CharSequence value) {
    try {
      putInt(k, Integer.parseInt(value.toString()));
    } catch (NumberFormatException ignore) {}
  }

  private void putInt(K k, int value) {
    prefs.edit().putInt(k.id(), value).apply();
  }

  public int getMaxIndex(K k) {
    int i = 0;
    for (; prefs.contains(k.id(i)); i++) ;
    return i;
  }

  @SuppressLint("ObsoleteSdkInt")
  private static File selectMaxSize(File[] files) {
    if (SDK_INT < JELLY_BEAN_MR2) return (files.length > 0) ? files[0] : null;
    File max = null;
    long maxLen = 0;

    for (File f : files) {
      try {
        if (f == null) continue;
        StatFs stat = new StatFs(f.getAbsolutePath());
        if (maxLen < stat.getTotalBytes()) max = f;
      } catch (IllegalArgumentException ignore) {}
    }

    return max;
  }
}
