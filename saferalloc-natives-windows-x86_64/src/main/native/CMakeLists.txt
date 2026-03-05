cmake_minimum_required(VERSION 3.20)
project(saferalloc LANGUAGES C)

include(FetchContent)

option(SAFERALLOC_ENABLE_PROBES "Build test-only JNI probe exports" OFF)

# Configure mimalloc *before* adding it.
set(MI_SECURE ON CACHE BOOL "" FORCE)
set(MI_OVERRIDE OFF CACHE BOOL "" FORCE)
set(MI_BUILD_SHARED OFF CACHE BOOL "" FORCE)
set(MI_BUILD_STATIC ON CACHE BOOL "" FORCE)

FetchContent_Declare(
  mimalloc
  GIT_REPOSITORY https://github.com/microsoft/mimalloc.git
  GIT_TAG v2.2.7
)
FetchContent_MakeAvailable(mimalloc)

set(SAFERALLOC_SOURCES
  ${CMAKE_CURRENT_LIST_DIR}/../../../../saferalloc/src/main/c/saferalloc.c
)

if(SAFERALLOC_ENABLE_PROBES)
  list(APPEND SAFERALLOC_SOURCES
    ${CMAKE_CURRENT_LIST_DIR}/../../../../saferalloc/src/main/c/saferalloc_probes.c
  )
endif()

add_library(saferalloc SHARED
  ${SAFERALLOC_SOURCES}
)
set_target_properties(saferalloc PROPERTIES
  C_STANDARD 11
  C_STANDARD_REQUIRED ON
  C_EXTENSIONS OFF
)
if(MSVC)
  target_compile_options(saferalloc PRIVATE /experimental:c11atomics)
endif()

if(CMAKE_BUILD_TYPE STREQUAL "Debug")
  target_compile_options(saferalloc PRIVATE -O0 -g3)
endif()

target_include_directories(saferalloc PRIVATE
  ${mimalloc_SOURCE_DIR}/include
)

target_link_libraries(saferalloc PRIVATE mimalloc-static)

find_package(JNI REQUIRED)
target_include_directories(saferalloc PRIVATE ${JNI_INCLUDE_DIRS})

set_target_properties(saferalloc PROPERTIES
  OUTPUT_NAME "saferalloc"
  LIBRARY_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/out"
  RUNTIME_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/out"
  ARCHIVE_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/out"
)
