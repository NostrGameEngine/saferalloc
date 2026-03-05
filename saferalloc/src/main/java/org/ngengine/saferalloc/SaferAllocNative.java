package org.ngengine.saferalloc;

import java.nio.ByteBuffer;


final class SaferAllocNative {
  private SaferAllocNative() {}

  static native long malloc(long size);
  static native long calloc(long count, long size);
  static native long realloc(long addr, long newSize);
  static native void free(long addr);
  static native long mallocAligned(long size, long alignment);
  static native long currentAllocatedBytes();
  static native long mallocFunctionPointer();
  static native long callocFunctionPointer();
  static native long reallocFunctionPointer();
  static native long freeFunctionPointer();
  static native long alignedAllocFunctionPointer();
  static native long alignedFreeFunctionPointer();
  static native int pointerSizeBytes();

  static native ByteBuffer wrapMemByteBuffer(long addr, long size);
  static native long addressMemByteBuffer(ByteBuffer buffer);
}
