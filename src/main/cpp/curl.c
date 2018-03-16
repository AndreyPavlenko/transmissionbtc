#include "commons.h"
#include <stdio.h>
#include <string.h>
#include <errno.h>
#include <curl/curl.h>
#include <libtransmission/version.h>

JNIEXPORT void JNICALL
Java_com_ap_transmission_btc_Native_curl(JNIEnv *env, jclass __unused c,
                                         jstring jurl, jstring jdst, jint timeout) {
  const char *dst = (*env)->GetStringUTFChars(env, jdst, 0);
  FILE *file = fopen(dst, "wb");

  if (file == NULL) {
    throwEX(env, CLASS_IOEX, "Failed to open file %s: %s", dst, strerror(errno));
  }

  CURL *curl = curl_easy_init();

  if (curl) {
    char err[CURL_ERROR_SIZE];
    const char *url = (*env)->GetStringUTFChars(env, jurl, 0);
    curl_easy_setopt(curl, CURLOPT_URL, url);
    curl_easy_setopt(curl, CURLOPT_ERRORBUFFER, err);
    curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, NULL);
    curl_easy_setopt(curl, CURLOPT_WRITEDATA, file);
    curl_easy_setopt(curl, CURLOPT_SSL_VERIFYHOST, 0L);
    curl_easy_setopt(curl, CURLOPT_SSL_VERIFYPEER, 0L);
    curl_easy_setopt(curl, CURLOPT_PROXY_SSL_VERIFYHOST, 0L);
    curl_easy_setopt(curl, CURLOPT_PROXY_SSL_VERIFYPEER, 0L);
    curl_easy_setopt(curl, CURLOPT_USERAGENT, "Transmission/" SHORT_VERSION_STRING);
    curl_easy_setopt(curl, CURLOPT_TIMEOUT, timeout);

#ifndef NDEBUG
    curl_easy_setopt(curl, CURLOPT_VERBOSE, 1);
#endif

    CURLcode res = curl_easy_perform(curl);
    (*env)->ReleaseStringUTFChars(env, jurl, url);
    curl_easy_cleanup(curl);

    if (res != CURLE_OK) {
      throwEX(env, CLASS_IOEX, err);
    }
  } else {
    throwEX(env, CLASS_IOEX, "Failed to initialize curl");
  }

  CATCH:
  (*env)->ReleaseStringUTFChars(env, jdst, dst);
  if (file != NULL) fclose(file);
}
