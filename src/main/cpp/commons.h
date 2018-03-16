#ifndef TRANSMISSIONBTC_COMMONS_H
#define TRANSMISSIONBTC_COMMONS_H

#include <jni.h>
#include <stdio.h>
#include <errno.h>
#include <stdlib.h>
#include <string.h>
#include <semaphore.h>
#include <android/log.h>
#include <libtransmission/transmission.h>
#include <libtransmission/utils.h>

#define LOG_TAG "transmissionbtc"
#define CLASS_IOEX "java/io/IOException"
#define CLASS_IAEX "java/lang/IllegalArgumentException"
#define CLASS_NSTEX "com/ap/transmission/btc/torrent/NoSuchTorrentException"
#define CLASS_DUPEX "com/ap/transmission/btc/torrent/DuplicateTorrentException"

#define CATCH __onException__

#define logErr(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define __FILENAME__ (strrchr(__FILE__, '/') ? strrchr(__FILE__, '/') + 1 : __FILE__)
#define throwEX(env, className, ...) { throw(__FILENAME__, __LINE__, env, className, __VA_ARGS__); goto CATCH; }
#define throwIOEX(env, ...) throwEX(env, CLASS_IOEX, __VA_ARGS__)
#define throwOrLog(env, className, throw, ...) \
      if (throw) { throwEX(env, className, __VA_ARGS__); } \
      else { logErr(__VA_ARGS__); goto CATCH; }

typedef struct Err {
    bool isSet;

    void (*set)(struct Err *err, const char *ex, const char *msg, ...);
} Err;
#define errCheck(err) if (err->isSet) goto CATCH

jint throw(const char *, int, JNIEnv *, const char *, const char *, ...);

size_t cp(JNIEnv *env, const char *, const char *);

#define ctorFromFileEx(env, jsession, jpath) \
  ctorFromFile(env, jsession, jpath, true); \
  if ((*env)->ExceptionCheck(env)) goto CATCH

tr_ctor *ctorFromFile(JNIEnv *, jlong, jstring, bool);

#define infoFromFileEx(env, jsession, jpath, info) \
  if ((infoFromFile(env, jsession, jpath, info, true) != TR_PARSE_OK) || (*env)->ExceptionCheck(env)) \
    goto CATCH

tr_parse_result infoFromFile(JNIEnv *, jlong, jstring, tr_info *, bool);

#define findTorrentByHashFunc ((void *(*)(tr_session *, void *, Err *)) findTorrentByHash)

int findTorrentByHash(tr_session *session, uint8_t *hash, Err *err);

#define findTorrentByIdEx(session, id, err) findTorrentById(session,  id, err); errCheck(err)

tr_torrent *findTorrentById(tr_session *session, int id, Err *err);

#define getFileInfoEx(tor, idx, err) getFileInfo(tor, idx, err); errCheck(err)

const tr_file *getFileInfo(tr_torrent *tor, uint32_t idx, Err *err);

#define getWantedFileInfoEx(tor, idx, err) getWantedFileInfo(tor, idx, err); errCheck(err)

const tr_file *getWantedFileInfo(tr_torrent *tor, uint32_t idx, Err *err);

#define runInTransmissionThreadEx(env, jsession, func, data) \
  runInTransmissionThread(__FILENAME__, __LINE__, env, jsession, func, data);\
  if ((*env)->ExceptionCheck(env)) goto CATCH

void *runInTransmissionThread(const char *file, int line, JNIEnv *env, jlong jsession,
                              void *(*func)(tr_session *session, void *userData, Err *err),
                              void *userData);

#endif //TRANSMISSIONBTC_COMMONS_H
