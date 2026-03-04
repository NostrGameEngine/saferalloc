cmake_minimum_required(VERSION 3.20)
project(saferalloc LANGUAGES C)

option(SAFERALLOC_ENABLE_PROBES "Build test-only JNI probe exports" OFF)
option(SAFERALLOC_ANDROID_PASSTHROUGH "Use system allocator on Android" ON)

set(SAFERALLOC_SOURCES
  ${CMAKE_CURRENT_LIST_DIR}/../../../../saferalloc/src/main/c/saferalloc.c
)

# Probes are not built in this module (Android packaging should not include destructive exports).
if(SAFERALLOC_ENABLE_PROBES)
  list(APPEND SAFERALLOC_SOURCES
    ${CMAKE_CURRENT_LIST_DIR}/../../../../saferalloc/src/main/c/saferalloc_probes.c
  )
endif()

add_library(saferalloc SHARED
  ${SAFERALLOC_SOURCES}
)

target_compile_definitions(saferalloc PRIVATE SAFERALLOC_ANDROID_PASSTHROUGH=1)

if(CMAKE_BUILD_TYPE STREQUAL "Debug")
  target_compile_options(saferalloc PRIVATE -O0 -g3)
endif()

# Android NDK toolchain provides jni.h in its sysroot; no host-JNI discovery is needed.
set_target_properties(saferalloc PROPERTIES
  OUTPUT_NAME "saferalloc"
  LIBRARY_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/out"
  RUNTIME_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/out"
  ARCHIVE_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/out"
)
