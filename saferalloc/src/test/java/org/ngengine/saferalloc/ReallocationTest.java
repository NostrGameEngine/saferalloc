package org.ngengine.saferalloc;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReallocationTest {
  @Test
  void growShrinkAndSameSizePreserveWholeCapacity() {
    long baseline = SaferAlloc.currentAllocatedBytes();
    ByteBuffer buffer = SaferAlloc.malloc(16);
    assertNotNull(buffer);
    try {
      for (int i = 0; i < buffer.capacity(); i++) {
        buffer.put(i, (byte) (i + 1));
      }
      buffer.position(3);
      buffer.limit(8);

      buffer = SaferAlloc.realloc(buffer, 32);
      assertBufferShape(buffer, 32);
      for (int i = 0; i < 16; i++) {
        assertEquals((byte) (i + 1), buffer.get(i));
      }

      buffer.position(10);
      buffer.limit(12);
      buffer = SaferAlloc.realloc(buffer, 8);
      assertBufferShape(buffer, 8);
      for (int i = 0; i < 8; i++) {
        assertEquals((byte) (i + 1), buffer.get(i));
      }

      buffer.position(2);
      buffer.limit(4);
      buffer = SaferAlloc.realloc(buffer, 8);
      assertBufferShape(buffer, 8);
      for (int i = 0; i < 8; i++) {
        assertEquals((byte) (i + 1), buffer.get(i));
      }
    } finally {
      SaferAlloc.free(buffer);
    }
    assertEquals(baseline, SaferAlloc.currentAllocatedBytes());
  }

  @Test
  void nullInputAllocatesAndZeroSizeFrees() {
    long baseline = SaferAlloc.currentAllocatedBytes();
    assertNull(SaferAlloc.realloc(null, 0));
    ByteBuffer buffer = SaferAlloc.realloc(null, 64);
    assertNotNull(buffer);
    try {
      assertBufferShape(buffer, 64);
      buffer.put(0, (byte) 42);
      assertTrue(SaferAlloc.currentAllocatedBytes() > baseline);
      buffer = SaferAlloc.realloc(buffer, 0);
      assertNull(buffer);
    } finally {
      SaferAlloc.free(buffer);
    }
    assertEquals(baseline, SaferAlloc.currentAllocatedBytes());
  }

  @Test
  void zeroCapacityAllocationCanBeResized() {
    long baseline = SaferAlloc.currentAllocatedBytes();
    ByteBuffer buffer = SaferAlloc.malloc(0);
    // A null zero-sized allocation is also a supported realloc input.
    try {
      buffer = SaferAlloc.realloc(buffer, 16);
      assertBufferShape(buffer, 16);
      buffer.put(15, (byte) 99);
      assertEquals((byte) 99, buffer.get(15));
    } finally {
      SaferAlloc.free(buffer);
    }
    assertEquals(baseline, SaferAlloc.currentAllocatedBytes());
  }

  @Test
  void invalidSizesLeaveOriginalUntouched() {
    long baseline = SaferAlloc.currentAllocatedBytes();
    ByteBuffer buffer = SaferAlloc.malloc(16);
    assertNotNull(buffer);
    try {
      buffer.put(0, (byte) 42);
      buffer.position(3);
      buffer.limit(8);
      long address = SaferAlloc.address(buffer);
      long allocated = SaferAlloc.currentAllocatedBytes();
      assertThrows(IllegalArgumentException.class, () -> SaferAlloc.realloc(buffer, -1));
      // Also validate the JNI boundary, independent of the public Java guard.
      assertThrows(IllegalArgumentException.class, () -> SaferAllocNative.reallocBuffer(buffer, -1));
      assertEquals(allocated, SaferAlloc.currentAllocatedBytes());
      assertEquals(address, SaferAlloc.address(buffer));
      assertEquals(3, buffer.position());
      assertEquals(8, buffer.limit());
      assertEquals((byte) 42, buffer.get(0));
    } finally {
      SaferAlloc.free(buffer);
    }
    assertThrows(IllegalArgumentException.class, () -> SaferAlloc.realloc(null, -1));
    assertEquals(baseline, SaferAlloc.currentAllocatedBytes());
  }

  @Test
  void heapBuffersAreRejectedBeforeAnyAllocationOrFree() {
    long baseline = SaferAlloc.currentAllocatedBytes();
    ByteBuffer buffer = ByteBuffer.allocate(16);
    buffer.put(0, (byte) 42);
    assertThrows(IllegalArgumentException.class, () -> SaferAlloc.realloc(buffer, 32));
    assertThrows(IllegalArgumentException.class, () -> SaferAlloc.realloc(buffer, 0));
    assertEquals((byte) 42, buffer.get(0));
    assertEquals(baseline, SaferAlloc.currentAllocatedBytes());
  }

  @Test
  void alignedAllocationCanBeResized() {
    long baseline = SaferAlloc.currentAllocatedBytes();
    ByteBuffer buffer = SaferAlloc.mallocAligned(64, 64);
    assertNotNull(buffer);
    try {
      buffer.put(63, (byte) 42);
      buffer = SaferAlloc.realloc(buffer, 128);
      assertBufferShape(buffer, 128);
      assertEquals((byte) 42, buffer.get(63));
    } finally {
      SaferAlloc.free(buffer);
    }
    assertEquals(baseline, SaferAlloc.currentAllocatedBytes());
  }

  private static void assertBufferShape(ByteBuffer buffer, int capacity) {
    assertNotNull(buffer);
    assertTrue(buffer.isDirect());
    assertEquals(capacity, buffer.capacity());
    assertEquals(0, buffer.position());
    assertEquals(capacity, buffer.limit());
  }
}
