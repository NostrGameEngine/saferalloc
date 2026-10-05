package org.ngengine.saferalloc;

import java.nio.ByteBuffer;

/** Runs in a small, isolated heap so native allocation can outlive Java allocation. */
public final class AllocationFailureMain {
  private static Object[] retained;

  public static void main(String[] args) {
    String operation = args[0];
    ByteBuffer warm = allocate(operation);
    SaferAlloc.free(warm);
    long before = SaferAlloc.currentAllocatedBytes();
    retained = new Object[1000000];
    int used = 0;
    try {
      while (used < retained.length) retained[used++] = new byte[16];
    } catch (OutOfMemoryError expected) {
      // Keep these objects reachable until the ByteBuffer allocation has failed.
    }
    boolean failed = false;
    ByteBuffer result = null;
    try {
      result = allocate(operation);
    } catch (OutOfMemoryError expected) {
      failed = true;
    }
    long after = SaferAlloc.currentAllocatedBytes();
    retained = null;
    System.gc();
    if (result != null) SaferAlloc.free(result);
    if (!failed) throw new AssertionError("The test did not exhaust the Java heap");
    if (after != before) {
      throw new AssertionError(operation + " leaked " + (after - before) + " native bytes");
    }
    System.out.println(operation + ": wrapper allocation failed without leaking native memory");
  }

  private static ByteBuffer allocate(String operation) {
    if ("calloc".equals(operation)) return SaferAlloc.calloc(64, 64);
    if ("aligned".equals(operation)) return SaferAlloc.mallocAligned(4096, 64);
    return SaferAlloc.malloc(4096);
  }
}
