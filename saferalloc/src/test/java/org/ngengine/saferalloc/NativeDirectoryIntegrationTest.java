package org.ngengine.saferalloc;

import java.io.IOException;
import java.io.UncheckedIOException;
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
import com.jme3.nativebootstrap.directories.NativeDirectories;
import com.jme3.nativebootstrap.loader.NativeLoader;
import com.jme3.nativebootstrap.loader.NativeResource;
import com.jme3.nativebootstrap.os.OperatingSystems;
import java.util.Collections;
import java.nio.file.attribute.PosixFileAttributeView;

import static org.junit.jupiter.api.Assertions.*;

class NativeDirectoryIntegrationTest {
  @TempDir Path root;

  private Path createDirectory(Path parent) {
    return NativeDirectories.fromRoots("saferalloc", Collections.singletonList(parent),
        OperatingSystems::detect).get(0).get();
  }

  @Test
  void createsUniquePrivateDirectories() throws IOException {
    Path first = createDirectory(root);
    Path second = createDirectory(root);
    assertNotEquals(first, second);
    assertEquals(root.toRealPath(), first.getParent());
    assertEquals(root.toRealPath(), second.getParent());
    if (Files.getFileAttributeView(first, PosixFileAttributeView.class) != null) {
      Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(first);
      assertFalse(permissions.contains(PosixFilePermission.GROUP_WRITE));
      assertFalse(permissions.contains(PosixFilePermission.OTHERS_WRITE));
      assertFalse(permissions.contains(PosixFilePermission.GROUP_EXECUTE));
      assertFalse(permissions.contains(PosixFilePermission.OTHERS_EXECUTE));
    }
  }

  @Test
  void neverOverwritesAnExistingNative() throws Exception {
    Path directory = createDirectory(root);
    Path nativeFile = directory.resolve(System.mapLibraryName("saferalloc"));
    byte[] original = new byte[] {1, 2, 3};
    Files.write(nativeFile, original);
    NativeResource resource = NativeResource.fromClasspath(SaferAlloc.class, NativeTestSupport.resourcePath());
    assertThrows(UnsatisfiedLinkError.class, () -> new NativeLoader(path -> fail("Must not link a populated directory"))
        .load(Collections.singletonList(() -> directory), Collections.singletonList(resource)));
    assertArrayEquals(original, Files.readAllBytes(nativeFile));
  }

  @Test
  void neverFollowsAPoisonedNativeSymlink() throws Exception {
    if (Files.getFileAttributeView(root, PosixFileAttributeView.class) == null) return;
    Path directory = createDirectory(root);
    Path victim = root.resolve("victim");
    Files.write(victim, new byte[] {1, 2, 3});
    Path link = directory.resolve(System.mapLibraryName("saferalloc"));
    Files.createSymbolicLink(link, victim);
    NativeResource resource = NativeResource.fromClasspath(SaferAlloc.class, NativeTestSupport.resourcePath());
    assertThrows(UnsatisfiedLinkError.class, () -> new NativeLoader(path -> fail("Must not link a poisoned directory"))
        .load(Collections.singletonList(() -> directory), Collections.singletonList(resource)));
    assertTrue(Files.isSymbolicLink(link));
    assertArrayEquals(new byte[] {1, 2, 3}, Files.readAllBytes(victim));
  }

  @Test
  void rejectsReplaceableParent() throws IOException {
    if (Files.getFileAttributeView(root, PosixFileAttributeView.class) == null) return;
    // macOS java.io.tmpdir is below a private user directory, so use shared /tmp.
    Path replaceable = Files.createTempDirectory(Paths.get("/tmp"), "saferalloc-unsafe-");
    try {
      Files.setPosixFilePermissions(replaceable,
          PosixFilePermissions.fromString("rwxrwxrwx"));
      assertThrows(UncheckedIOException.class, () -> createDirectory(replaceable));
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
  void handlesWritableParentInsidePrivateAncestor() throws IOException {
    if (Files.getFileAttributeView(root, PosixFileAttributeView.class) == null) return;
    Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
    Path protectedParent = Files.createDirectory(root.resolve("writable"));
    Files.setPosixFilePermissions(protectedParent, PosixFilePermissions.fromString("rwxrwxrwx"));

    // macOS ACL traversal requires every ancestor to remain safe, even under a private parent.
    if (OperatingSystems.detect() == com.jme3.nativebootstrap.common.OperatingSystem.MACOS) {
      assertThrows(UncheckedIOException.class, () -> createDirectory(protectedParent));
      return;
    }
    Path directory = createDirectory(protectedParent);
    assertEquals(protectedParent.toRealPath(), directory.getParent());
    assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(directory));
  }
}
