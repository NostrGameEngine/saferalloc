package org.ngengine.saferalloc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class NativeLibraryExtractionTest {
  @TempDir Path root;

  @Test
  void createsUniquePrivateDirectories() throws IOException {
    Path first = NativeLibraryExtraction.createDirectory(root, "saferalloc-");
    Path second = NativeLibraryExtraction.createDirectory(root, "saferalloc-");
    assertNotEquals(first, second);
    assertEquals(root.toRealPath(), first.getParent());
    assertEquals(root.toRealPath(), second.getParent());
    if (Files.getFileStore(first).supportsFileAttributeView("posix")) {
      Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(first);
      assertFalse(permissions.contains(PosixFilePermission.GROUP_WRITE));
      assertFalse(permissions.contains(PosixFilePermission.OTHERS_WRITE));
      assertFalse(permissions.contains(PosixFilePermission.GROUP_EXECUTE));
      assertFalse(permissions.contains(PosixFilePermission.OTHERS_EXECUTE));
    }
  }

  @Test
  void neverOverwritesAnExistingNative() throws IOException {
    Path directory = NativeLibraryExtraction.createDirectory(root, "saferalloc-");
    Path nativeFile = directory.resolve("libsaferalloc.so");
    byte[] original = new byte[] {1, 2, 3};
    Files.write(nativeFile, original);
    assertThrows(IOException.class, () ->
        NativeLibraryLoader.writeLibrary(directory, nativeFile.getFileName().toString(), new byte[] {4, 5}));
    assertArrayEquals(original, Files.readAllBytes(nativeFile));
  }

  @Test
  void neverFollowsAPoisonedNativeSymlink() throws IOException {
    if (!Files.getFileStore(root).supportsFileAttributeView("posix")) return;
    Path directory = NativeLibraryExtraction.createDirectory(root, "saferalloc-");
    Path victim = root.resolve("victim");
    Files.write(victim, new byte[] {1, 2, 3});
    Path link = directory.resolve("libsaferalloc.so");
    Files.createSymbolicLink(link, victim);
    assertThrows(IOException.class, () ->
        NativeLibraryLoader.writeLibrary(directory, link.getFileName().toString(), new byte[] {4, 5}));
    assertTrue(Files.isSymbolicLink(link));
    assertArrayEquals(new byte[] {1, 2, 3}, Files.readAllBytes(victim));
  }

  @Test
  void rejectsReplaceableParent() throws IOException {
    if (!Files.getFileStore(root).supportsFileAttributeView("posix")) return;
    // macOS java.io.tmpdir is below a private user directory, so use shared /tmp.
    Path replaceable = Files.createTempDirectory(Paths.get("/tmp"), "saferalloc-unsafe-");
    try {
      Files.setPosixFilePermissions(replaceable,
          PosixFilePermissions.fromString("rwxrwxrwx"));
      IOException failure = assertThrows(IOException.class, () ->
          NativeLibraryExtraction.createDirectory(replaceable, "saferalloc-"));
      assertTrue(failure.getMessage().startsWith("Native extraction ancestor is writable by other users:"));
    } finally {
      // Preserve the assertion failure even if a regression creates a child directory.
      try (Stream<Path> paths = Files.walk(replaceable)) {
        for (Path path : paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new)) {
          Files.deleteIfExists(path);
        }
      }
    }
  }

  @Test
  void acceptsWritableParentInsidePrivateAncestor() throws IOException {
    if (!Files.getFileStore(root).supportsFileAttributeView("posix")) return;
    Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
    Path protectedParent = Files.createDirectory(root.resolve("writable"));
    Files.setPosixFilePermissions(protectedParent, PosixFilePermissions.fromString("rwxrwxrwx"));

    Path directory = NativeLibraryExtraction.createDirectory(protectedParent, "saferalloc-");
    assertEquals(protectedParent.toRealPath(), directory.getParent());
    assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(directory));
  }
}
