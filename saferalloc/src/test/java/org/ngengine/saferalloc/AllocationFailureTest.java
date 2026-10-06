package org.ngengine.saferalloc;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AllocationFailureTest {
  @TempDir Path output;

  @Test
  void freesNewNativeAllocationsWhenJavaHeapIsExhausted() throws Exception {
    String java = new File(System.getProperty("java.home"), "bin/java").getAbsolutePath();
    String classpath = NativeTestSupport.classpath(AllocationFailureMain.class);
    String nativePath = System.getProperty("saferalloc.native.override");
    assertNotNull(nativePath, "the test needs the configured test native library");
    for (String operation : new String[] {"malloc", "calloc", "aligned"}) {
      Path log = output.resolve(operation + ".log");
      Process child = new ProcessBuilder(java, "-Xmx16m", "-XX:-UseGCOverheadLimit",
          "-Dsaferalloc.native.override=" + nativePath, "-cp", classpath,
          AllocationFailureMain.class.getName(), operation)
          .redirectErrorStream(true).redirectOutput(log.toFile()).start();
      try {
        assertTrue(child.waitFor(30, TimeUnit.SECONDS), "allocation test timed out");
        assertEquals(0, child.exitValue(), new String(Files.readAllBytes(log), "UTF-8"));
      } finally {
        child.destroyForcibly();
      }
    }
  }
}
