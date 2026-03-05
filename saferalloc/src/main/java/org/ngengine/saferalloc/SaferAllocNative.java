package org.ngengine.saferalloc;

import java.nio.Buffer;
import java.nio.ByteBuffer;


public final class SaferAllocNative {
  private SaferAllocNative() {}

  public static native long malloc(long size);
  public static native long calloc(long count, long size);
  public static native long realloc(long addr, long newSize);
  public static native void free(long addr);
  public static native long mallocAligned(long size, long alignment);
  public static  native long currentAllocatedBytes();
  public static native long mallocFunctionPointer();
  public static native long callocFunctionPointer();
  public static native long reallocFunctionPointer();
  public static native long freeFunctionPointer();
  public static native long alignedAllocFunctionPointer();
  public static native long alignedFreeFunctionPointer();
  public static native int pointerSizeBytes();

  public static native ByteBuffer wrapMemByteBuffer(long addr, long size);
  public static native long addressMemByteBuffer(Buffer buffer);
}
