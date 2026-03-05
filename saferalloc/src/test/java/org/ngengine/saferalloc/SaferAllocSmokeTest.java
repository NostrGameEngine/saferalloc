package org.ngengine.saferalloc;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SaferAllocSmokeTest {

  @Test
  void mallocWriteReadFree() {
    ByteBuffer buffer = SaferAlloc.malloc(64);
    assertNotNull(buffer);
    long p = SaferAlloc.address(buffer);
    assertNotEquals(0L, p);
    SaferAllocProbe.writeByte(p, 0, (byte) 123);
    assertEquals((byte) 123, SaferAllocProbe.readByte(p, 0));
    SaferAlloc.free(buffer);
  }

  @Test
  void mallocByteBufferWriteReadReallocFree() {
    ByteBuffer buffer = SaferAlloc.malloc(16);
    assertNotNull(buffer);
    assertTrue(buffer.isDirect());
    assertEquals(16, buffer.capacity());
    long addr = SaferAlloc.address(buffer);
    assertNotEquals(0L, addr);

    buffer.put(0, (byte) 42);
    assertEquals((byte) 42, buffer.get(0));

    ByteBuffer resized = SaferAlloc.realloc(buffer, 32);
    assertNotNull(resized);
    assertTrue(resized.isDirect());
    assertEquals(32, resized.capacity());

    SaferAlloc.free(resized);
  }

  @Test
  void allocatedBytesCounterTracksLifecycle() {
    long before = SaferAlloc.currentAllocatedBytes();
    ByteBuffer buffer = SaferAlloc.malloc(128);
    assertNotNull(buffer);

    long afterAlloc = SaferAlloc.currentAllocatedBytes();
    assertTrue(afterAlloc >= before, "allocated bytes should not decrease after alloc");
    assertTrue(afterAlloc > before, "allocated bytes should increase after alloc");

    ByteBuffer grown = SaferAlloc.realloc(buffer, 512);
    assertNotNull(grown);
    long afterRealloc = SaferAlloc.currentAllocatedBytes();
    assertTrue(afterRealloc >= before, "allocated bytes should remain tracked after realloc");

    SaferAlloc.free(grown);
    long afterFree = SaferAlloc.currentAllocatedBytes();
    assertEquals(before, afterFree, "allocated bytes should return to baseline after free");
  }

}
