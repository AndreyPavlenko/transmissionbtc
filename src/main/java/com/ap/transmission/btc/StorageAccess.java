package com.ap.transmission.btc;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.support.annotation.Keep;
import android.support.v4.provider.DocumentFile;
import android.util.SparseArray;

import java.io.File;
import java.util.Deque;
import java.util.LinkedList;

import static com.ap.transmission.btc.Utils.close;
import static com.ap.transmission.btc.Utils.debug;
import static com.ap.transmission.btc.Utils.err;
import static com.ap.transmission.btc.Utils.warn;

/**
 * @author Andrey Pavlenko
 */
public class StorageAccess {
  private static final String TAG = StorageAccess.class.getName();
  private static final SparseArray<ParcelFileDescriptor> openFDs = new SparseArray<>();
  @SuppressLint("StaticFieldLeak")
  private static volatile Context context;
  private static volatile SharedPreferences pathToUrlMappings;

  public static void init(Context ctx) {
    context = ctx.getApplicationContext();
    pathToUrlMappings = ctx.getSharedPreferences("pathToUrlMappings", Context.MODE_PRIVATE);
  }

  public static void addMapping(String path, String url) {
    pathToUrlMappings.edit().putString(path, url).apply();
  }

  @Keep
  public static boolean createDir(String path) {
    return (findOrCreateDocumentDir(path, true) != null);
  }

  public static DocumentFile findOrCreateDocumentDir(String path, boolean create) {
    File p = new File(path);
    SharedPreferences map = pathToUrlMappings;
    Deque<String> names = new LinkedList<>();
    DocumentFile root = null;

    for (File dir = p; dir != null; dir = dir.getParentFile()) {
      String uri = map.getString(dir.getAbsolutePath(), null);

      if (uri == null) {
        names.addFirst(dir.getName());
      } else {
        root = DocumentFile.fromTreeUri(context, Uri.parse(uri));
        break;
      }
    }

    if (root != null) {
      DocumentFile dir = root;

      for (String n : names) {
        DocumentFile d = dir.findFile(n);

        if (d == null) {
          if (create) {
            d = dir.createDirectory(n);

            if (d == null) {
              err(TAG, "Failed to create directory: %s", path);
              return null;
            } else {
              debug(TAG, "Directory created: %s", path);
            }
          } else {
            debug(TAG, "Directory not found: %s", path);
            return null;
          }
        }

        dir = d;
      }

      return dir;
    }

    if (create) warn(TAG, "Failed to create directory: %s", path);
    else debug(TAG, "Directory not found: %s", path);
    return null;
  }

  @Keep
  @SuppressWarnings("unused")
  public static int openFile(String path, boolean create, boolean writable, boolean truncate) {
    DocumentFile df = findOrCreateDocumentFile(path, create);

    if (df != null) {
      String mode = truncate ? "rwt" : writable ? "rw" : "r";

      try {
        ParcelFileDescriptor pfd = context.getContentResolver().
            openFileDescriptor(df.getUri(), mode);

        if (pfd != null) {
          int fd = pfd.getFd();
          synchronized (openFDs) { openFDs.put(fd, pfd); }
          debug(TAG, "File opened: %s, fd=%d", path, fd);
          return fd;
        }
      } catch (Exception ex) {
        err(TAG, ex, "Failed to open file descriptor: %s", path);
        return -1;
      }
    }

    if (create) err(TAG, "Failed to create file: %s", path);
    return -1;
  }

  public static DocumentFile findOrCreateDocumentFile(String path, boolean create) {
    File f = new File(path);
    DocumentFile dir = findOrCreateDocumentDir(f.getParent(), create);

    if (dir != null) {
      DocumentFile df = dir.findFile(f.getName());

      if (df == null) {
        if (create) {
          df = dir.createFile(null, f.getName());

          if (df != null) {
            debug(TAG, "File created: %s", path);
            return df;
          } else {
            err(TAG, "Failed to create file: %s", path);
            return null;
          }
        } else {
          debug(TAG, "No such file: %s", path);
          return null;
        }
      } else {
        return df;
      }
    } else {
      return null;
    }
  }

  @Keep
  @SuppressWarnings("unused")
  public static boolean closeFileDescriptor(int fd) {
    ParcelFileDescriptor pfd;

    synchronized (openFDs) {
      pfd = openFDs.get(fd);
      if (pfd != null) openFDs.remove(fd);
    }

    if (pfd != null) {
      close(pfd);
      debug(TAG, "File descriptor closed: %d", fd);
      return true;
    } else {
      return false;
    }
  }

  @Keep
  @SuppressWarnings("unused")
  public static boolean renamePath(String srcPath, String dstPath) {
    debug(TAG, "Renaming %s to %s", srcPath, dstPath);
    File srcFile = new File(srcPath);

    if (srcFile.isFile()) {
      DocumentFile srcDoc = findOrCreateDocumentFile(srcPath, false);

      if (srcDoc == null) {
        err(TAG, "Source document file not found: %s", srcPath);
        return false;
      }

      File dstFile = new File(dstPath);
      if (dstFile.exists()) deleteExistingFile(dstPath);

      if (dstFile.getParent().equals(srcFile.getParent())) {
        if (srcDoc.renameTo(dstFile.getName())) {
          debug(TAG, "File %s renamed to %s", srcPath, dstPath);
          return true;
        }
      } else {
        DocumentFile dstDoc = findOrCreateDocumentFile(dstPath, true);
        return (dstDoc != null) && moveDocumentFile(srcDoc, dstDoc);
      }
    } else if (srcFile.isDirectory()) {
      DocumentFile srcDocDir = findOrCreateDocumentDir(srcPath, false);

      if (srcDocDir == null) {
        err(TAG, "Source document directory not found: %s", srcPath);
        return false;
      }

      DocumentFile dstDocDir = findOrCreateDocumentDir(dstPath, true);
      return (dstDocDir != null) && moveDocumentDir(srcDocDir, dstDocDir);
    } else if (!srcFile.exists()) {
      warn(TAG, "Source file does not exist: %s", srcPath);
      return false;
    } else {
      warn(TAG, "Unexpected source file: %s", srcPath);
      return false;
    }

    err(TAG, "Failed to rename path %s to %s", srcPath, dstPath);
    return false;
  }

  @Keep
  @SuppressWarnings("unused")
  public static boolean removePath(String path) {
    debug(TAG, "Deleting %s", path);
    DocumentFile doc = findOrCreateDocumentFile(path, false);

    if (doc == null) {
      warn(TAG, "Document not found: %s", path);
      return false;
    }

    return deleteExistingDocumentFile(doc);
  }

  private static boolean deleteExistingFile(String path) {
    DocumentFile doc = findOrCreateDocumentFile(path, false);

    if (doc == null) {
      err(TAG, "Existing document file not found: %s", path);
      return false;
    }

    debug(TAG, "Deleting %s", path);
    return deleteExistingDocumentFile(doc);
  }

  private static boolean deleteExistingDocumentFile(DocumentFile doc) {
    if (doc.isFile()) {
      if (doc.delete()) {
        debug(TAG, "Document file deleted: %s", doc);
        return true;
      } else {
        warn(TAG, "Failed to delete document file: %s", doc);
        return false;
      }
    } else if (doc.isDirectory()) {
      for (DocumentFile f : doc.listFiles()) {
        if (!deleteExistingDocumentFile(f)) return false;
      }

      if (doc.delete()) {
        debug(TAG, "Document directory deleted: %s", doc);
        return true;
      } else {
        warn(TAG, "Failed to delete document directory: %s", doc);
        return false;
      }
    } else {
      warn(TAG, "Unexpected document file %s - ignoring", doc);
      return false;
    }
  }

  private static boolean moveDocumentFile(DocumentFile src, DocumentFile dst) {
    ContentResolver cr = context.getContentResolver();
    ParcelFileDescriptor spfd = null;
    ParcelFileDescriptor dpfd = null;

    try {
      spfd = cr.openFileDescriptor(src.getUri(), "r");
      dpfd = cr.openFileDescriptor(dst.getUri(), "rwt");

      if (spfd == null) {
        err(TAG, "Failed to open src file descriptor: %s", src);
        return false;
      }
      if (dpfd == null) {
        err(TAG, "Failed to open dst file descriptor: %s", dst);
        return false;
      }

      debug(TAG, "Copying %s to %s", src, dst);
      Utils.transfer(spfd.getFileDescriptor(), dpfd.getFileDescriptor());
      if (src.delete()) debug(TAG, "Source document file deleted: %s", src);
      else warn(TAG, "Failed to delete source document file: %s", src);
      return true;
    } catch (Exception ex) {
      err(TAG, ex, "Failed to rename document file %s to %s", src, dst);
      return false;
    } finally {
      Utils.close(spfd, dpfd);
    }
  }

  private static boolean moveDocumentDir(DocumentFile srcDir, DocumentFile dstDir) {
    for (DocumentFile f : srcDir.listFiles()) {
      if (f.isFile()) {
        DocumentFile dst = dstDir.findFile(f.getName());

        if (dst == null) {
          dst = dstDir.createFile(null, f.getName());

          if (dst == null) {
            err(TAG, "Failed to create document file: %s at %s", f.getName(), dstDir);
            return false;
          }
        } else if (!dst.isFile()) {
          err(TAG, "Destination exists but not a file: %s at %s", f.getName(), dstDir);
          return false;
        }

        if (!moveDocumentFile(f, dst)) return false;
      } else if (f.isDirectory()) {
        DocumentFile dst = dstDir.findFile(f.getName());

        if (dst == null) {
          dst = dstDir.createDirectory(f.getName());

          if (dst == null) {
            err(TAG, "Failed to create document directory: %s at %s", f.getName(), dstDir);
            return false;
          }
        } else if (!dst.isDirectory()) {
          err(TAG, "Destination exists but not a directory: %s at %s", f.getName(), dstDir);
          return false;
        }

        if (!moveDocumentDir(f, dst)) return false;
      } else {
        warn(TAG, "Unexpected document file %s - ignoring", f);
      }
    }

    if (srcDir.delete()) {
      debug(TAG, "Document directory %s renamed to %s", srcDir, dstDir);
    } else {
      debug(TAG, "Document directory %s copied to %s", srcDir, dstDir);
      warn(TAG, "Failed to delete source document directory: %s", srcDir);
    }

    return true;
  }
}

