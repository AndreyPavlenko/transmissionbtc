#include "commons.h"
#include "native_to_java.h"
#include "transmission-private.h"
#include <libtransmission/version.h>
#include <libtransmission/variant.h>

#define MEM_K 1024
#define MEM_K_STR "KiB"
#define MEM_M_STR "MiB"
#define MEM_G_STR "GiB"
#define MEM_T_STR "TiB"

#define DISK_K 1000
#define DISK_K_STR "kB"
#define DISK_M_STR "MB"
#define DISK_G_STR "GB"
#define DISK_T_STR "TB"

#define SPEED_K 1000
#define SPEED_K_STR "kB/s"
#define SPEED_M_STR "MB/s"
#define SPEED_G_STR "GB/s"
#define SPEED_T_STR "TB/s"

JNIEXPORT jstring JNICALL
Java_com_ap_transmission_btc_Native_transmissionVersion(JNIEnv *env, jclass __unused c) {
  return (*env)->NewStringUTF(env, SHORT_VERSION_STRING);
}

// ----------------------------------------- Start -------------------------------------------------
typedef struct OrigMetaDataFunc {
    tr_torrent_metadata_func metadata_func;
    void *metadata_func_user_data;
} OrigMetaDataFunc;

static void metadataFunc(tr_torrent *torrent, void *user_data) {
  OrigMetaDataFunc *origData = (OrigMetaDataFunc *) user_data;

  if (origData != NULL) {
    origData->metadata_func(torrent, origData->metadata_func_user_data);
    free(origData);
  }

  callAddedOrChangedCallback();
}

static tr_rpc_callback_status rpcFunc(tr_session *__unused session, tr_rpc_callback_type type,
                                      struct tr_torrent *tor,
                                      void *__unused user_data) {
  switch (type) {
    case TR_RPC_TORRENT_ADDED:
      if ((tor != NULL) && !tr_torrentHasMetadata(tor)) {
        tr_torrent_metadata_func mdFunc = tor->metadata_func_user_data;
        OrigMetaDataFunc *origData = NULL;

        if (mdFunc != NULL) {
          origData = malloc(sizeof(OrigMetaDataFunc));
          origData->metadata_func = mdFunc;
          origData->metadata_func_user_data = tor->metadata_func_user_data;
        }

        tor->metadata_func = metadataFunc;
        tor->metadata_func_user_data = origData;
      }
    case TR_RPC_TORRENT_STARTED:
    case TR_RPC_TORRENT_MOVED:
    case TR_RPC_TORRENT_CHANGED:
      callAddedOrChangedCallback();
      break;
    case TR_RPC_TORRENT_STOPPED:
    case TR_RPC_TORRENT_REMOVING:
    case TR_RPC_TORRENT_TRASHING:
      callStoppedCallback();
    case TR_RPC_SESSION_CHANGED:
      callSessionChangedCallback();
      break;
    default:
      break;
  }

  return TR_RPC_OK;
}

static void altSpeedFunc(tr_session *__unused session, bool __unused active, bool userDriven,
                         void *__unused data) {
  if (!userDriven) callScheduledAltSpeedCallback();
}

static void *transmissionStart(tr_session *session, void *data, Err *__unused err) {
  jboolean suspend = (jboolean) data;
  tr_sessionSetPaused(session, false);
  tr_sessionSetRPCCallback(session, rpcFunc, NULL);
  tr_sessionSetAltSpeedFunc(session, altSpeedFunc, NULL);

  /* load the torrents */
  tr_torrent **torrents;
  tr_ctor *ctor = tr_ctorNew(session);
  torrents = tr_sessionLoadTorrents(session, ctor, NULL);
  tr_sessionSuspend(session, suspend);
  tr_free(torrents);
  tr_ctorFree(ctor);
  return NULL;
}

JNIEXPORT jlong JNICALL
Java_com_ap_transmission_btc_Native_transmissionStart(
    JNIEnv *env, jclass __unused c,
    jstring jconfigDir, jstring jdownloadsDir,
    jint encrMode,
    jboolean enableRpc, int rpcPort,
    jboolean enableAuth, jstring jusername, jstring jpassword,
    jboolean enableRpcWhitelist, jstring jrpcWhitelist,
    jboolean loadConfig, jboolean enableSequential, jboolean suspend) {
  tr_variant settings;
  tr_session *session;
  const char *configDir = (*env)->GetStringUTFChars(env, jconfigDir, 0);
  tr_variantInitDict(&settings, 0);

  if (loadConfig) {
    if (!tr_sessionLoadSettings(&settings, configDir, "transmissionbtc")) {
      (*env)->ReleaseStringUTFChars(env, jconfigDir, configDir);
      tr_variantFree(&settings);
      throwIOEX(env, "Failed to load config");
    }
  } else {
    // Set defaults
    tr_variantDictAddBool(&settings, TR_KEY_rename_partial_files, false);
    tr_variantDictAddBool(&settings, TR_KEY_peer_port_random_on_start, true);
  }

  const char *downloadsDir = (*env)->GetStringUTFChars(env, jdownloadsDir, 0);
  tr_variantDictAddStr(&settings, TR_KEY_download_dir, downloadsDir);
  (*env)->ReleaseStringUTFChars(env, jdownloadsDir, downloadsDir);

  tr_variantDictAddInt(&settings, TR_KEY_encryption, encrMode);
  tr_variantDictAddBool(&settings, TR_KEY_sequentialDownload, enableSequential);

  if (enableRpc) {
    tr_variantDictAddBool(&settings, TR_KEY_rpc_enabled, true);
    tr_variantDictAddInt(&settings, TR_KEY_rpc_port, rpcPort);

    if (enableAuth) {
      const char *username = (*env)->GetStringUTFChars(env, jusername, 0);
      const char *password = (*env)->GetStringUTFChars(env, jpassword, 0);
      tr_variantDictAddStr(&settings, TR_KEY_rpc_username, username);
      tr_variantDictAddStr(&settings, TR_KEY_rpc_password, password);
      (*env)->ReleaseStringUTFChars(env, jusername, username);
      (*env)->ReleaseStringUTFChars(env, jpassword, password);
      tr_variantDictAddBool(&settings, TR_KEY_rpc_authentication_required, true);
    } else {
      tr_variantDictAddBool(&settings, TR_KEY_rpc_authentication_required, false);
    }

    if (enableRpcWhitelist) {
      const char *rpcWhitelist = (*env)->GetStringUTFChars(env, jrpcWhitelist, 0);
      tr_variantDictAddStr(&settings, TR_KEY_rpc_whitelist, rpcWhitelist);
      (*env)->ReleaseStringUTFChars(env, jrpcWhitelist, rpcWhitelist);
      tr_variantDictAddBool(&settings, TR_KEY_rpc_whitelist_enabled, true);
    } else {
      tr_variantDictAddBool(&settings, TR_KEY_rpc_whitelist_enabled, false);
    }
  } else {
    tr_variantDictAddBool(&settings, TR_KEY_rpc_enabled, false);
  }

  tr_formatter_mem_init(MEM_K, MEM_K_STR, MEM_M_STR, MEM_G_STR, MEM_T_STR);
  tr_formatter_size_init(DISK_K, DISK_K_STR, DISK_M_STR, DISK_G_STR, DISK_T_STR);
  tr_formatter_speed_init(SPEED_K, SPEED_K_STR, SPEED_M_STR, SPEED_G_STR, SPEED_T_STR);
  session = tr_sessionInit(configDir, true, &settings);
  tr_sessionSaveSettings(session, configDir, &settings);
  (*env)->ReleaseStringUTFChars(env, jconfigDir, configDir);
  tr_variantFree(&settings);

  jlong jsession = (jlong) session;
  runInTransmissionThreadEx(env, jsession, transmissionStart, (void *) suspend);
  return jsession;

  CATCH:
  return 0;
}
// -------------------------------------------------------------------------------------------------

// ----------------------------------------- Stop --------------------------------------------------
JNIEXPORT void JNICALL
Java_com_ap_transmission_btc_Native_transmissionStop(
    JNIEnv *__unused env, jclass __unused c, jlong jsession, jstring jconfigDir) {
  tr_variant settings;
  tr_session *session = (tr_session *) jsession;
  session->rpc_func_user_data = NULL;

  tr_variantInitDict(&settings, 0);
  tr_sessionGetSettings(session, &settings);

  const char *configDir = (*env)->GetStringUTFChars(env, jconfigDir, 0);
  tr_sessionSaveSettings(session, configDir, &settings);
  (*env)->ReleaseStringUTFChars(env, jconfigDir, configDir);

  tr_variantFree(&settings);
  tr_sessionClose(session);
}
// -------------------------------------------------------------------------------------------------

// --------------------------------------- Suspend -------------------------------------------------
static void *transmissionSuspend(tr_session *session, void *data, Err *__unused err) {
  tr_sessionSuspend(session, (bool) data);
  return NULL;
}

JNIEXPORT void JNICALL
Java_com_ap_transmission_btc_Native_transmissionSuspend(
    JNIEnv *__unused env, jclass __unused c, jlong jsession, jboolean suspend) {
  runInTransmissionThreadEx(env, jsession, transmissionSuspend, (void *) suspend);
  CATCH:;
}
// -------------------------------------------------------------------------------------------------

// -------------------------------- HasDownloadingTorrents -----------------------------------------
static void *
transmissionHasDownloadingTorrents(tr_session *session, void *__unused data, Err *__unused err) {
  for (tr_torrent *it = session->torrentList; it != NULL; it = it->next) {
    switch (tr_torrentGetActivity(it)) {
      case TR_STATUS_DOWNLOAD:
      case TR_STATUS_DOWNLOAD_WAIT:
      case TR_STATUS_CHECK:
      case TR_STATUS_CHECK_WAIT:
        return (void *) JNI_TRUE;
      default:
        continue;
    }
  }

  return (void *) JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_ap_transmission_btc_Native_transmissionHasDownloadingTorrents(
    JNIEnv *__unused env, jclass __unused c, jlong jsession) {
  return (jboolean) runInTransmissionThreadEx(env, jsession, transmissionHasDownloadingTorrents,
                                              NULL);
  CATCH:
  return JNI_FALSE;
}
// -------------------------------------------------------------------------------------------------

// ------------------------------------- ListTorrentNames ------------------------------------------
typedef struct ListTorrentsData {
    int count;
    char **torrents;
} ListTorrentsData;

static void *
transmissionListTorrentNames(tr_session *session, void *data, Err *__unused err) {
  ListTorrentsData *d = (ListTorrentsData *) data;
  d->count = session->torrentCount;
  if (d->count == 0) return NULL;
  d->torrents = malloc(d->count * (sizeof(char *)));
  int i = 0;

  for (tr_torrent *it = session->torrentList; it != NULL; it = it->next) {
    size_t hashLen = sizeof(it->info.hashString);
    size_t nameLen = strlen(it->info.name);
    size_t lineLen = hashLen + nameLen + 12;
    char *line = malloc(lineLen);
    snprintf(line, lineLen, "%d %s %s", tr_torrentId(it), it->info.hashString, it->info.name);
    d->torrents[i++] = line;
  }

  return NULL;
}

JNIEXPORT jobjectArray JNICALL
Java_com_ap_transmission_btc_Native_transmissionListTorrentNames(
    JNIEnv *__unused env, jclass __unused c, jlong jsession) {
  ListTorrentsData d;
  jobjectArray result = NULL;
  runInTransmissionThreadEx(env, jsession, transmissionListTorrentNames, &d);

  if (d.count == 0) return NULL;
  result = (*env)->NewObjectArray(env, d.count, (*env)->FindClass(env, "java/lang/String"), NULL);

  for (int i = 0; i < d.count; i++) {
    jobject jname = (*env)->NewStringUTF(env, d.torrents[i]);
    (*env)->SetObjectArrayElement(env, result, i, jname);
    (*env)->DeleteLocalRef(env, jname);
    free(d.torrents[i]);
  }

  free(d.torrents);
  CATCH:
  return result;
}
// -------------------------------------------------------------------------------------------------

// ----------------------------------- GetEncryptionMode -------------------------------------------
JNIEXPORT jint JNICALL
Java_com_ap_transmission_btc_Native_transmissionGetEncryptionMode(
    JNIEnv *__unused env, jclass __unused c, jlong jsession) {
  return tr_sessionGetEncryption((tr_session *) jsession);
}
// -------------------------------------------------------------------------------------------------