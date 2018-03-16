#include "commons.h"
#include <stdlib.h>
#include <semaphore.h>

JNIEXPORT jlong JNICALL
Java_com_ap_transmission_btc_Native_semCreate(JNIEnv *__unused env, jclass __unused c) {
  sem_t *sem = malloc(sizeof(sem_t));
  memset(sem, 0, sizeof(sem_t));
  sem_init(sem, 0, 0);
  return (jlong) sem;
}

JNIEXPORT void JNICALL
Java_com_ap_transmission_btc_Native_semDestroy(JNIEnv *__unused env, jclass __unused c,
                                                 jlong jsem) {
  sem_t *sem = (sem_t *) jsem;
  sem_destroy(sem);
  free(sem);
}

JNIEXPORT void JNICALL
Java_com_ap_transmission_btc_Native_semPost(JNIEnv *__unused env, jclass __unused c,
                                                jlong jsem) {
  sem_t *sem = (sem_t *) jsem;
  sem_post(sem);
}