package org.ngengine.saferalloc;

final class SaferAllocProbe {
  private SaferAllocProbe() {}

  static int probeDoubleFree() {
    SaferAlloc.ensureLoaded();
    return SaferAllocProbeNative.probeDoubleFree();
  }

  static int probeOverflow() {
    SaferAlloc.ensureLoaded();
    return SaferAllocProbeNative.probeOverflow();
  }

  static int probeUseAfterFree() {
    SaferAlloc.ensureLoaded();
    return SaferAllocProbeNative.probeUseAfterFree();
  }

  static void writeByte(long addr, int offset, byte value) {
    SaferAlloc.ensureLoaded();
    SaferAllocProbeNative.writeByte(addr, offset, value);
  }

  static byte readByte(long addr, int offset) {
    SaferAlloc.ensureLoaded();
    return SaferAllocProbeNative.readByte(addr, offset);
  }

}
