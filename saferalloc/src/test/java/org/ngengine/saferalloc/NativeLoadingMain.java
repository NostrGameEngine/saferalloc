package org.ngengine.saferalloc;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/** A plain Java 8 entry point for testing JNI loading in an isolated JVM. */
public final class NativeLoadingMain {
  public static void main(String[] args) throws Exception {
    if (args.length > 1 && args[1].equals("android")) {
      System.setProperty("java.runtime.name", "Android Runtime");
      System.setProperty("java.vm.name", "Dalvik");
    }
    if (Boolean.getBoolean("saferalloc.test.deny-filestore")) {
      System.setSecurityManager(new SecurityManager() {
        @Override public void checkPermission(java.security.Permission permission) {
          if (permission.getName().equals("getFileStoreAttributes")) {
            throw new SecurityException("Mount point not found");
          }
        }
      });
    }
    ByteBuffer buffer = SaferAlloc.malloc(32);
    if (buffer == null || !buffer.isDirect() || buffer.capacity() != 32) {
      throw new AssertionError("Native allocation failed");
    }
    try {
      buffer.put(0, (byte) 42);
      if (buffer.get(0) != 42 || SaferAlloc.address(buffer) == 0) {
        throw new AssertionError("Native allocation is unusable");
      }
    } finally {
      SaferAlloc.free(buffer);
    }
    if (args.length > 0 && !args[0].isEmpty()) {
      Path expected = Paths.get(args[0]).toRealPath();
      try (Stream<Path> files = Files.walk(expected)) {
        Path nativeFile = files.filter(path -> path.getFileName().toString().equals(System.mapLibraryName("saferalloc")))
            .findFirst().orElseThrow(() -> new AssertionError("Native library was not extracted under " + expected));
        System.out.println("EXTRACTED " + nativeFile);
      }
    }
    System.out.println("NATIVE_OK");
  }
}
