package org.ngengine.saferalloc;


public final class SaferAllocFunctionPointers {
  private SaferAllocFunctionPointers() {}

  public static long malloc() {
    SaferAlloc.ensureLoaded();
    return SaferAllocNative.mallocFunctionPointer();
  }

  public static long calloc() {
    SaferAlloc.ensureLoaded();
    return SaferAllocNative.callocFunctionPointer();
  }

  public static long realloc() {
    SaferAlloc.ensureLoaded();
    return SaferAllocNative.reallocFunctionPointer();
  }

  public static long free() {
    SaferAlloc.ensureLoaded();
    return SaferAllocNative.freeFunctionPointer();
  }

  public static long alignedAlloc() {
    SaferAlloc.ensureLoaded();
    return SaferAllocNative.alignedAllocFunctionPointer();
  }

  public static long alignedFree() {
    SaferAlloc.ensureLoaded();
    return SaferAllocNative.alignedFreeFunctionPointer();
  }
}
