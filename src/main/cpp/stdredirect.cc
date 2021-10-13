#include <jni.h>

#ifdef NDEBUG
JNIEXPORT void JNICALL
Java_com_ap_transmission_btc_Native_stdRedirect(JNIEnv *__unused env, jclass __unused c) {}
#else

#include "commons.h"
#include <stdlib.h>
#include <unistd.h>
#include <pthread.h>
#include <android/log.h>

extern "C" {

static int pfd[2];
static pthread_t thr;

static void *thread_func(void *__unused args) {
  ssize_t rdsz;
  char buf[128];
  while ((rdsz = read(pfd[0], buf, sizeof buf - 1)) > 0) {
    if (buf[rdsz - 1] == '\n') --rdsz;
    buf[rdsz] = 0;
    __android_log_write(ANDROID_LOG_DEBUG, "libtransmission", buf);
  }
  return 0;
}

JNIEXPORT void JNICALL
Java_com_ap_transmission_btc_Native_stdRedirect(JNIEnv *env, jclass __unused c) {
  setvbuf(stdout, 0, _IOLBF, 0);
  setvbuf(stderr, 0, _IONBF, 0);

  pipe(pfd);
  dup2(pfd[1], 1);
  dup2(pfd[1], 2);

  if (pthread_create(&thr, 0, thread_func, 0) == -1) {
    throwEX(env, "java/lang/RuntimeException", "pthread_create() failed");
  } else {
    pthread_detach(thr);
  }

  CATCH:;
}

} // extern "C"
#endif