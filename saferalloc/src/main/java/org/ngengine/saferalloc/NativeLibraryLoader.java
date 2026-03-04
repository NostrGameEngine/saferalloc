package org.ngengine.saferalloc;

import java.io.*;
import java.nio.file.*;
import java.util.*;

final class NativeLibraryLoader {
  private NativeLibraryLoader() {}

  static void load(String baseName) {
    Path override = getOverridePath(baseName);
    if (override != null) {
      System.load(override.toAbsolutePath().toString());
      return;
    }

    // On Android, the preferred path is System.loadLibrary() (APK/AAR jniLibs).
    // If that fails, fall back to resource extraction (useful when running on non-standard classpaths/tests).
    if (isAndroidRuntime()) {
      try {
        System.loadLibrary(baseName);
        return;
      } catch (Throwable ignored) {
        // fall through to resource path
      }
    }

    String os = detectOs();
    String arch = detectArch();
    String mapped = System.mapLibraryName(baseName);

    // Resources come from natives modules, packaged at: natives/<os>/<arch>/<mapped>
    String resource = "natives/" + os + "/" + arch + "/" + mapped;
    InputStream in = NativeLibraryLoader.class.getClassLoader().getResourceAsStream(resource);
    if (in == null) {
      System.loadLibrary(baseName);
      return;
    }

    byte[] libraryBytes;
    try (InputStream data = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      copyTo(data, out);
      libraryBytes = out.toByteArray();
    } catch (IOException e) {
      throw new UnsatisfiedLinkError("Failed to read bundled native library: " + e);
    }

    List<Path> candidates = new ArrayList<>();
    addTempCandidate(candidates, baseName);
    addUserCacheCandidate(candidates, baseName);
    addUserHomeFallbackCandidate(candidates, baseName);

    Throwable lastFailure = null;
    for (Path candidateDir : candidates) {
      try {
        Path libraryPath = writeLibrary(candidateDir, mapped, libraryBytes);
        System.load(libraryPath.toAbsolutePath().toString());
        return;
      } catch (Throwable t) {
        lastFailure = t;
      }
    }

    UnsatisfiedLinkError error = new UnsatisfiedLinkError("Failed to extract/load native library from temp, user cache, or ~/.nge.");
    if (lastFailure != null) {
      error.initCause(lastFailure);
    }
    throw error;
  }

  private static Path getOverridePath(String baseName) {
    String override = System.getProperty("saferalloc.native.override", "").trim();
    if (override.isEmpty()) {
      return null;
    }

    Path path = Paths.get(override);
    if (Files.isDirectory(path)) {
      path = path.resolve(System.mapLibraryName(baseName));
    }
    if (!Files.exists(path)) {
      throw new UnsatisfiedLinkError("Native override does not exist: " + path);
    }
    if (!Files.isReadable(path)) {
      throw new UnsatisfiedLinkError("Native override is not readable: " + path);
    }
    return path;
  }

  private static void addTempCandidate(List<Path> candidates, String baseName) {
    String tmp = System.getProperty("java.io.tmpdir", "").trim();
    if (tmp.isEmpty()) {
      return;
    }
    Path tmpRoot = Paths.get(tmp);
    if (!Files.isDirectory(tmpRoot) || !Files.isWritable(tmpRoot)) {
      return;
    }
    try {
      Path dir = Files.createTempDirectory(tmpRoot, baseName + "-");
      dir.toFile().deleteOnExit();
      candidates.add(dir);
    } catch (IOException ignored) {
      // Fall through to user cache.
    }
  }

  private static void addUserCacheCandidate(List<Path> candidates, String baseName) {
    Path cacheRoot = getUserCacheRoot();
    if (cacheRoot == null) {
      return;
    }
    Path dir = cacheRoot.resolve("ngengine").resolve(baseName);
    if (ensureUsableDirectory(dir)) {
      candidates.add(dir);
    }
  }

  private static void addUserHomeFallbackCandidate(List<Path> candidates, String baseName) {
    String userHome = System.getProperty("user.home", "").trim();
    if (userHome.isEmpty()) {
      return;
    }
    Path dir = Paths.get(userHome).resolve(".nge").resolve(baseName);
    if (ensureUsableDirectory(dir)) {
      candidates.add(dir);
    }
  }

  private static Path getUserCacheRoot() {
    String userHome = System.getProperty("user.home", "").trim();
    if (userHome.isEmpty()) {
      return null;
    }

    Path home = Paths.get(userHome);
    String os = detectOs();
    if ("windows".equals(os)) {
      return home.resolve("AppData").resolve("Local");
    }
    if ("macos".equals(os)) {
      return home.resolve("Library").resolve("Caches");
    }
    // linux + android (fallback)
    return home.resolve(".cache");
  }

  private static boolean ensureUsableDirectory(Path dir) {
    try {
      Files.createDirectories(dir);
      return Files.isDirectory(dir) && Files.isWritable(dir);
    } catch (IOException e) {
      return false;
    }
  }

  private static Path writeLibrary(Path dir, String mapped, byte[] bytes) throws IOException {
    if (!ensureUsableDirectory(dir)) {
      throw new IOException("Directory is not writable: " + dir);
    }

    Path out = dir.resolve(mapped);
    Files.write(out, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    if (!Files.isReadable(out)) {
      throw new IOException("Extracted library is not readable: " + out);
    }
    out.toFile().deleteOnExit();
    return out;
  }

  private static boolean isAndroidRuntime() {
    // Avoid linking to android.* at compile time.
    String runtime = System.getProperty("java.runtime.name", "");
    String vm = System.getProperty("java.vm.name", "");
    return runtime.toLowerCase(Locale.ROOT).contains("android")
      || vm.toLowerCase(Locale.ROOT).contains("dalvik");
  }

  private static String detectOs() {
    if (isAndroidRuntime()) {
      return "android";
    }
    String n = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    if (n.contains("win")) return "windows";
    if (n.contains("mac") || n.contains("darwin")) return "macos";
    return "linux";
  }

  private static String detectArch() {
    String a = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);

    if (isAndroidRuntime()) {
      // Map Java arch to Android ABI folder names.
      if (a.equals("aarch64") || a.equals("arm64")) return "arm64-v8a";
      if (a.startsWith("arm")) return "armeabi-v7a";
      if (a.equals("x86")) return "x86";
      return "x86_64";
    }

    if (a.equals("aarch64") || a.equals("arm64")) return "aarch64";
    return "x86_64";
  }

  private static void copyTo(InputStream in, OutputStream out) throws IOException {
    byte[] buffer = new byte[8192];
    int read;
    while ((read = in.read(buffer)) != -1) {
      out.write(buffer, 0, read);
    }
  }
}
