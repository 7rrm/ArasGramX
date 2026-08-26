# MeeroX v217 (12.10.1 merge hotfix): the vendored libyuv subtree ships pruned
# content (includes + sources, no upstream CMakeLists.txt). v213-v216 CI
# compiled only because the native-build cache still carried one; the 12.10.1
# native prebuild restructure invalidated the cache and exposed the gap
# (configure failed at top-level CMakeLists.txt:17 add_subdirectory).
# Self-contained replacement providing the `yuv` static target the top-level
# links, built from every source/*.cc like upstream does.
cmake_minimum_required(VERSION 3.16)
project(libyuv C CXX)

file(GLOB LY_SOURCES CONFIGURE_DEPENDS "source/*.cc")
# Platform slices that do not belong to any Android ABI are dropped; every
# remaining file guards itself with #if defined(ARCH) and compiles to an
# empty object off-arch, exactly like upstream's build-all approach.
list(FILTER LY_SOURCES EXCLUDE REGEX ".*_(mmi|msa|win)\.cc$")
add_library(yuv STATIC ${LY_SOURCES})
target_include_directories(yuv PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/include)
target_compile_options(yuv PRIVATE -fno-strict-aliasing)
set_target_properties(yuv PROPERTIES POSITION_INDEPENDENT_CODE ON)
