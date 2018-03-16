package com.ap.transmission.btc;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.ap.transmission.btc.Utils.copyAssets;

/**
 * @author Andrey Pavlenko
 */
@SuppressWarnings("unused")
public class Scripts {
  private final File scriptsDir;
  private static volatile Scripts instance;

  private Scripts(Context ctx) throws IOException {
    if (Utils.exec(15000, null, "su", "-c", "ls") != 0) {
      throw new IOException("Root privileges not granted");
    }

    File dataDir = new File(ctx.getApplicationInfo().dataDir);
    scriptsDir = new File(dataDir, "scripts");
    copyAssets(ctx.getAssets(), "scripts", dataDir);

    int status = Utils.exec(3000, null, "su", "-c",
        "chmod 777 '" + scriptsDir.getAbsolutePath() + "'/*");
    if (status != 0) {
      throw new IOException("Command failed: su -c chmod 777 '" +
          scriptsDir.getAbsolutePath() + "'/*");
    }
  }

  public static Scripts getInstance(Context ctx) throws IOException {
    Scripts i = instance;

    if (i == null) {
      synchronized (Scripts.class) {
        if ((i = instance) == null) {
          instance = i = new Scripts(ctx);
        }
      }
    }

    return i;
  }

  public int execScript(Script script, String... args) throws IOException {
    File f = new File(scriptsDir, script.toString() + ".sh");
    List<String> cmd = new ArrayList<>(3);
    StringBuilder scriptCmd = new StringBuilder(100);

    scriptCmd.append('\'').append(f.getAbsolutePath()).append('\'');
    for (String a : args) scriptCmd.append(' ').append(a);

    cmd.add("su");
    cmd.add("-c");
    cmd.add(scriptCmd.toString());
    return Utils.exec(3000, null, cmd);
  }

  public enum Script {
    set_so_buf, create_dir, create_file
  }
}
