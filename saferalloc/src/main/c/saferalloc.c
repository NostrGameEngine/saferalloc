#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <stdatomic.h>

#if defined(SAFERALLOC_ANDROID_PASSTHROUGH)
  #include <errno.h>
  #include <malloc.h>
#else
  #include "mimalloc.h"
#endif

#if defined(SAFERALLOC_ANDROID_PASSTHROUGH)
// Modern android has its own hardened allocator, so we just pass through.
static void* safer_malloc(size_t size) { return malloc(size); }
static void* safer_calloc(size_t count, size_t size) { return calloc(count, size); }
static void* safer_realloc(void* p, size_t new_size) { return realloc(p, new_size); }
static void safer_free(void* p) { free(p); }

static size_t safer_usable_size(const void* p) {
  if (p == NULL) {
    return 0;
  }
  return malloc_usable_size((void*)p);
}

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
// For everything else we wrap mimalloc built with secure mode.
static void* safer_malloc(size_t size) { return mi_malloc(size); }
static void* safer_calloc(size_t count, size_t size) { return mi_calloc(count, size); }
static void* safer_realloc(void* p, size_t new_size) { return mi_realloc(p, new_size); }
static void safer_free(void* p) { mi_free(p); }
static void* safer_malloc_aligned(size_t size, size_t alignment) { return mi_malloc_aligned(size, alignment); }

static size_t safer_usable_size(const void* p) {
  if (p == NULL) {
    return 0;
  }
  return mi_usable_size((void*)p);
}
#endif

static _Atomic long long g_allocated_bytes = 0;

static void safer_track_alloc(void* p) {
  atomic_fetch_add_explicit(&g_allocated_bytes, (long long)safer_usable_size(p), memory_order_relaxed);
}

static void safer_track_free(void* p) {
  atomic_fetch_sub_explicit(&g_allocated_bytes, (long long)safer_usable_size(p), memory_order_relaxed);
}

static void* safer_tracked_malloc(size_t size) {
  void* p = safer_malloc(size);
  if (p != NULL) {
    safer_track_alloc(p);
  }
  return p;
}

static void* safer_tracked_calloc(size_t count, size_t size) {
  void* p = safer_calloc(count, size);
  if (p != NULL) {
    safer_track_alloc(p);
  }
  return p;
}

static void* safer_tracked_realloc(void* old_ptr, size_t new_size) {
  if (old_ptr == NULL) {
    if (new_size == 0) {
      return NULL;
    }
    return safer_tracked_malloc(new_size);
  }

  size_t old_size = safer_usable_size(old_ptr);
  void* p = safer_realloc(old_ptr, new_size);
  if (p == NULL) {
    // Allocation failed: original block remains valid for new_size > 0.
    // For realloc(ptr, 0), a NULL result may also mean old block was freed.
    if (new_size == 0) {
      atomic_fetch_sub_explicit(&g_allocated_bytes, (long long)old_size, memory_order_relaxed);
    }
    return NULL;
  }

  size_t actual_new_size = safer_usable_size(p);
  long long delta = (long long)actual_new_size - (long long)old_size;
  atomic_fetch_add_explicit(&g_allocated_bytes, delta, memory_order_relaxed);
  return p;
}

static void safer_tracked_free(void* p) {
  if (p == NULL) {
    return;
  }
  safer_track_free(p);
  safer_free(p);
}

static void* safer_tracked_malloc_aligned(size_t size, size_t alignment) {
  void* p = safer_malloc_aligned(size, alignment);
  if (p != NULL) {
    safer_track_alloc(p);
  }
  return p;
}

// Function-pointer entry points for external allocators (e.g., LWJGL).
static void* safer_fp_malloc(size_t size) { return safer_tracked_malloc(size); }
static void* safer_fp_calloc(size_t count, size_t size) { return safer_tracked_calloc(count, size); }
static void* safer_fp_realloc(void* p, size_t new_size) { return safer_tracked_realloc(p, new_size); }
static void safer_fp_free(void* p) { safer_tracked_free(p); }
static void* safer_fp_aligned_alloc(size_t alignment, size_t size) { return safer_tracked_malloc_aligned(size, alignment); }
static void safer_fp_aligned_free(void* p) { safer_tracked_free(p); }

static jobject safer_new_direct_byte_buffer(JNIEnv* env, void* p, jlong size) {
  if (p == NULL) {
    return NULL;
  }
  return (*env)->NewDirectByteBuffer(env, p, size);
}

static void safer_throw_illegal_argument(JNIEnv* env, const char* message) {
  jclass iae = (*env)->FindClass(env, "java/lang/IllegalArgumentException");
  if (iae != NULL) {
    (*env)->ThrowNew(env, iae, message);
  }
}

JNIEXPORT jlong JNICALL Java_org_ngengine_saferalloc_SaferAllocNative_malloc(JNIEnv* env, jclass cls, jlong size) {
  (void)env;
  (void)cls;
  return (jlong)(uintptr_t)safer_tracked_malloc((size_t)size);
}

JNIEXPORT jlong JNICALL Java_org_ngengine_saferalloc_SaferAllocNative_calloc(JNIEnv* env, jclass cls, jlong count, jlong size) {
  (void)env;
  (void)cls;
  return (jlong)(uintptr_t)safer_tracked_calloc((size_t)count, (size_t)size);
}

JNIEXPORT jlong JNICALL Java_org_ngengine_saferalloc_SaferAllocNative_realloc(JNIEnv* env, jclass cls, jlong addr, jlong newSize) {
  (void)env;
  (void)cls;
  return (jlong)(uintptr_t)safer_tracked_realloc((void*)(uintptr_t)addr, (size_t)newSize);
}

JNIEXPORT void JNICALL Java_org_ngengine_saferalloc_SaferAllocNative_free(JNIEnv* env, jclass cls, jlong addr) {
  (void)env;
  (void)cls;
  safer_tracked_free((void*)(uintptr_t)addr);
}

JNIEXPORT jlong JNICALL Java_org_ngengine_saferalloc_SaferAllocNative_mallocAligned(JNIEnv* env, jclass cls, jlong size, jlong alignment) {
  (void)env;
  (void)cls;
  return (jlong)(uintptr_t)safer_tracked_malloc_aligned((size_t)size, (size_t)alignment);
}

JNIEXPORT jlong JNICALL Java_org_ngengine_saferalloc_SaferAllocNative_currentAllocatedBytes(JNIEnv* env, jclass cls) {
  (void)env;
  (void)cls;
  return (jlong)atomic_load_explicit(&g_allocated_bytes, memory_order_relaxed);
}

JNIEXPORT jint JNICALL Java_org_ngengine_saferalloc_SaferAllocNative_pointerSizeBytes(JNIEnv* env, jclass cls) {
  (void)env;
  (void)cls;
  return (jint)sizeof(void*);
}

JNIEXPORT jlong JNICALL Java_org_ngengine_saferalloc_SaferAllocNative_mallocFunctionPointer(JNIEnv* env, jclass cls) {
  (void)env;
  (void)cls;
  return (jlong)(uintptr_t)&safer_fp_malloc;
}

JNIEXPORT jlong JNICALL Java_org_ngengine_saferalloc_SaferAllocNative_callocFunctionPointer(JNIEnv* env, jclass cls) {
  (void)env;
  (void)cls;
  return (jlong)(uintptr_t)&safer_fp_calloc;
}

JNIEXPORT jlong JNICALL Java_org_ngengine_saferalloc_SaferAllocNative_reallocFunctionPointer(JNIEnv* env, jclass cls) {
  (void)env;
  (void)cls;
  return (jlong)(uintptr_t)&safer_fp_realloc;
}

JNIEXPORT jlong JNICALL Java_org_ngengine_saferalloc_SaferAllocNative_freeFunctionPointer(JNIEnv* env, jclass cls) {
  (void)env;
  (void)cls;
  return (jlong)(uintptr_t)&safer_fp_free;
}

JNIEXPORT jlong JNICALL Java_org_ngengine_saferalloc_SaferAllocNative_alignedAllocFunctionPointer(JNIEnv* env, jclass cls) {
  (void)env;
  (void)cls;
  return (jlong)(uintptr_t)&safer_fp_aligned_alloc;
}

JNIEXPORT jlong JNICALL Java_org_ngengine_saferalloc_SaferAllocNative_alignedFreeFunctionPointer(JNIEnv* env, jclass cls) {
  (void)env;
  (void)cls;
  return (jlong)(uintptr_t)&safer_fp_aligned_free;
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
    safer_throw_illegal_argument(env, "buffer must be a direct Buffer");
    return 0;
  }
  return (jlong)(uintptr_t)p;
}
