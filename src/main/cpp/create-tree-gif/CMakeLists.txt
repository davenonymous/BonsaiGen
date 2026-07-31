cmake_minimum_required(VERSION 3.22)
project(create-tree-gif LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 11)

option(BUILD_EGL_FALLBACK "Direct EGL surfaceless fallback when SDL cannot create a GL context" ON)

if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Release)
endif()

# SDL2: Ubuntu ships CMake config files; pkg-config as fallback.
find_package(SDL2 CONFIG QUIET)
if(NOT SDL2_FOUND)
    find_package(PkgConfig REQUIRED)
    pkg_check_modules(SDL2PC REQUIRED IMPORTED_TARGET sdl2)
    add_library(SDL2::SDL2 ALIAS PkgConfig::SDL2PC)
endif()

# giflib: CMake's built-in FindGIF module (giflib has no pkg-config file).
find_package(GIF REQUIRED)

list(APPEND CMAKE_MODULE_PATH ${CMAKE_CURRENT_SOURCE_DIR}/cmake)
find_package(WebP REQUIRED)

include(CheckSymbolExists)
set(CMAKE_REQUIRED_INCLUDES ${WebP_INCLUDE_DIRS})
set(CMAKE_REQUIRED_LIBRARIES ${WebP_MUX_LIBRARY} ${WebP_LIBRARY})
check_symbol_exists(WebPAnimEncoderNewInternal "webp/mux.h" HAVE_WEBP_ANIM_ENCODER)
unset(CMAKE_REQUIRED_LIBRARIES)
unset(CMAKE_REQUIRED_INCLUDES)
if(NOT HAVE_WEBP_ANIM_ENCODER)
    message(FATAL_ERROR "libwebpmux is too old: WebPAnimEncoder API not found in webp/mux.h")
endif()

add_executable(create-tree-gif
    src/main.cpp
    src/options.cpp
    src/log.cpp
    src/assets.cpp
    src/blockmodel.cpp
    src/texture.cpp
    src/scene.cpp
    src/render.cpp
    src/encode_webp.cpp
    src/encode_gif.cpp
    src/quantize.cpp
    src/vendor_impl.c
)

target_include_directories(create-tree-gif PRIVATE src third_party)
target_link_libraries(create-tree-gif PRIVATE SDL2::SDL2 GIF::GIF WebP::webpmux WebP::webp)
target_compile_options(create-tree-gif PRIVATE
    $<$<COMPILE_LANGUAGE:CXX>:-Wall -Wextra -Wno-unused-parameter>
)
# Vendored C code (miniz) is not ours to fix warnings in.
set_source_files_properties(src/vendor_impl.c PROPERTIES COMPILE_OPTIONS "-w")

if(BUILD_EGL_FALLBACK)
    find_package(OpenGL COMPONENTS EGL)
    if(OpenGL_EGL_FOUND)
        target_link_libraries(create-tree-gif PRIVATE OpenGL::EGL)
        target_compile_definitions(create-tree-gif PRIVATE CTG_HAVE_EGL=1)
    else()
        message(STATUS "EGL not found - building without the surfaceless fallback")
    endif()
endif()
