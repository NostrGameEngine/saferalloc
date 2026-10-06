package org.ngengine.saferalloc;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.jar.JarFile;
import com.jme3.nativebootstrap.common.OperatingSystem;
import com.jme3.nativebootstrap.directories.NativeDirectories;
import com.jme3.nativebootstrap.loader.NativeLoader;
import com.jme3.nativebootstrap.os.OperatingSystems;

final class NativeTestSupport {
  private NativeTestSupport() {}

  static String classpath(Class<?> main) throws Exception {
    Set<String> entries = new LinkedHashSet<>();
    for (Class<?> type : Arrays.asList(main, SaferAlloc.class, NativeLoader.class, NativeDirectories.class, OperatingSystem.class, OperatingSystems.class)) {
      entries.add(Paths.get(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());
    }
    return String.join(File.pathSeparator, entries);
  }

  static Path nativeJar() {
    return Paths.get(System.getProperty("saferalloc.test.native.jar"));
  }

  static String resourcePath() throws Exception {
    try (JarFile jar = new JarFile(nativeJar().toFile())) {
      return jar.stream().filter(entry -> !entry.isDirectory() && entry.getName().startsWith("natives/"))
          .map(entry -> "/" + entry.getName()).findFirst()
          .orElseThrow(() -> new AssertionError("Native artifact contains no native resource"));
    }
  }
}
