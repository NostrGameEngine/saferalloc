package org.ngengine.saferalloc;

import java.nio.file.*;
import java.util.*;
import com.jme3.nativebootstrap.common.OperatingSystem;
import com.jme3.nativebootstrap.directories.NativeDirectories;
import com.jme3.nativebootstrap.loader.NativeLoader;
import com.jme3.nativebootstrap.loader.NativeResource;
import com.jme3.nativebootstrap.os.OperatingSystems;

final class NativeLibraryLoader {
  private NativeLibraryLoader() {}

  static void load(String baseName) {
    OperatingSystem os = OperatingSystems.detect();
    if (os == OperatingSystem.IOS) {
      return;
    }

    Path override = getOverridePath(baseName);
    if (override != null) {
      System.load(override.toAbsolutePath().toString());
      return;
    }

    // Android libraries are installed from the AAR's jni entries.
    if (os == OperatingSystem.ANDROID) {
      System.loadLibrary(baseName);
      return;
    }

    String arch = detectArch();
    String mapped = System.mapLibraryName(baseName);

    // Resources come from natives modules, packaged at: natives/<os>/<arch>/<mapped>
    String resource = "/natives/" + os.name().toLowerCase(Locale.ROOT) + "/" + arch + "/" + mapped;

    new NativeLoader(path -> System.load(path.toString()), name -> System.loadLibrary(name)).load(
        NativeDirectories.candidates(baseName, () -> os),
        Collections.singletonList(NativeResource.fromClasspath(NativeLibraryLoader.class, resource)));
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

  private static String detectArch() {
    String a = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);

    if (a.equals("aarch64") || a.equals("arm64")) return "aarch64";
    return "x86_64";
  }

}
