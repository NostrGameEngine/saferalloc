package org.ngengine.saferalloc;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.jme3.nativebootstrap.common.OperatingSystem;
import com.jme3.nativebootstrap.os.OperatingSystems;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class NativeLibraryLoadingTest {
  @TempDir Path root;

  @Test void loadsBundledNativeFromPrivateTemp() throws Exception {
    Path temp = Files.createDirectory(root.resolve("temp"));
    runProbe(temp, root, temp, NativeTestSupport.nativeJar(), false, 0);
    // Windows may retain a loaded DLL until the process exits, after JVM cleanup hooks run.
    if (OperatingSystems.detect() != OperatingSystem.WINDOWS) {
      try (java.util.stream.Stream<Path> files = Files.list(temp)) {
        assertEquals(0, files.count(), "Extracted files should be removed at normal JVM exit");
      }
    }
  }

  @Test void fallsBackToUserCache() throws Exception {
    Path blocked = Files.write(root.resolve("blocked-temp"), new byte[]{1});
    Path cache;
    switch (OperatingSystems.detect()) {
      case WINDOWS: cache = root.resolve("AppData/Local/saferalloc"); break;
      case MACOS: cache = root.resolve("Library/Caches/saferalloc"); break;
      default: cache = root.resolve(".cache/saferalloc");
    }
    runProbe(blocked, root, cache, NativeTestSupport.nativeJar(), false, 0);
  }

  @Test void fallsBackToBootstrapHomeWhenTempAndCacheAreUnusable() throws Exception {
    Path blocked = Files.write(root.resolve("blocked-temp"), new byte[]{1});
    for (String cache : Arrays.asList("Library", "AppData", ".cache")) {
      Files.write(root.resolve(cache), new byte[]{1});
    }
    runProbe(blocked, root, root.resolve(".jme3/natives/saferalloc"), NativeTestSupport.nativeJar(), false, 0);
  }

  @Test void honorsNativeBootstrapOverridesWithTheRebrandedLoader() throws Exception {
    Path blocked = Files.write(root.resolve("blocked"), new byte[]{1});
    Path home = root.resolve("override-home");
    runProbe(blocked, blocked, home.resolve(".custom/natives/saferalloc"), NativeTestSupport.nativeJar(), false, 0,
        "-Dnatives.tempDir=" + blocked, "-Dnatives.cacheDir=" + blocked,
        "-Dnatives.userHome=" + home, "-Dnatives.namespace=.custom");
  }

  @Test void preservesAllDestinationFailures() throws Exception {
    Path blocked = Files.write(root.resolve("blocked"), new byte[]{1});
    String output = runProbe(blocked, blocked, null, NativeTestSupport.nativeJar(), false, 1);
    assertTrue(output.contains("Native loading failed"), output);
    assertEquals(4, output.split("Suppressed:", -1).length - 1, output);
  }

  @Test void loadsWithoutMountMetadataOrUserNameLookup() throws Exception {
    Path temp = Files.createDirectory(root.resolve("temp"));
    runProbe(temp, root, temp, NativeTestSupport.nativeJar(), false, 0,
        "-Djava.security.manager=allow", "-Dsaferalloc.test.deny-filestore=true", "-Duser.name=?");
  }

  @Test void preservesNativeFileAndDirectoryOverrides() throws Exception {
    Path blocked = Files.write(root.resolve("blocked-temp"), new byte[]{1});
    Path binary = Paths.get(System.getProperty("saferalloc.native.override"));
    for (Path override : Arrays.asList(binary, binary.getParent())) {
      runProbe(blocked, blocked, null, null, false, 0, "-Dsaferalloc.native.override=" + override);
    }
  }

  @Test void usesSystemLibraryPathWhenClasspathResourceIsAbsent() throws Exception {
    Path blocked = Files.write(root.resolve("blocked-temp"), new byte[]{1});
    Path binary = Paths.get(System.getProperty("saferalloc.native.override"));
    runProbe(blocked, blocked, null, null, false, 0, "-Djava.library.path=" + binary.getParent());
  }

  @Test void androidPrefersSystemLibraryPath() throws Exception {
    Path blocked = Files.write(root.resolve("blocked-temp"), new byte[]{1});
    Path binary = Paths.get(System.getProperty("saferalloc.native.override"));
    runProbe(blocked, blocked, null, null, true, 0, "-Djava.library.path=" + binary.getParent());
  }

  @Test void androidDoesNotExtractClasspathResourcesWhenSystemLibraryIsMissing() throws Exception {
    assumeTrue(OperatingSystems.detect() != OperatingSystem.WINDOWS);
    String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
    String abi = arch.equals("aarch64") || arch.equals("arm64") ? "arm64-v8a" : "x86_64";
    Path jar = root.resolve("android-resources.jar");
    try (JarFile original = new JarFile(NativeTestSupport.nativeJar().toFile());
         ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(jar))) {
      output.putNextEntry(new ZipEntry("natives/android/" + abi + "/" + System.mapLibraryName("saferalloc")));
      try (InputStream input = original.getInputStream(original.getJarEntry(NativeTestSupport.resourcePath().substring(1)))) {
        byte[] bytes = new byte[8192];
        int size;
        while ((size = input.read(bytes)) != -1) output.write(bytes, 0, size);
      }
      output.closeEntry();
    }
    Path temp = Files.createDirectory(root.resolve("temp"));
    String output = runProbe(temp, root, null, jar, true, 1);
    assertTrue(output.contains("UnsatisfiedLinkError"), output);
    try (java.util.stream.Stream<Path> files = Files.list(temp)) {
      assertEquals(0, files.count(), "Android must use the installed native library");
    }
  }

  private String runProbe(Path temp, Path home, Path expectedRoot, Path nativeJar,
      boolean android, int expectedExit, String... options) throws Exception {
    boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("win");
    List<String> command = new ArrayList<>(Arrays.asList(
        Paths.get(System.getProperty("java.home"), "bin", windows ? "java.exe" : "java").toString(),
        "-Djava.io.tmpdir=" + temp, "-Duser.home=" + home, "-Djava.library.path=" + root.resolve("no-system-natives")));
    command.addAll(Arrays.asList(options));
    String classpath = NativeTestSupport.classpath(NativeLoadingMain.class);
    if (nativeJar != null) classpath += File.pathSeparator + nativeJar;
    command.addAll(Arrays.asList("-cp", classpath, NativeLoadingMain.class.getName(),
        expectedRoot == null ? "" : expectedRoot.toString(), android ? "android" : "desktop"));
    Path log = root.resolve("native-load.log");
    ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile());
    builder.environment().remove("XDG_CACHE_HOME");
    builder.environment().remove("LOCALAPPDATA");
    Process process = builder.start();
    try {
      assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Native loading probe timed out");
      String output = new String(Files.readAllBytes(log), StandardCharsets.UTF_8);
      assertEquals(expectedExit, process.exitValue(), output);
      if (expectedExit == 0) assertTrue(output.contains("NATIVE_OK"), output);
      return output;
    } finally {
      process.destroyForcibly();
    }
  }
}
