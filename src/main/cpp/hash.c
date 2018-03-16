#include "commons.h"
#include <libtransmission/transmission.h>
#include <libtransmission/utils.h>

JNIEXPORT jint JNICALL
Java_com_ap_transmission_btc_Native_hashLength(JNIEnv *__unused env, jclass __unused c) {
  return SHA_DIGEST_LENGTH;
}

JNIEXPORT jstring JNICALL
Java_com_ap_transmission_btc_Native_hashBytesToString(
    JNIEnv *env, jclass __unused c, jbyteArray jhash) {
  char hash[SHA_DIGEST_LENGTH];
  char hashString[1 + 2 * SHA_DIGEST_LENGTH];
  (*env)->GetByteArrayRegion(env, jhash, 0, sizeof(hash), (jbyte *) hash);
  tr_binary_to_hex(hash, hashString, sizeof(hash));
  return (*env)->NewStringUTF(env, (const char *) hashString);
}

JNIEXPORT jbyteArray JNICALL
Java_com_ap_transmission_btc_Native_hashStringToBytes(
    JNIEnv *env, jclass __unused c, jstring jhashString) {
  char hash[SHA_DIGEST_LENGTH];
  const char *hashString = (*env)->GetStringUTFChars(env, jhashString, 0);
  tr_hex_to_binary(hashString, hash, sizeof(hash));
  (*env)->ReleaseStringUTFChars(env, jhashString, hashString);
  jbyteArray jhash = (*env)->NewByteArray(env, sizeof(hash));
  (*env)->SetByteArrayRegion(env, jhash, 0, sizeof(hash), (const jbyte *) hash);
  return jhash;
}

JNIEXPORT jbyteArray JNICALL
Java_com_ap_transmission_btc_Native_hashGetTorrentHash(
    JNIEnv *env, jclass __unused c, jstring torrentPath) {
  tr_info info;
  infoFromFileEx(env, 0, torrentPath, &info);
  jbyteArray jhash = (*env)->NewByteArray(env, SHA_DIGEST_LENGTH);
  (*env)->SetByteArrayRegion(env, jhash, 0, SHA_DIGEST_LENGTH, (const jbyte *) info.hash);
  tr_metainfoFree(&info);
  return jhash;
  CATCH:
  return NULL;
}
