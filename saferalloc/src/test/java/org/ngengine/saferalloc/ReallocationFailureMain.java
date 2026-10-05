package org.ngengine.saferalloc;

import java.nio.ByteBuffer;

/** Exhausts only a small child JVM's Java heap, while native allocation still succeeds. */
public final class ReallocationFailureMain {
  private static Object[] retained;
  private static ByteBuffer original;

  public static void main(String[] args) {
    // Load and warm the complete path before exhausting the heap, so the failure
    // occurs when JNI creates the replacement wrapper rather than loading classes.
    ByteBuffer warm = SaferAlloc.malloc(16);
    warm = SaferAlloc.realloc(warm, 4096);
    SaferAlloc.free(warm);

    long baseline = SaferAlloc.currentAllocatedBytes();
    original = SaferAlloc.malloc(16);
    if (original == null) throw new AssertionError("initial native allocation failed");
    for (int i = 0; i < original.capacity(); i++) {
      original.put(i, (byte) (i + 1));
    }
    original.position(3);
    original.limit(8);
    long address = SaferAlloc.address(original);
    long before = SaferAlloc.currentAllocatedBytes();

    retained = new Object[1000000];
    int used = 0;
    // A first OOM can release JVM-internal emergency resources. Refill after
    // that, keeping every allocated object strongly reachable through the call.
    for (int pass = 0; pass < 4; pass++) {
      try {
        while (used < retained.length) {
          retained[used] = new byte[16];
          used++;
        }
      } catch (OutOfMemoryError expected) {
        // The error and its stack trace also occupy heap. Retain them so GC
        // cannot reclaim that space to satisfy the subsequent wrapper allocation.
        if (used < retained.length) retained[used++] = expected;
      }
    }

    boolean failed = false;
    ByteBuffer result = null;
    try {
      result = SaferAlloc.realloc(original, 4096);
    } catch (OutOfMemoryError expected) {
      failed = true;
    }
    long after = SaferAlloc.currentAllocatedBytes();
    retained = null;
    System.gc();

    if (!failed) {
      if (result != null) SaferAlloc.free(result);
      throw new AssertionError("replacement wrapper did not fail under Java heap exhaustion");
    }
    // Check accounting BEFORE touching or freeing the original. On the buggy
    // implementation it has already been resized/freed, so dereferencing it here
    // would make this regression unsafe. Growing 16 to 4096 makes that observable.
    if (after != before) {
      throw new AssertionError("native accounting changed after wrapper failure: " + before + " -> " + after);
    }
    if (SaferAlloc.address(original) != address) {
      throw new AssertionError("original buffer address changed");
    }
    if (original.capacity() != 16 || original.position() != 3 || original.limit() != 8) {
      throw new AssertionError("original buffer state changed");
    }
    original.clear();
    for (int i = 0; i < original.capacity(); i++) {
      if (original.get(i) != (byte) (i + 1)) {
        throw new AssertionError("original content changed at index " + i);
      }
    }
    // Check that the retained allocation is still writable and may be freed once.
    original.put(0, (byte) 99);
    if (original.get(0) != (byte) 99) throw new AssertionError("original is not writable");
    SaferAlloc.free(original);
    original = null;
    if (SaferAlloc.currentAllocatedBytes() != baseline) {
      throw new AssertionError("native accounting did not return to baseline");
    }
    System.out.println("REALLOC_WRAPPER_FAILURE_PRESERVED_ORIGINAL");
  }
}
