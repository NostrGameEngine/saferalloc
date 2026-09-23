package org.ngengine.saferalloc;

import java.io.*;
import java.nio.file.*;
import java.util.*;

final class NativeLibraryLoader {
  private NativeLibraryLoader() {}

  static void load(String baseName) {
    if (isIosRuntime()) {
      return;
    }

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

    if (!mapped.equals(Paths.get(mapped).getFileName().toString()) || mapped.equals(".") || mapped.equals("..")) {
      throw new UnsatisfiedLinkError("Invalid native library file name: " + mapped);
    }

    UnsatisfiedLinkError error = new UnsatisfiedLinkError(
        "Failed to extract/load native library from temp, user cache, or ~/.nge.");
    for (int rootIndex = 0; rootIndex < 3; rootIndex++) {
      Path libraryPath = null;
      Path directory = null;
      try {
        Path root = extractionRoot(rootIndex, baseName);
        if (root == null) continue;
        directory = NativeLibraryExtraction.createDirectory(root, baseName + "-");
        libraryPath = writeLibrary(directory, mapped, libraryBytes);
        System.load(libraryPath.toAbsolutePath().toString());
        return;
      } catch (IOException | UnsatisfiedLinkError | SecurityException | IllegalArgumentException
          | UnsupportedOperationException failure) {
        error.addSuppressed(failure);
        if (libraryPath != null) {
          try {
            Files.deleteIfExists(libraryPath);
          } catch (IOException | SecurityException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
          }
        }
        if (directory != null) {
          try {
            Files.deleteIfExists(directory);
          } catch (IOException | SecurityException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
          }
        }
      }
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

  private static Path extractionRoot(int index, String baseName) {
    if (index == 0) {
      String tmp = System.getProperty("java.io.tmpdir", "").trim();
      return tmp.isEmpty() ? null : Paths.get(tmp);
    }
    if (index == 1) {
      Path cacheRoot = getUserCacheRoot();
      return cacheRoot == null ? null : cacheRoot.resolve("ngengine").resolve(baseName);
    }
    String home = System.getProperty("user.home", "").trim();
    return home.isEmpty() ? null : Paths.get(home).resolve(".nge").resolve(baseName);
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

  static Path writeLibrary(Path dir, String mapped, byte[] bytes) throws IOException {
    Path out = dir.resolve(mapped);
    OutputStream stream = Files.newOutputStream(out, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    try (OutputStream data = stream) {
      out.toFile().deleteOnExit();
      data.write(bytes);
    } catch (IOException | RuntimeException failure) {
      try {
        Files.deleteIfExists(out);
      } catch (IOException | SecurityException cleanupFailure) {
        failure.addSuppressed(cleanupFailure);
      }
      throw failure;
    }
    return out;
  }

  private static boolean isAndroidRuntime() {
    // Avoid linking to android.* at compile time.
    String runtime = System.getProperty("java.runtime.name", "");
    String vm = System.getProperty("java.vm.name", "");
    return runtime.toLowerCase(Locale.ROOT).contains("android")
      || vm.toLowerCase(Locale.ROOT).contains("dalvik");
  }

  private static boolean isIosRuntime() {
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    String vm = System.getProperty("java.vm.name", "").toLowerCase(Locale.ROOT);
    return os.contains("ios") || vm.contains("substrate vm") && os.contains("darwin");
  }

  private static String detectOs() {
    if (isIosRuntime()) {
      return "ios";
    }
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
