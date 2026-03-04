#include <jni.h>
#include <stdint.h>
#include <string.h>

#if defined(SAFERALLOC_ANDROID_PASSTHROUGH)
  #include <stdlib.h>
  #include <errno.h>
#else
  #include "mimalloc.h"
#endif


#if defined(SAFERALLOC_ANDROID_PASSTHROUGH)
// Modern android has its own hardened allocator, so we just pass through
static void* safer_malloc(size_t size) { return malloc(size); }
static void* safer_calloc(size_t count, size_t size) { return calloc(count, size); }
static void* safer_realloc(void* p, size_t new_size) { return realloc(p, new_size); }
static void  safer_free(void* p) { free(p); }

static void* safer_malloc_aligned(size_t size, size_t alignment) {
  // Android/NDK: use posix_memalign for portability (aligned_alloc is not universally available).
  void* p = NULL;
  int rc = posix_memalign(&p, alignment, size);
  if (rc != 0) {
    return NULL;
  }
  return p;
}

#else
// for everything else we wrap mimalloc built with secure mode
static void* safer_malloc(size_t size) { return mi_malloc(size); }
static void* safer_calloc(size_t count, size_t size) { return mi_calloc(count, size); }
static void* safer_realloc(void* p, size_t new_size) { return mi_realloc(p, new_size); }
static void  safer_free(void* p) { mi_free(p); }
static void* safer_malloc_aligned(size_t size, size_t alignment) { return mi_malloc_aligned(size, alignment); }
#endif

static jobject safer_new_direct_byte_buffer(JNIEnv* env, void* p, jlong size) {
  if (p == NULL) {
    return NULL;
  }
  return (*env)->NewDirectByteBuffer(env, p, size);
}

JNIEXPORT jlong JNICALL Java_org_ngengine_saferalloc_SaferAllocNative_malloc(JNIEnv* env, jclass cls, jlong size) {
  (void)env; (void)cls;
  void* p = safer_malloc((size_t)size);
  return (jlong)(uintptr_t)p;
}

JNIEXPORT jlong JNICALL Java_org_ngengine_saferalloc_SaferAllocNative_calloc(JNIEnv* env, jclass cls, jlong count, jlong size) {
  (void)env; (void)cls;
  void* p = safer_calloc((size_t)count, (size_t)size);
  return (jlong)(uintptr_t)p;
}

JNIEXPORT jlong JNICALL Java_org_ngengine_saferalloc_SaferAllocNative_realloc(JNIEnv* env, jclass cls, jlong addr, jlong newSize) {
  (void)env; (void)cls;
  void* p = safer_realloc((void*)(uintptr_t)addr, (size_t)newSize);
  return (jlong)(uintptr_t)p;
}

JNIEXPORT void JNICALL Java_org_ngengine_saferalloc_SaferAllocNative_free(JNIEnv* env, jclass cls, jlong addr) {
  (void)env; (void)cls;
  safer_free((void*)(uintptr_t)addr);
}

JNIEXPORT jlong JNICALL Java_org_ngengine_saferalloc_SaferAllocNative_mallocAligned(JNIEnv* env, jclass cls, jlong size, jlong alignment) {
  (void)env; (void)cls;
  void* p = safer_malloc_aligned((size_t)size, (size_t)alignment);
  return (jlong)(uintptr_t)p;
}

JNIEXPORT jobject JNICALL Java_org_ngengine_saferalloc_SaferAllocNative_wrapMemByteBuffer(JNIEnv* env, jclass cls, jlong addr, jlong size) {
  (void)cls;
  return safer_new_direct_byte_buffer(env, (void*)(uintptr_t)addr, size);
}

JNIEXPORT jlong JNICALL Java_org_ngengine_saferalloc_SaferAllocNative_addressMemByteBuffer(JNIEnv* env, jclass cls, jobject buffer) {
  (void)cls;
  if (buffer == NULL) {
    return 0;
  }
  void* p = (*env)->GetDirectBufferAddress(env, buffer);
  if (p == NULL) {
    jclass iae = (*env)->FindClass(env, "java/lang/IllegalArgumentException");
    if (iae != NULL) {
      (*env)->ThrowNew(env, iae, "buffer must be a direct ByteBuffer");
    }
    return 0;
  }
  return (jlong)(uintptr_t)p;
}
