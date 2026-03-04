#include <jni.h>
#include <stdint.h>

#include "mimalloc.h"

// Test-only JNI probes. These are compiled only for the debug/probe native test build.
// They exist for destructive testing only and remain separate from the production binding API.

JNIEXPORT jint JNICALL Java_org_ngengine_saferalloc_SaferAllocProbeNative_probeDoubleFree(JNIEnv* env, jclass cls) {
  (void)env; (void)cls;
  void* p = mi_malloc(64);
  if (!p) return 2;
  mi_free(p);
  mi_free(p); // secure mode: detected/ignored per mimalloc docs
  return 0;
}

JNIEXPORT jint JNICALL Java_org_ngengine_saferalloc_SaferAllocProbeNative_probeOverflow(JNIEnv* env, jclass cls) {
  (void)env; (void)cls;
  uint8_t* p = (uint8_t*)mi_malloc(16);
  if (!p) return 2;
  for (int i = 0; i < 32; i++) {
    p[i] = (uint8_t)i;
  }
  mi_free(p);
  return 0;
}

JNIEXPORT jint JNICALL Java_org_ngengine_saferalloc_SaferAllocProbeNative_probeUseAfterFree(JNIEnv* env, jclass cls) {
  (void)env; (void)cls;
  uint8_t* p = (uint8_t*)mi_malloc(32);
  if (!p) return 2;
  p[0] = 7;
  mi_free(p);
  volatile uint8_t x = p[0];
  (void)x;
  p[0] = 8;
  return 0;
}

JNIEXPORT void JNICALL Java_org_ngengine_saferalloc_SaferAllocProbeNative_writeByte(JNIEnv* env, jclass cls, jlong addr, jint offset, jbyte value) {
  (void)env; (void)cls;
  uint8_t* p = (uint8_t*)(uintptr_t)addr;
  p[offset] = (uint8_t)value;
}

JNIEXPORT jbyte JNICALL Java_org_ngengine_saferalloc_SaferAllocProbeNative_readByte(JNIEnv* env, jclass cls, jlong addr, jint offset) {
  (void)env; (void)cls;
  uint8_t* p = (uint8_t*)(uintptr_t)addr;
  return (jbyte)p[offset];
}

