package org.ngengine.saferalloc;

import java.nio.ByteBuffer;
import java.nio.Buffer;


public final class SaferAlloc {
  private static final Object LOAD_LOCK = new Object();
  private static volatile boolean loaded = false;

  private SaferAlloc() {}

  public static void ensureLoaded() {
    if (loaded) return;
    synchronized (LOAD_LOCK) {
      if (loaded) return;
      NativeLibraryLoader.load("saferalloc");
      loaded = true;
    }
  }

  public static ByteBuffer malloc(int size) {
    ensureLoaded();
    requireNonNegativeSize(size);
    long addr = SaferAllocNative.malloc(size);
    return wrapMemByteBuffer(addr, size);
  }

  public static ByteBuffer calloc(int count, int size) {
    ensureLoaded();
    requireNonNegativeSize(count);
    requireNonNegativeSize(size);
    int total = requireBufferCapacity(count, size);
    long addr = SaferAllocNative.calloc(count, size);
    return wrapMemByteBuffer(addr, total);
  }

  public static ByteBuffer realloc(ByteBuffer buffer, int newSize) {
    ensureLoaded();
    requireNonNegativeSize(newSize);
    long addr = address(buffer);
    long newAddr = SaferAllocNative.realloc(addr, newSize);
    if (newAddr == 0L && addr != 0L && newSize > 0) {
      throw new OutOfMemoryError("realloc failed; original buffer is still valid");
    }
    return wrapMemByteBuffer(newAddr, newSize);
  }

  public static ByteBuffer mallocAligned(int size, int alignment) {
    ensureLoaded();
    requireNonNegativeSize(size);
    requireValidAlignment(alignment);
    long addr = SaferAllocNative.mallocAligned(size, alignment);
    return wrapMemByteBuffer(addr, size);
  }

  public static long address(Buffer buffer) {
    ensureLoaded();
    if (buffer == null) {
      return 0L;
    }
    return SaferAllocNative.addressMemByteBuffer(buffer);
  }

  public static void free(ByteBuffer buffer) {
    ensureLoaded();
    long addr = address(buffer);
    if (addr != 0L) {
      SaferAllocNative.free(addr);
    }
  }

  public static void free(Buffer buffer) {
    ensureLoaded();
    long addr = address(buffer);
    if (addr != 0L) {
      SaferAllocNative.free(addr);
    }
  }

  public static void free(long address){
    ensureLoaded();
    if (address != 0L) {
      SaferAllocNative.free(address);
    }
  }

  public static long currentAllocatedBytes() {
    ensureLoaded();
    return SaferAllocNative.currentAllocatedBytes();
  }

  private static void requireNonNegativeSize(int size) {
    if (size < 0) {
      throw new IllegalArgumentException("size must be >= 0");
    }
  }

  private static int requireBufferCapacity(int count, int size) {
    long total = (long) count * (long) size;
    if (total > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("requested buffer size exceeds ByteBuffer limit: " + total);
    }
    return (int) total;
  }

  private static void requireValidAlignment(int alignment) {
    if (alignment <= 0) {
      throw new IllegalArgumentException("alignment must be > 0");
    }
    if ((alignment & (alignment - 1)) != 0) {
      throw new IllegalArgumentException("alignment must be a power of two");
    }
    int pointerSize = pointerSizeBytes();
    if ((alignment % pointerSize) != 0) {
      throw new IllegalArgumentException("alignment must be a multiple of pointer size (" + pointerSize + " bytes)");
    }
  }

  private static int pointerSizeBytes() {
    ensureLoaded();
    return SaferAllocNative.pointerSizeBytes();
  }

  private static ByteBuffer wrapMemByteBuffer(long addr, int size) {
    requireNonNegativeSize(size);
    if (addr == 0L) {
      return null;
    }
    return SaferAllocNative.wrapMemByteBuffer(addr, size);
  }
}
