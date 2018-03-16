#include "commons.h"
#include <stdlib.h>

JNIEXPORT void JNICALL
Java_com_ap_transmission_btc_Native_envUnset(
    JNIEnv *env, jclass __unused c, jstring jname) {
  const char *name = (*env)->GetStringUTFChars(env, jname, 0);
  unsetenv(name);
  (*env)->ReleaseStringUTFChars(env, jname, name);
}

JNIEXPORT void JNICALL
Java_com_ap_transmission_btc_Native_envSet(
    JNIEnv *env, jclass __unused c, jstring jname, jstring jvalue) {
  if ((jvalue == NULL) || ((*env)->GetStringUTFLength(env, jvalue) == 0)) {
    Java_com_ap_transmission_btc_Native_envUnset(env, c, jname);
  } else {
    const char *name = (*env)->GetStringUTFChars(env, jname, 0);
    const char *value = (*env)->GetStringUTFChars(env, jvalue, 0);
    setenv(name, value, 1);
    (*env)->ReleaseStringUTFChars(env, jname, name);
    (*env)->ReleaseStringUTFChars(env, jvalue, value);
  }
}