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
|Windows aarch64|mimalloc|mimalloc with MI_SECURE=ON|
|Android 11+|passthrough|Modern Android has its own hardened allocator (Scudo), so we just pass through. |
|iOS 15+|passthrough|iOS uses Apple libmalloc, which includes platform allocator hardening, so we just pass through. The native artifact is packaged as a static xcframework for [libJGLIOS](https://github.com/NostrGameEngine/libJGLIOS). |

On desktop, bundled JNI libraries are extracted into a fresh private directory for each JVM run. The loader tries the system temporary directory, the user's cache, and then `~/.nge`. It rejects unsafe directory permissions and non-executable filesystems before loading, and schedules extracted files for removal at normal JVM exit. An abrupt exit may leave a private directory behind. Set `saferalloc.native.override` to an existing native file or directory when extraction is managed externally.

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
- `realloc(buffer, newSize)` throws `OutOfMemoryError` if a non-null buffer cannot be resized, including when its replacement Java wrapper cannot be allocated. The old allocation remains valid on failure.
- `realloc(buffer, 0)` returns `null` and frees the old allocation.
- `realloc(null, newSize)` behaves like `malloc(newSize)` for positive sizes; `realloc(null, 0)` returns `null`.
- High-level `realloc` allocates a separate native block and Java wrapper before copying `min(buffer.capacity(), newSize)` bytes and freeing the original. It ignores the original position and limit, and the returned buffer has position zero and limit `newSize`. This guarantees exception safety at the cost of copying and temporarily holding both allocations, even when shrinking or keeping the same size. Native reallocation function pointers retain their existing in-place-capable behavior.
- invalid sizes throw `IllegalArgumentException`.
- `mallocAligned(size, alignment)` requires `alignment` to be a power of two and a multiple of pointer size.
- `calloc(count, size)` rejects capacities larger than `Integer.MAX_VALUE`.


## Note

- This API manages native memory manually. Always call `free(buffer)`.
- Pass only live, owning buffers returned by SaferAlloc to `realloc` or `free`, not slices, duplicates, or buffers allocated elsewhere. These operations do not track ownership.
- After successful `realloc`, treat the old buffer and any views as invalid and use the returned one only. If `realloc` throws, keep using the old buffer (it is still allocated).
- `free(null)` is a no-op.
