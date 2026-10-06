package org.ngengine.saferalloc;

import org.junit.jupiter.api.Test;

class NativeLibraryLoaderTest {
  @Test
  void iosRuntimeSkipsDesktopExtractionAndLoading() {
    String os = System.getProperty("os.name");
    try {
      for (String label : new String[]{"iOS", "iPhone OS", "iPad"}) {
        System.setProperty("os.name", label);
        NativeLibraryLoader.load("missing-ios-static-library");
      }
    } finally {
      restoreProperty("os.name", os);
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
