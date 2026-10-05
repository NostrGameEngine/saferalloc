package org.ngengine.saferalloc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class NativeLibraryLoaderTest {
  @Test
  void detectsDesktopOperatingSystems() throws Exception {
    String originalOs = System.getProperty("os.name");
    String originalRuntime = System.getProperty("java.runtime.name");
    String originalVm = System.getProperty("java.vm.name");
    try {
      System.setProperty("java.runtime.name", "OpenJDK Runtime Environment");
      System.setProperty("java.vm.name", "OpenJDK 64-Bit Server VM");
      Method detectOs = NativeLibraryLoader.class.getDeclaredMethod("detectOs");
      detectOs.setAccessible(true);
      String[][] cases = {
          {"Linux", "linux"},
          {"Windows 11", "windows"},
          {"Mac OS X", "macos"},
          {"Darwin", "macos"}
      };
      for (String[] testCase : cases) {
        System.setProperty("os.name", testCase[0]);
        assertEquals(testCase[1], detectOs.invoke(null), testCase[0]);
      }
    } finally {
      restoreProperty("os.name", originalOs);
      restoreProperty("java.runtime.name", originalRuntime);
      restoreProperty("java.vm.name", originalVm);
    }
  }

  private static void restoreProperty(String name, String value) {
    if (value == null) {
      System.clearProperty(name);
    } else {
      System.setProperty(name, value);
    }
  }
}
