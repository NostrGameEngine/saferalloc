package org.ngengine.saferalloc;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs optional destructive probes in a forked JVM (so crashes won't take down the test runner).
 * Enable with -DenableDestructiveTests=true
 */
public class DestructiveForkedTest {

  @Test
  void runForked() throws Exception {
    Assumptions.assumeTrue(Boolean.getBoolean("enableDestructiveTests"));

    String java = System.getProperty("java.home") + "/bin/java";
    String cp = System.getProperty("java.class.path");

    List<String> cmd = new ArrayList<>();
    cmd.add(java);

    String nativeOverride = System.getProperty("saferalloc.native.override");
    if (nativeOverride != null && !nativeOverride.trim().isEmpty()) {
      cmd.add("-Dsaferalloc.native.override=" + nativeOverride);
    }

    cmd.add("-cp");
    cmd.add(cp);
    cmd.add("org.ngengine.saferalloc.DestructiveMain");

    Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();

    StringBuilder out = new StringBuilder();
    try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
      String line;
      while ((line = br.readLine()) != null) out.append(line).append("\n");
    }
    int code = p.waitFor();

    // We don't assert code!=0 because secure mode doesn't guarantee a crash. We assert the probe runner completes.
    assertEquals(0, code, out.toString());
    assertTrue(out.toString().contains("DONE"), out.toString());
  }
}
