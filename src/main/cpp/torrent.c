#include "commons.h"
#include "transmission-private.h"
#include <semaphore.h>

// ----------------------------------- torrentAdd --------------------------------------------------
/*
 * Returns:
 *  0 - OK
 *  1 - PARSE_ERR
 *  2 - DUPLICATE
 *  3 - OK_DELETE
 */
JNIEXPORT jint JNICALL
Java_com_ap_transmission_btc_Native_torrentAdd(
    JNIEnv *env, jclass __unused c, jlong jsession, jstring jpath, jstring jdownloadDir,
    jboolean setDelete, jboolean sequential, jintArray unwantedIndexes,
    jbyteArray returnMeTorrentHash) {
  bool delete = false;
  tr_ctor *ctor = ctorFromFile(env, jsession, jpath, false);

  if (ctor != NULL) {
    if (jdownloadDir != NULL) {
      const char *downloadDir = (*env)->GetStringUTFChars(env, jdownloadDir, 0);
      tr_ctorSetDownloadDir(ctor, TR_FORCE, downloadDir);
      (*env)->ReleaseStringUTFChars(env, jdownloadDir, downloadDir);
    }

    if (unwantedIndexes != NULL) {
      jsize len = (*env)->GetArrayLength(env, unwantedIndexes);
      jint *unwanted = (*env)->GetIntArrayElements(env, unwantedIndexes, 0);
      tr_file_index_t trIndexes[len];

      for (int i = 0; i < len; i++) {
        trIndexes[i] = (tr_file_index_t) unwanted[i];
      }

      (*env)->ReleaseIntArrayElements(env, unwantedIndexes, unwanted, 0);
      tr_ctorSetFilesWanted(ctor, trIndexes, (tr_file_index_t) len, false);
    }

    int err = 0;
    tr_ctorSetDeleteSource(ctor, setDelete);
    tr_ctorSetSequentialDownload(ctor, sequential);
    tr_torrent *tor = tr_torrentNew(ctor, &err, NULL);

    if ((err == TR_PARSE_DUPLICATE) && (unwantedIndexes != NULL)) {
      tr_info inf;
      tr_parse_result res = tr_torrentParse(ctor, &inf);

      if ((res == TR_PARSE_DUPLICATE) || (res == TR_PARSE_OK)) {
        tor = tr_torrentFindFromHash((tr_session *) jsession, inf.hash);

        if (tor != NULL) {
          const tr_info *info = tr_torrentInfo(tor);
          tr_file_index_t wanted[info->fileCount];
          tr_file_index_t wantedCount = 0;
          jsize len = (*env)->GetArrayLength(env, unwantedIndexes);
          jint *unwanted = (*env)->GetIntArrayElements(env, unwantedIndexes, 0);

          for (int i = 0; i < info->fileCount; i++) {
            tr_file f = info->files[i];
            if (f.dnd == 0) continue;
            jboolean w = true;

            for (int n = 0; n < len; n++) {
              if (unwanted[n] == i) {
                w = false;
                break;
              }
            }

            if (w) wanted[wantedCount++] = (tr_file_index_t) i;
          }

          (*env)->ReleaseIntArrayElements(env, unwantedIndexes, unwanted, 0);

          if (wantedCount != 0) {
            tr_torrentSetFileDLs(tor, wanted, wantedCount, true);
            err = (tr_ctorGetDeleteSource(ctor, &delete) && delete) ? 3 : 0;
          }
        }
      } else {
        err = 1;
      }
    } else if (!err) {
      err = (tr_ctorGetDeleteSource(ctor, &delete) && delete) ? 3 : 0;
    }

    if ((returnMeTorrentHash != NULL) && (tor != NULL)) {
      const tr_info *info = tr_torrentInfo(tor);
      (*env)->SetByteArrayRegion(env, returnMeTorrentHash, 0, SHA_DIGEST_LENGTH,
                                 (const jbyte *) info->hash);
    }

    tr_ctorFree(ctor);
    return err;
  } else {
    return 1;
  }
}
// -------------------------------------------------------------------------------------------------

// ------------------------------------ torrentRemove ----------------------------------------------
typedef struct TorrentRemoveData {
    jint torrentId;
    jboolean removeLocalData;
} TorrentRemoveData;

static void *torrentRemove(tr_session *session, void *data, Err *err) {
  TorrentRemoveData *d = (TorrentRemoveData *) data;
  tr_torrent *tor = findTorrentByIdEx(session, d->torrentId, err);
  tr_torrentRemove(tor, d->removeLocalData, NULL);
  CATCH:
  return NULL;
}

JNIEXPORT void JNICALL
Java_com_ap_transmission_btc_Native_torrentRemove(
    JNIEnv *env, jclass __unused c, jlong jsession, jint torrentId, jboolean removeLocalData) {
  TorrentRemoveData d = {torrentId, removeLocalData};
  runInTransmissionThreadEx(env, jsession, torrentRemove, &d);
  CATCH:;
}
// -------------------------------------------------------------------------------------------------

// ------------------------------------ torrentStop ------------------------------------------------
static void *torrentStop(tr_session *session, void *data, Err *err) {
  tr_torrent *tor = findTorrentByIdEx(session, (int) data, err);
  tr_torrentStop(tor);
  CATCH:
  return NULL;
}

JNIEXPORT void JNICALL
Java_com_ap_transmission_btc_Native_torrentStop(
    JNIEnv *env, jclass __unused c, jlong jsession, jint torrentId) {
  runInTransmissionThreadEx(env, jsession, torrentStop, (void *) torrentId);
  CATCH:;
}
// -------------------------------------------------------------------------------------------------

// ------------------------------------ torrentStart -----------------------------------------------
static void *torrentStart(tr_session *session, void *data, Err *err) {
  tr_torrent *tor = findTorrentByIdEx(session, (int) data, err);
  tr_torrentStart(tor);
  CATCH:
  return NULL;
}

JNIEXPORT void JNICALL
Java_com_ap_transmission_btc_Native_torrentStart(
    JNIEnv *env, jclass __unused c, jlong jsession, jint torrentId) {
  runInTransmissionThreadEx(env, jsession, torrentStart, (void *) torrentId);
  CATCH:;
}
// -------------------------------------------------------------------------------------------------

// ------------------------------------ torrentVerify -----------------------------------------------
static void *torrentVerify(tr_session *session, void *data, Err *err) {
  tr_torrent *tor = findTorrentByIdEx(session, (int) data, err);
  tr_torrentVerify(tor, NULL, NULL);
  CATCH:
  return NULL;
}

JNIEXPORT void JNICALL
Java_com_ap_transmission_btc_Native_torrentVerify(
    JNIEnv *env, jclass __unused c, jlong jsession, jint torrentId) {
  runInTransmissionThreadEx(env, jsession, torrentVerify, (void *) torrentId);
  CATCH:;
}
// -------------------------------------------------------------------------------------------------

// ------------------------------- torrentListFilesFromFile ----------------------------------------
JNIEXPORT jobjectArray JNICALL
Java_com_ap_transmission_btc_Native_torrentListFilesFromFile(
    JNIEnv *env, jclass __unused c, jstring jtorrent) {
  tr_info info;
  infoFromFileEx(env, 0, jtorrent, &info);
  tr_file_index_t count = info.fileCount;
  tr_file *files = info.files;
  jclass jstring = (*env)->FindClass(env, "java/lang/String");
  jobjectArray a = (*env)->NewObjectArray(env, count, jstring, NULL);

  for (int i = 0; i < count; i++) {
    jobject jname = (*env)->NewStringUTF(env, files[i].name);
    (*env)->SetObjectArrayElement(env, a, i, jname);
    (*env)->DeleteLocalRef(env, jname);
  }

  tr_metainfoFree(&info);
  return a;
  CATCH:
  return NULL;
}
// -------------------------------------------------------------------------------------------------

// ----------------------------------- torrentListFiles --------------------------------------------
typedef struct ListFilesData {
    jint torrentId;
    jint count;
    char **fileNames;
} ListFilesData;


static void *
torrentListFiles(tr_session *session, void *data, Err *err) {
  ListFilesData *d = (ListFilesData *) data;
  tr_torrent *tor = findTorrentByIdEx(session, d->torrentId, err);
  d->count = tor->info.fileCount;
  if (d->count == 0) return NULL;
  d->fileNames = malloc(d->count * (sizeof(char *)));

  for (int i = 0; i < d->count; i++) {
    tr_file *f = &(tor->info.files[i]);
    d->fileNames[i] = strdup(f->name);
  }

  CATCH:
  return NULL;
}

JNIEXPORT jobjectArray JNICALL
Java_com_ap_transmission_btc_Native_torrentListFiles(
    JNIEnv *env, jclass __unused c, jlong jsession, jint torrentId) {
  ListFilesData d = {.count=0};
  jobjectArray result = NULL;
  d.torrentId = torrentId;
  runInTransmissionThreadEx(env, jsession, torrentListFiles, &d);
  if (d.count == 0) return NULL;
  result = (*env)->NewObjectArray(env, d.count, (*env)->FindClass(env, "java/lang/String"), NULL);

  for (int i = 0; i < d.count; i++) {
    jobject jname = (*env)->NewStringUTF(env, d.fileNames[i]);
    (*env)->SetObjectArrayElement(env, result, i, jname);
    (*env)->DeleteLocalRef(env, jname);
    free(d.fileNames[i]);
  }

  free(d.fileNames);
  CATCH:
  return result;
}
// -------------------------------------------------------------------------------------------------

// ----------------------------- torrentMagnetToTorrentFile ----------------------------------------
typedef struct MagnetToTorrentData {
    sem_t *sem;
    const char *magnet;
    char *path;
    tr_torrent *tor;
    jint torrentId;
    jboolean enqueue;
} MagnetToTorrentData;

static void metadataCallback(tr_torrent *__unused torrent, void *data) {
  MagnetToTorrentData *d = (MagnetToTorrentData *) data;

  if (tr_torrentHasMetadata(d->tor)) {
    const tr_info *info = tr_torrentInfo(d->tor);
    d->path = strdup(info->torrent);
  } else {
    tr_torrentRemove(d->tor, true, NULL);
    d->tor = NULL;
  }

  sem_post(d->sem);
}

static void *torrentMagnetToTorrentFile(tr_session *session, void *data, Err *err) {
  MagnetToTorrentData *d = (MagnetToTorrentData *) data;
  tr_ctor *ctor = tr_ctorNew(session);

  if (tr_ctorSetMetainfoFromMagnetLink(ctor, d->magnet) != 0) {
    tr_ctorFree(ctor);
    err->set(err, CLASS_IOEX, "Failed to set meta from magnet link");
    return NULL;
  }

  int newErr;
  tr_torrent *tor = tr_torrentNew(ctor, &newErr, NULL);
  tr_ctorFree(ctor);

  if (tor == NULL) {
    if (newErr == TR_PARSE_DUPLICATE) err->set(err, CLASS_DUPEX, "Duplicate torrent");
    else err->set(err, CLASS_IOEX, "Failed to add torrent");
    return NULL;
  }

  d->tor = tor;
  d->torrentId = tor->uniqueId;
  tr_torrentSetMetadataCallback(tor, &metadataCallback, data);
  return NULL;
}

static void *torrentMagnetToTorrentFileCleanup(tr_session *session, void *data, Err *__unused err) {
  MagnetToTorrentData *d = (MagnetToTorrentData *) data;
  tr_torrent *tor = tr_torrentFindFromId(session, d->torrentId);

  if (tor != NULL) {
    if (d->enqueue) {
      tr_torrentSetMetadataCallback(tor, NULL, NULL);
    } else {
      tr_torrentRemove(tor, true, NULL);

      if (session->rpc_func != NULL) {
        session->rpc_func(session, TR_RPC_TORRENT_TRASHING, NULL, session->rpc_func_user_data);
      }
    }
  }

  if (d->path != NULL) free(d->path);
  return NULL;
}

JNIEXPORT void JNICALL
Java_com_ap_transmission_btc_Native_torrentMagnetToTorrentFile(
    JNIEnv *env, jclass __unused c, jlong jsession, jlong jsem,
    jstring jmagnet, jstring jpath, jint timeout, jbooleanArray enqueue) {
  const char *magnet = (*env)->GetStringUTFChars(env, jmagnet, 0);
  sem_t *sem = (sem_t *) jsem;
  MagnetToTorrentData d = {0};
  d.sem = sem;
  d.magnet = magnet;

  runInTransmissionThreadEx(env, jsession, torrentMagnetToTorrentFile, &d);

  int s = 0;
  struct timespec ts;
  clock_gettime(CLOCK_REALTIME, &ts);
  ts.tv_sec += timeout;

  do {
    if (d.path != NULL) break;
  } while (((s = sem_timedwait(sem, &ts)) == -1) && errno == EINTR);

  if (s == -1) {
    time_t eot = ts.tv_sec;
    clock_gettime(CLOCK_REALTIME, &ts);

    if (ts.tv_sec >= eot) {
      throwEX(env, CLASS_IOEX, "Timed out while downloading magnet meta");
    } else {
      throwEX(env, CLASS_IOEX, "Failed to download meta data");
    }
  } else {
    jboolean isCopy;
    jboolean *enq = (*env)->GetBooleanArrayElements(env, enqueue, &isCopy);
    d.enqueue = enq[0];
    (*env)->ReleaseBooleanArrayElements(env, enqueue, enq, 0);

    if ((d.path != NULL) && !d.enqueue) {
      const char *path = (*env)->GetStringUTFChars(env, jpath, 0);
      cp(env, d.path, path);
      (*env)->ReleaseStringUTFChars(env, jpath, path);
    } else if (!d.enqueue) {
      throwEX(env, CLASS_IOEX, "Failed to download meta data");
    }
  }

  CATCH:
  (*env)->ReleaseStringUTFChars(env, jmagnet, magnet);
  runInTransmissionThread(__FILENAME__, __LINE__, env, jsession,
                          torrentMagnetToTorrentFileCleanup, &d);
}
// -------------------------------------------------------------------------------------------------

// ---------------------------------- torrentFindByHash --------------------------------------------
JNIEXPORT jint JNICALL
Java_com_ap_transmission_btc_Native_torrentFindByHash(
    JNIEnv *env, jclass __unused c, jlong jsession, jbyteArray torrentHash) {
  char hash[SHA_DIGEST_LENGTH];
  (*env)->GetByteArrayRegion(env, torrentHash, 0, sizeof(hash), (jbyte *) hash);
  return (jint) runInTransmissionThreadEx(env, jsession, findTorrentByHashFunc, hash);
  CATCH:
  return -1;
}
// -------------------------------------------------------------------------------------------------

// ------------------------------------ torrentGetName ---------------------------------------------
static void *torrentGetName(tr_session *session, void *data, Err *err) {
  tr_torrent *tor = findTorrentByIdEx(session, (int) data, err);
  return strdup(tor->info.name);
  CATCH:
  return NULL;
}

JNIEXPORT jstring JNICALL
Java_com_ap_transmission_btc_Native_torrentGetName(
    JNIEnv *env, jclass __unused c, jlong jsession, jint torrentId) {
  jstring jname = NULL;
  char *name = (char *) runInTransmissionThreadEx(env, jsession, torrentGetName,
                                                  (void *) torrentId);
  jname = (*env)->NewStringUTF(env, name);
  free(name);
  CATCH:
  return jname;
}
// -------------------------------------------------------------------------------------------------

// ------------------------------------ torrentGetHash ---------------------------------------------
typedef struct GetHashData {
    jint torrentId;
    jbyte *hash;
} GetHashData;

static void *torrentGetHash(tr_session *session, void *data, Err *err) {
  GetHashData *d = (GetHashData *) data;
  tr_torrent *tor = findTorrentByIdEx(session, d->torrentId, err);
  memcpy(d->hash, tr_torrentInfo(tor)->hash, SHA_DIGEST_LENGTH);
  CATCH:
  return NULL;
}

JNIEXPORT void JNICALL
Java_com_ap_transmission_btc_Native_torrentGetHash(
    JNIEnv *env, jclass __unused c, jlong jsession, jint torrentId, jbyteArray torrentHash) {
  GetHashData d = {torrentId};
  d.hash = (*env)->GetByteArrayElements(env, torrentHash, 0);
  runInTransmissionThreadEx(env, jsession, torrentGetHash, &d);
  CATCH:
  (*env)->ReleaseByteArrayElements(env, torrentHash, d.hash, 0);
}
// -------------------------------------------------------------------------------------------------

// ---------------------------------- torrentGetPieceHash ------------------------------------------
typedef struct GetPieceHashData {
    jint torrentId;
    jlong pieceIdx;
    jbyte *hash;
} GetPieceHashData;

static void *torrentGetPieceHash(tr_session *session, void *data, Err *err) {
  GetPieceHashData *d = (GetPieceHashData *) data;
  tr_torrent *tor = findTorrentByIdEx(session, d->torrentId, err);
  const tr_info *info = tr_torrentInfo(tor);

  if (d->pieceIdx < info->pieceCount)
    memcpy(d->hash, info->pieces[d->pieceIdx].hash, SHA_DIGEST_LENGTH);
  else
    err->set(err, CLASS_IAEX, "Invalid piece index: %d", d->pieceIdx);

  CATCH:
  return NULL;
}

JNIEXPORT void JNICALL
Java_com_ap_transmission_btc_Native_torrentGetPieceHash(
    JNIEnv *env, jclass __unused c, jlong jsession, jint torrentId,
    jlong pieceIdx, jbyteArray pieceHash) {
  GetPieceHashData d = {torrentId, pieceIdx};
  d.hash = (*env)->GetByteArrayElements(env, pieceHash, 0);
  runInTransmissionThreadEx(env, jsession, torrentGetPieceHash, &d);
  CATCH:
  (*env)->ReleaseByteArrayElements(env, pieceHash, d.hash, 0);
}
// -------------------------------------------------------------------------------------------------

// ---------------------------------- torrentSetPiecesHiPri ----------------------------------------
typedef struct SetPiecesHiPriData {
    jint torrentId;
    jlong firstPiece;
    jlong lastPiece;
} SetPiecesHiPriData;

static void *torrentSetPiecesHiPri(tr_session *session, void *data, Err *err) {
  SetPiecesHiPriData *d = (SetPiecesHiPriData *) data;
  tr_torrent *tor = findTorrentByIdEx(session, d->torrentId, err);
  tr_piece *pieces = tor->info.pieces;
  tr_piece_index_t first = (tr_piece_index_t) d->firstPiece;
  tr_piece_index_t last = (tr_piece_index_t) d->lastPiece;
  tr_piece_index_t count = tor->info.pieceCount;
  bool hasChanges = false;

  if (!tor->isSequential) {
    tor->isSequential = true;
    hasChanges = true;
  }

  for (tr_piece_index_t i = first; (i < count) && (i <= last); i++) {
    if ((pieces[i].priority != TR_PRI_HIGH) && (pieces[i].dnd == 0)) {
      pieces[i].priority = TR_PRI_HIGH;
      hasChanges = true;
    }
  }

  if (hasChanges) {
    tr_torrentSetDirty(tor);
    if (tor->isRunning) tr_peerMgrRebuildRequests(tor);
    else tr_torrentStart(tor);
  }

  CATCH:
  return NULL;
}

JNIEXPORT void JNICALL
Java_com_ap_transmission_btc_Native_torrentSetPiecesHiPri(
    JNIEnv *env, jclass __unused c, jlong jsession, jint torrentId,
    jlong firstPiece, jlong lastPiece) {
  SetPiecesHiPriData d = {torrentId, firstPiece, lastPiece};
  runInTransmissionThreadEx(env, jsession, torrentSetPiecesHiPri, &d);
  CATCH:;
}
// -------------------------------------------------------------------------------------------------

// ------------------------------------- torrentFindFile -------------------------------------------
typedef struct FileData {
    jint torrentId;
    jint fileIdx;
} FileData;

static void *torrentFindFile(tr_session *session, void *data, Err *err) {
  FileData *d = (FileData *) data;
  tr_torrent *tor = findTorrentByIdEx(session, d->torrentId, err);
  getWantedFileInfoEx(tor, (uint32_t) d->fileIdx, err);
  return tr_torrentFindFile(tor, (tr_file_index_t) d->fileIdx);
  CATCH:
  return NULL;
}

JNIEXPORT jstring JNICALL
Java_com_ap_transmission_btc_Native_torrentFindFile(
    JNIEnv *env, jclass __unused c, jlong jsession, jint torrentId, jint fileIdx) {
  FileData d = {torrentId, fileIdx};
  char *path = (char *) runInTransmissionThreadEx(env, jsession, torrentFindFile, &d);
  if (path == NULL) return NULL;
  jstring jpath = (*env)->NewStringUTF(env, path);
  tr_free(path);
  return jpath;

  CATCH:
  return NULL;
}
// -------------------------------------------------------------------------------------------------

// ----------------------------------- torrentGetFileName ------------------------------------------
static void *torrentGetFileName(tr_session *session, void *data, Err *err) {
  FileData *d = (FileData *) data;
  void *name = NULL;
  tr_torrent *tor = findTorrentByIdEx(session, d->torrentId, err);
  const tr_file *f = getFileInfoEx(tor, (uint32_t) d->fileIdx, err);
  size_t nameLen = strlen(f->name);
  name = calloc(nameLen + 1, sizeof(char));
  memcpy(name, f->name, nameLen);
  CATCH:
  return name;
}

JNIEXPORT jstring JNICALL
Java_com_ap_transmission_btc_Native_torrentGetFileName(
    JNIEnv *env, jclass __unused c, jlong jsession, jint torrentId, jint fileIdx) {
  jstring jname = NULL;
  FileData d = {torrentId, fileIdx};
  char *name = (char *) runInTransmissionThreadEx(env, jsession, torrentGetFileName, &d);
  jname = (*env)->NewStringUTF(env, name);
  free(name);
  CATCH:
  return jname;
}
// -------------------------------------------------------------------------------------------------

// ----------------------------------- torrentGetFileStat ------------------------------------------
typedef struct FileStatData {
    jint torrentId;
    jint fileIdx;
    jlong *bitFields;
    jsize bitFieldsLen;
} FileStatData;

static void *torrentGetFileStat(tr_session *session, void *data, Err *err) {
  FileStatData *d = (FileStatData *) data;
  tr_torrent *tor = findTorrentByIdEx(session, d->torrentId, err);
  const tr_file *f = getFileInfoEx(tor, (uint32_t) d->fileIdx, err);

  CATCH:
  if ((tor == NULL) || (f == NULL)) return NULL;

  const tr_info *info = tr_torrentInfo(tor);
  bool complete = true;
  int8_t tabs[info->pieceCount];
  size_t pieceCount = f->lastPiece - f->firstPiece + 1;
  size_t fieldsCount = ((pieceCount % 64) == 0) ? (pieceCount / 64) : ((pieceCount / 64) + 1);
  jsize statLen = d->bitFieldsLen = (jsize) (fieldsCount + 6);
  jlong *bitFields = d->bitFields;
  if (bitFields == NULL) bitFields = d->bitFields = malloc(sizeof(jlong) * statLen);

  tr_torrentAvailability(tor, tabs, info->pieceCount);

  for (int i = 6, t = f->firstPiece; i < statLen; i++) {
    jlong bf = 0;

    for (int n = 0; (n < 64) && (t <= f->lastPiece); n++, t++) {
      if (tabs[t] == -1) bf |= (1LL << n);
      else complete = false;
    }

    bitFields[i] = bf;
  }

  bitFields[0] = info->pieceSize;
  bitFields[1] = f->length;
  bitFields[2] = f->offset;
  bitFields[3] = f->firstPiece;
  bitFields[4] = f->lastPiece;
  bitFields[5] = f->dnd ? 2 : complete ? 1 : 0;
  return NULL;
}

/**
 * Returns:
 *  jlongArray[0] = pieceLength
 *  jlongArray[1] = fileLength
 *  jlongArray[2] = fileOffset
 *  jlongArray[3] = firstPieceIndex
 *  jlongArray[4] = lastPieceIndex
 *  jlongArray[5] = fileComplete
 *  jlongArray[6...] = pieceBitFields
 */
JNIEXPORT jbyteArray JNICALL
Java_com_ap_transmission_btc_Native_torrentGetFileStat(
    JNIEnv *env, jclass __unused c, jlong jsession, jint torrentId, jint fileIdx, jbyteArray stat) {
  FileStatData d = {.torrentId= torrentId, .fileIdx = fileIdx, .bitFields = NULL};
  if (stat != NULL) d.bitFields = (*env)->GetLongArrayElements(env, stat, 0);
  runInTransmissionThreadEx(env, jsession, torrentGetFileStat, &d);

  if (stat == NULL) {
    stat = (*env)->NewLongArray(env, d.bitFieldsLen);
    (*env)->SetLongArrayRegion(env, stat, 0, d.bitFieldsLen, d.bitFields);
    free(d.bitFields);
  } else {
    (*env)->ReleaseLongArrayElements(env, stat, d.bitFields, 0);
  }

  CATCH:
  return stat;
}
// -------------------------------------------------------------------------------------------------

// ------------------------------------- torrentGetPiece -------------------------------------------
typedef struct GetPieceData {
    jint torrentId;
    jlong pieceIdx;
    jbyte *dst;
    jint offset;
    jint len;
} GetPieceData;

static void *torrentGetPiece(tr_session *session, void *data, Err *err) {
  GetPieceData *d = (GetPieceData *) data;
  tr_torrent *tor = findTorrentByIdEx(session, d->torrentId, err);
  CATCH:
  if (tor == NULL) return NULL;

#ifndef NDEBUG
  tr_piece_index_t pieceCount = tr_torrentInfo(tor)->pieceCount;
  int8_t tabs[pieceCount];
  tr_torrentAvailability(tor, tabs, pieceCount);
  if (tabs[d->pieceIdx] != -1) {
    err->set(err, CLASS_IOEX, "Piece %ld is incomplete!", d->pieceIdx);
    return NULL;
  }
#endif

  if (tr_cacheReadBlock(session->cache, tor, (tr_piece_index_t) d->pieceIdx,
                        (uint32_t) d->offset, (uint32_t) d->len, (uint8_t *) d->dst) != 0) {
    err->set(err, CLASS_IOEX, "Failed to get piece %d: offset=%ld, length=%d", d->pieceIdx,
             d->offset, d->len);
  }

  return NULL;
}

JNIEXPORT void JNICALL
Java_com_ap_transmission_btc_Native_torrentGetPiece(
    JNIEnv *env, jclass __unused c, jlong jsession, jint torrentId, jlong pieceIdx,
    jbyteArray dst, jint offset, jint len) {
  GetPieceData d = {torrentId, pieceIdx, (*env)->GetByteArrayElements(env, dst, 0), offset, len};
  runInTransmissionThreadEx(env, jsession, torrentGetPiece, &d);
  CATCH:
  (*env)->ReleaseByteArrayElements(env, dst, d.dst, 0);
}
// -------------------------------------------------------------------------------------------------

// ------------------------------------- torrentStatBrief ------------------------------------------
static double getVerifyProgress(tr_torrent const *tor) {
  double d = 0;
  if (tr_torrentHasMetadata(tor)) {
    tr_piece_index_t checked = 0;
    for (tr_piece_index_t i = 0; i < tor->info.pieceCount; ++i) {
      if (tor->info.pieces[i].timeChecked != 0) checked++;
    }
    d = checked / (double) tor->info.pieceCount;
  }
  return d;
}

typedef struct StatBriefData {
    jlong *stat;
    jint statLen;
    bool alloc;
} StatBriefData;

/*
 * Returns:
 * stat[0] - torrentId
 * stat[1] - status
 * stat[2] - progress
 * stat[3] - totalLength
 * stat[4] - remainingLength
 * stat[5] - uploadedLength
 * stat[6] - peersUp
 * stat[7] - peersDown
 * stat[8] - speedUp
 * stat[9] - speedDown
 */
static void *torrentStatBrief(tr_session *session, void *data, __unused Err *err) {
  StatBriefData *d = (StatBriefData *) data;
  tr_torrent *it = session->torrentList;
  int numTorrents = session->torrentCount;
  int statLen = numTorrents * 10;

  if ((d->stat == NULL) || (statLen != d->statLen)) {
    d->stat = malloc(sizeof(jlong) * statLen);
    d->statLen = statLen;
    d->alloc = true;
  }

  for (int i = 0; (it != NULL); it = it->next, i += 10) {
    tr_torrent_activity status = tr_torrentGetActivity(it);

    d->stat[i] = it->uniqueId;
    d->stat[i + 3] = tr_cpSizeWhenDone(&it->completion);
    d->stat[i + 4] = tr_torrentGetLeftUntilDone(it);
    d->stat[i + 5] = it->uploadedCur + it->uploadedPrev;

    if (it->error != TR_STAT_LOCAL_ERROR) {
      switch (status) {
        case TR_STATUS_STOPPED:
          d->stat[i + 1] = 0;
          d->stat[i + 2] = (jlong) (tr_cpPercentDone(&it->completion) * 100);
          continue;
        case TR_STATUS_CHECK_WAIT :
        case TR_STATUS_CHECK:
          d->stat[i + 1] = 1;
          d->stat[i + 2] = (jlong) (getVerifyProgress(it) * 100);
          break;
        case TR_STATUS_DOWNLOAD_WAIT:
        case TR_STATUS_DOWNLOAD :
          d->stat[i + 1] = 2;
          d->stat[i + 2] = (jlong) (tr_cpPercentDone(&it->completion) * 100);
          break;
        case TR_STATUS_SEED_WAIT :
        case TR_STATUS_SEED:
          d->stat[i + 1] = 3;
          d->stat[i + 2] = (jlong) (tr_cpPercentDone(&it->completion) * 100);
          break;
      }
    } else {
      d->stat[i + 1] = 4;
      d->stat[i + 2] = 0;
    }

    if (it->swarm != NULL) {
      uint64_t const now = tr_time_msec();
      struct tr_swarm_stats sstat;
      tr_swarmGetStats(it->swarm, &sstat);
      d->stat[i + 6] = sstat.activePeerCount[TR_UP];
      d->stat[i + 7] = sstat.activePeerCount[TR_DOWN] + sstat.activeWebseedCount;
      d->stat[i + 8] = tr_bandwidthGetPieceSpeed_Bps(&it->bandwidth, now, TR_UP);
      d->stat[i + 9] = tr_bandwidthGetPieceSpeed_Bps(&it->bandwidth, now, TR_DOWN);
    } else {
      d->stat[i + 6] = 0L;
      d->stat[i + 7] = 0L;
      d->stat[i + 8] = 0L;
      d->stat[i + 9] = 0L;
    }
  }

  return NULL;
}

JNIEXPORT jlongArray JNICALL
Java_com_ap_transmission_btc_Native_torrentStatBrief(
    JNIEnv *env, jclass __unused c, jlong jsession, jlongArray jstat) {
  StatBriefData d = {NULL, 0, false};
  jlong *stat = NULL;
  bool release = false;

  if (jstat != NULL) {
    d.stat = stat = (*env)->GetLongArrayElements(env, jstat, 0);
    d.statLen = (*env)->GetArrayLength(env, jstat);
    release = true;
  }

  runInTransmissionThreadEx(env, jsession, torrentStatBrief, &d);

  CATCH:
  if (release) {
    (*env)->ReleaseLongArrayElements(env, jstat, stat, 0);
  }
  if (d.alloc) {
    jstat = (*env)->NewLongArray(env, d.statLen);
    (*env)->SetLongArrayRegion(env, jstat, 0, d.statLen, d.stat);
    free(d.stat);
  }

  return jstat;
}
// -------------------------------------------------------------------------------------------------

// ------------------------------------- torrentGetError -------------------------------------------
static void *torrentGetError(tr_session *session, void *data, Err *err) {
  tr_torrent *tor = findTorrentByIdEx(session, (int) data, err);
  return strdup(tor->errorString);
  CATCH:
  return NULL;
}

JNIEXPORT jstring JNICALL
Java_com_ap_transmission_btc_Native_torrentGetError(
    JNIEnv *env, jclass __unused c, jlong jsession, jint torrentId) {
  char *err = (char *) runInTransmissionThreadEx(env, jsession, torrentGetError,
                                                 (void *) torrentId);
  jstring jerr = (*env)->NewStringUTF(env, err);
  free(err);
  return jerr;
  CATCH:
  return NULL;
}
// -------------------------------------------------------------------------------------------------

// ------------------------------------- torrentSetDnd ---------------------------------------------
typedef struct SetDndData {
    bool dnd;
    jint torrentId;
    const tr_file_index_t *files;
    tr_file_index_t fileCount;
} SetDndData;

static void *torrentSetDnd(tr_session *session, void *data, Err *err) {
  SetDndData *d = (SetDndData *) data;
  tr_torrent *tor = findTorrentByIdEx(session, d->torrentId, err);
  tr_torrentSetFileDLs(tor, d->files, d->fileCount, !d->dnd);
  CATCH:
  return NULL;
}

JNIEXPORT void JNICALL
Java_com_ap_transmission_btc_Native_torrentSetDnd(
    JNIEnv *env, jclass __unused c, jlong jsession, jint torrentId, jintArray files, jboolean dnd) {
  SetDndData d;
  d.dnd = dnd;
  d.torrentId = torrentId;
  d.files = (tr_file_index_t *) (*env)->GetIntArrayElements(env, files, 0);
  d.fileCount = (tr_file_index_t) (*env)->GetArrayLength(env, files);
  runInTransmissionThreadEx(env, jsession, torrentSetDnd, &d);
  CATCH:
  (*env)->ReleaseIntArrayElements(env, files, (jint *) d.files, 0);
}
// -------------------------------------------------------------------------------------------------

// ---------------------------------- torrentSetLocation -------------------------------------------
typedef struct SetLocationData {
    jint torrentId;
    const char *path;
} SetLocationData;

static void *torrentSetLocation(tr_session *session, void *data, Err *err) {
  SetLocationData *d = (SetLocationData *) data;
  tr_torrent *tor = findTorrentByIdEx(session, d->torrentId, err);
  tr_torrentSetLocation(tor, d->path, true, NULL, NULL);
  CATCH:
  return NULL;
}

JNIEXPORT void JNICALL
Java_com_ap_transmission_btc_Native_torrentSetLocation(
    JNIEnv *env, jclass __unused c, jlong jsession, jint torrentId, jstring jpath) {
  jboolean isCopy;
  const char *path = (*env)->GetStringUTFChars(env, jpath, &isCopy);
  SetLocationData d = {torrentId, path};
  runInTransmissionThreadEx(env, jsession, torrentSetLocation, &d);
  CATCH:
  (*env)->ReleaseStringUTFChars(env, jpath, path);
}
// -------------------------------------------------------------------------------------------------
