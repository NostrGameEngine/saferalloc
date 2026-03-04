# SaferAlloc

SaferAlloc is a small JNI binding that provides hardened native memory allocation for java.
The underlying implementations are platform-specific and might change over time.

## Current Platform Behavior
 
|Platform|Underlying Allocator|Notes|
|-|-|-|
|Linux x86_64|mimalloc|mimalloc with MI_SECURE=ON|
|Linux aarch64|mimalloc|mimalloc with MI_SECURE=ON|
|macOS x86_64|mimalloc|mimalloc with MI_SECURE=ON|
|macOS aarch64|mimalloc|mimalloc with MI_SECURE=ON|
|Windows x86_64|mimalloc|mimalloc with MI_SECURE=ON|
|Android 11+|passthrough|Modern Android has its own hardened allocator (Scudo), so we just pass through. |

## Public API

`org.ngengine.saferalloc.SaferAlloc` exposes:
- `ByteBuffer malloc(int size)`
- `ByteBuffer calloc(int count, int size)`
- `ByteBuffer realloc(ByteBuffer buffer, int newSize)`
- `ByteBuffer mallocAligned(int size, int alignment)`
- `void free(ByteBuffer buffer)`
- `void free(long address)`
- `long address(ByteBuffer buffer)`

Notes:
- `malloc/calloc/mallocAligned` return `null` on allocation failure.
- `realloc(buffer, newSize)` throws `OutOfMemoryError` if growth fails and the old allocation is still valid.
- `realloc(buffer, 0)` may return `null` and free the old allocation.
- invalid sizes throw `IllegalArgumentException`.
- `mallocAligned(size, alignment)` requires `alignment` to be a power of two and a multiple of pointer size.
- `calloc(count, size)` rejects capacities larger than `Integer.MAX_VALUE`.


## Note

- This API manages native memory manually. Always call `free(buffer)`.
- After successful `realloc`, treat the old buffer as invalid and use the returned one only. If `realloc` throws, keep using the old buffer (it is still allocated).
- `free(null)` is a no-op.
