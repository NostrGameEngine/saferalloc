package org.ngengine.saferalloc;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Barrier-controlled child for the resource-extraction regression.
 */
public final class NativeLibraryForkMain {
  private NativeLibraryForkMain() {}

  public static void main(String[] args) throws Exception {
    Path signals = Paths.get(args[0]);
    String role = args[1];
    long deadline = System.nanoTime() + 30_000_000_000L;
    SaferAlloc.ensureLoaded();
    Files.createFile(signals.resolve(role + ".loaded"));
    while (!Files.exists(signals.resolve(role + ".continue"))) {
      if (System.nanoTime() >= deadline) throw new AssertionError("Child barrier deadline exceeded");
      Thread.sleep(5);
    }
    // Resolve JNI symbols after the other JVM has extracted and exited.
    long before = SaferAlloc.currentAllocatedBytes();
    ByteBuffer buffer = SaferAlloc.malloc(128);
    if (buffer == null || !buffer.isDirect()) throw new AssertionError("Native allocation failed");
    try {
      buffer.putInt(0, 0x73616665);
      if (buffer.getInt(0) != 0x73616665) throw new AssertionError("Native buffer corrupted");
      if (SaferAlloc.currentAllocatedBytes() <= before) throw new AssertionError("Allocation is not tracked");
    } finally {
      SaferAlloc.free(buffer);
    }
    if (SaferAlloc.currentAllocatedBytes() != before) throw new AssertionError("Native memory leaked");
    System.out.println("NATIVE_OK " + role);
  }
}
