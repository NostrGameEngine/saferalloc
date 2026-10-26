package org.ngengine.saferalloc;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.jme3.nativebootstrap.common.OperatingSystem;
import com.jme3.nativebootstrap.os.OperatingSystems;

import static org.junit.jupiter.api.Assertions.*;

class NativeLibraryForkedTest {
  @TempDir Path root;

  @Test
  void fallbackExtractionSurvivesAnotherJvmLoadingAndExiting() throws Exception {
    OperatingSystem os = OperatingSystems.detect();
    Assumptions.assumeTrue(os == OperatingSystem.LINUX || os == OperatingSystem.MACOS,
        "This regression checks POSIX native file lifetime and inode isolation");
    Path signals = Files.createDirectory(root.resolve("signals"));
    Path home = Files.createDirectory(root.resolve("home"));
    // A regular file cannot serve as an extraction directory, forcing the fallback
    // without relying on a particular host's noexec mount configuration.
    Path unavailableTemp = Files.createFile(root.resolve("unavailable-temp"));
    Path firstLog = root.resolve("first.log");
    Path secondLog = root.resolve("second.log");
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(40);
    Process first = null;
    Process second = null;
    try {
      first = startChild(signals, home, unavailableTemp, "first", firstLog);
      awaitLoaded(first, signals.resolve("first.loaded"), deadline, firstLog);
      List<Path> firstLibraries = extractedLibraries(home);
      assertEquals(1, firstLibraries.size(), "The first JVM must use the home fallback");
      Path firstLibrary = firstLibraries.get(0);
      byte[] original = Files.readAllBytes(firstLibrary);
      Object originalFileKey = Files.readAttributes(firstLibrary, BasicFileAttributes.class).fileKey();
      assertNotNull(originalFileKey, "A POSIX file identity is required for this regression");

      second = startChild(signals, home, unavailableTemp, "second", secondLog);
      awaitLoaded(second, signals.resolve("second.loaded"), deadline, secondLog);
      List<Path> concurrentLibraries = extractedLibraries(home);
      assertEquals(2, concurrentLibraries.size(), "Each JVM must extract its own native file");
      Path secondLibrary = concurrentLibraries.stream().filter(path -> !path.equals(firstLibrary))
          .findFirst().orElseThrow(() -> new AssertionError("Missing second native file"));
      assertNotEquals(firstLibrary.getParent(), secondLibrary.getParent());
      assertNotEquals(originalFileKey,
          Files.readAttributes(secondLibrary, BasicFileAttributes.class).fileKey());
      assertArrayEquals(original, Files.readAllBytes(firstLibrary));
      assertEquals(originalFileKey, Files.readAttributes(firstLibrary, BasicFileAttributes.class).fileKey());

      Files.createFile(signals.resolve("second.continue"));
      awaitExit(second, deadline, secondLog);
      assertFalse(Files.exists(secondLibrary), "The second JVM cleans up its own native file");
      assertTrue(Files.exists(firstLibrary), "The first JVM's loaded native file must survive");
      assertArrayEquals(original, Files.readAllBytes(firstLibrary));
      assertEquals(originalFileKey, Files.readAttributes(firstLibrary, BasicFileAttributes.class).fileKey());

      Files.createFile(signals.resolve("first.continue"));
      awaitExit(first, deadline, firstLog);
      assertTrue(new String(Files.readAllBytes(firstLog), java.nio.charset.StandardCharsets.UTF_8)
          .contains("NATIVE_OK first"));
      assertTrue(extractedLibraries(home).isEmpty(), "Normal exits remove both extracted libraries");
    } finally {
      try {
        stopChild(second);
      } finally {
        stopChild(first);
      }
    }
  }

  private Process startChild(Path signals, Path home, Path unavailableTemp, String role, Path log)
      throws Exception {
    String classpath = NativeTestSupport.classpath(NativeLibraryForkMain.class)
        + File.pathSeparator + NativeTestSupport.nativeJar();
    List<String> command = new ArrayList<>();
    command.add(new File(System.getProperty("java.home"), "bin/java").getAbsolutePath());
    command.add("-Xmx96m");
    command.add("-XX:ErrorFile=" + root.resolve(role + "-hs_err_pid%p.log"));
    command.add("-Duser.home=" + home);
    command.add("-Djava.io.tmpdir=" + unavailableTemp);
    command.add("-Dnatives.tempDir=" + unavailableTemp);
    command.add("-Dnatives.cacheDir=" + home.resolve("cache"));
    command.add("-Dnatives.userHome=" + home);
    command.add("-Dnatives.withOsLibraries=false");
    command.add("-cp");
    command.add(classpath);
    command.add(NativeLibraryForkMain.class.getName());
    command.add(signals.toString());
    command.add(role);
    return new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
  }

  private static List<Path> extractedLibraries(Path home) throws Exception {
    try (Stream<Path> paths = Files.walk(home)) {
      return paths.filter(path -> path.getFileName().toString().equals(System.mapLibraryName("saferalloc")))
          .collect(Collectors.toList());
    }
  }

  private static void awaitLoaded(Process child, Path marker, long deadline, Path log) throws Exception {
    while (!Files.exists(marker)) {
      assertTrue(child.isAlive(), "Child exited before load barrier: " + readLog(log));
      assertTrue(System.nanoTime() < deadline, "Load barrier exceeded original deadline: " + readLog(log));
      Thread.sleep(5);
    }
  }

  private static void awaitExit(Process child, long deadline, Path log) throws Exception {
    long remaining = deadline - System.nanoTime();
    assertTrue(remaining > 0 && child.waitFor(remaining, TimeUnit.NANOSECONDS),
        "Child exceeded original deadline: " + readLog(log));
    assertEquals(0, child.exitValue(), readLog(log));
  }

  private static String readLog(Path log) throws Exception {
    return new String(Files.readAllBytes(log), java.nio.charset.StandardCharsets.UTF_8);
  }

  private static void stopChild(Process child) throws Exception {
    if (child == null) return;
    if (child.isAlive()) {
      child.destroy();
      if (!child.waitFor(1, TimeUnit.SECONDS)) {
        child.destroyForcibly();
        assertTrue(child.waitFor(4, TimeUnit.SECONDS), "Owned child failed to terminate");
      }
    }
    assertFalse(child.isAlive());
  }
}
