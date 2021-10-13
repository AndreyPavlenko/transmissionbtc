#include <stdlib.h>
#include <unistd.h>
#include "commons.h"
#include "native_to_java.h"
#include "libtransmission/file.h"

#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wconversion"
#pragma ide diagnostic ignored "OCUnusedGlobalDeclarationInspection"

extern "C" {

static JavaVM *jvm;
static jclass classNative;
static jclass classStorageAccess;
static jmethodID addedOrChangedCallback;
static jmethodID stoppedCallback;
static jmethodID sessionChangedCallback;
static jmethodID scheduledAltSpeedCallback;
static jmethodID createDir;
static jmethodID openFile;
static jmethodID closeFileDescriptor;
static jmethodID renamePath;
static jmethodID removePath;

#define JvmAttach() \
  void *__env__ = nullptr;\
  jint _detached = jvm->GetEnv(&__env__, JNI_VERSION_1_2);\
  JNIEnv *env = (JNIEnv*) __env__;\
  if ((_detached == JNI_EDETACHED) && (jvm->AttachCurrentThread(&env, NULL) != JNI_OK)) {\
    logErr("JavaVM->AttachCurrentThread() failed");\
    goto CATCH;\
  }
#define JvmDetach() \
   if (env->ExceptionCheck()) logErr("Native to Java call failed");\
   if (_detached == JNI_EDETACHED) jvm->DetachCurrentThread()

static void rndChars(char *str, int num);

JNIEXPORT void JNICALL
Java_com_ap_transmission_btc_Native_nativeToJavaInit(JNIEnv *env, jclass c) {
  env->GetJavaVM(&jvm);
  jclass sa = env->FindClass("com/ap/transmission/btc/StorageAccess");
  classNative = (jclass) env->NewGlobalRef(c);
  classStorageAccess = (jclass) env->NewGlobalRef(sa);
  addedOrChangedCallback = env->GetStaticMethodID(c, "torrentAddedOrChangedCallback",
                                                     "()V");
  stoppedCallback = env->GetStaticMethodID(c, "torrentStoppedCallback", "()V");
  sessionChangedCallback = env->GetStaticMethodID(c, "sessionChangedCallback", "()V");
  scheduledAltSpeedCallback = env->GetStaticMethodID(c, "scheduledAltSpeedCallback", "()V");
  createDir = env->GetStaticMethodID(sa, "createDir", "(Ljava/lang/String;)Z");
  openFile = env->GetStaticMethodID(sa, "openFile", "(Ljava/lang/String;ZZZ)I");
  closeFileDescriptor = env->GetStaticMethodID(sa, "closeFileDescriptor", "(I)Z");
  renamePath = env->GetStaticMethodID(sa, "renamePath",
                                         "(Ljava/lang/String;Ljava/lang/String;)Z");
  removePath = env->GetStaticMethodID(sa, "removePath", "(Ljava/lang/String;)Z");
}

void callAddedOrChangedCallback() {
  JvmAttach();
  env->CallStaticVoidMethod(classNative, addedOrChangedCallback);
  JvmDetach();
  CATCH:;
}

void callStoppedCallback() {
  JvmAttach();
  env->CallStaticVoidMethod(classNative, stoppedCallback);
  JvmDetach();
  CATCH:;
}

void callSessionChangedCallback() {
  JvmAttach();
  env->CallStaticVoidMethod(classNative, sessionChangedCallback);
  JvmDetach();
  CATCH:;
}

void callScheduledAltSpeedCallback() {
  JvmAttach();
  env->CallStaticVoidMethod(classNative, scheduledAltSpeedCallback);
  JvmDetach();
  CATCH:;
}

} // extern "C"

bool tr_android_dir_create(char const *path) {
  jboolean result = JNI_FALSE;
{
  JvmAttach();
  jstring jpath = env->NewStringUTF(path);
  result = env->CallStaticBooleanMethod(classStorageAccess, createDir, jpath);
  JvmDetach();
}  CATCH:
  return result == JNI_TRUE;
}

tr_sys_file_t tr_android_file_open(char const *path, int flags) {
  int fd = -1;
{
  JvmAttach();
  jboolean create = (flags & (TR_SYS_FILE_CREATE | TR_SYS_FILE_CREATE_NEW)) ? JNI_TRUE : JNI_FALSE;
  jboolean writable = JNI_TRUE;
  jboolean truncate = (flags & TR_SYS_FILE_TRUNCATE) ? JNI_TRUE : JNI_FALSE;
  jstring jpath = env->NewStringUTF(path);
  fd = env->CallStaticIntMethod(classStorageAccess, openFile, jpath,
                                   create, writable, truncate);
  JvmDetach();
}  CATCH:
  return (fd == -1) ? TR_BAD_SYS_FILE : fd;
}

tr_sys_file_t tr_android_file_open_temp(char *path_template) {
  int len = strlen(path_template);
  char *suffix = (path_template + len - 6);

  for (int i = 0; i < 100; i++) {
    rndChars(suffix, 6);
    if (access(path_template, F_OK) == -1) {
      return tr_android_file_open(path_template, TR_SYS_FILE_CREATE_NEW);
    }
  }

  strcpy(suffix, "XXXXXX\0");
  logErr("Failed to create temporary file: %s", path_template);
  return TR_BAD_SYS_FILE;
}

bool tr_android_file_close(tr_sys_file_t handle) {
  jboolean result = JNI_FALSE;
{
  JvmAttach();
  result = env->CallStaticBooleanMethod(classStorageAccess, closeFileDescriptor,
                                           (jint) handle);
  JvmDetach();
}  CATCH:
  return result == JNI_TRUE;
}

bool tr_android_path_rename(char const *src_path, char const *dst_path) {
  jboolean result = JNI_FALSE;
{
  JvmAttach();
  jstring src = env->NewStringUTF(src_path);
  jstring dst = env->NewStringUTF(dst_path);
  result = env->CallStaticBooleanMethod(classStorageAccess, renamePath, src, dst);
  JvmDetach();
}  CATCH:
  return result == JNI_TRUE;
}

bool tr_android_path_remove(char const *path) {
  jboolean result = JNI_FALSE;
{
  JvmAttach();
  jstring jpath = env->NewStringUTF(path);
  result = env->CallStaticBooleanMethod(classStorageAccess, removePath, jpath);
  JvmDetach();
}  CATCH:
  return result == JNI_TRUE;
}

#define rndChar() "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"[random() % 62]

static void rndChars(char *str, int num) {
  for (; num > 0; num--, str++) *str = rndChar();
}

#pragma clang diagnostic pop