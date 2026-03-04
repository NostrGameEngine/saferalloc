package org.ngengine.saferalloc;

final class SaferAllocProbeNative {
  private SaferAllocProbeNative() {}

  static native int probeDoubleFree();
  static native int probeOverflow();
  static native int probeUseAfterFree();


  static native void writeByte(long addr, int offset, byte value);
  static native byte readByte(long addr, int offset);
}
