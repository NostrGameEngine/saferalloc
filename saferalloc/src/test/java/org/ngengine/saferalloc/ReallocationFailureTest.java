package org.ngengine.saferalloc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReallocationFailureTest {
  @Test
  void wrapperAllocationFailurePreservesOriginal() throws Exception {
    List<String> command = new ArrayList<>();
    command.add(System.getProperty("java.home") + "/bin/java");
    command.add("-Xms16m");
    command.add("-Xmx16m");
    command.add("-XX:+UseSerialGC");
    command.add("-XX:-UseGCOverheadLimit");
    String nativeOverride = System.getProperty("saferalloc.native.override");
    assertNotNull(nativeOverride, "the test needs the configured test native library");
    command.add("-Dsaferalloc.native.override=" + nativeOverride);
    // Gradle workers and the JUnit console need not expose test classes through
    // java.class.path. These code sources also handle separate main/test outputs.
    String classpath = NativeTestSupport.classpath(ReallocationFailureMain.class);
    command.add("-cp");
    command.add(classpath);
    command.add(ReallocationFailureMain.class.getName());

    Path output = Files.createTempFile("saferalloc-realloc-failure-", ".log");
    Process child = null;
    try {
      child = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile()).start();
      assertTrue(child.waitFor(60, TimeUnit.SECONDS), "heap-exhaustion child JVM timed out");
      String log = new String(Files.readAllBytes(output), StandardCharsets.UTF_8);
      assertEquals(0, child.exitValue(), log);
      assertTrue(log.contains("REALLOC_WRAPPER_FAILURE_PRESERVED_ORIGINAL"), log);
    } finally {
      if (child != null && child.isAlive()) {
        child.destroyForcibly();
        child.waitFor();
      }
      Files.deleteIfExists(output);
    }
  }
}
