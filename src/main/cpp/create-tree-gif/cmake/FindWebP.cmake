# Locates libwebp + libwebpmux and exports WebP::webp / WebP::webpmux.
# Ubuntu's libwebp-dev ships pkg-config files; a raw find_library pass covers
# systems that install headers without them.

find_package(PkgConfig QUIET)
if(PKG_CONFIG_FOUND)
    pkg_check_modules(PC_WEBP QUIET libwebp)
    pkg_check_modules(PC_WEBPMUX QUIET libwebpmux)
endif()

find_path(WebP_INCLUDE_DIR webp/encode.h HINTS ${PC_WEBP_INCLUDE_DIRS})
find_path(WebP_MUX_INCLUDE_DIR webp/mux.h HINTS ${PC_WEBPMUX_INCLUDE_DIRS})
find_library(WebP_LIBRARY webp HINTS ${PC_WEBP_LIBRARY_DIRS})
find_library(WebP_MUX_LIBRARY webpmux HINTS ${PC_WEBPMUX_LIBRARY_DIRS})

include(FindPackageHandleStandardArgs)
find_package_handle_standard_args(WebP
    REQUIRED_VARS WebP_INCLUDE_DIR WebP_MUX_INCLUDE_DIR WebP_LIBRARY WebP_MUX_LIBRARY)

if(WebP_FOUND)
    set(WebP_INCLUDE_DIRS ${WebP_INCLUDE_DIR} ${WebP_MUX_INCLUDE_DIR})

    if(NOT TARGET WebP::webp)
        add_library(WebP::webp UNKNOWN IMPORTED)
        set_target_properties(WebP::webp PROPERTIES
            IMPORTED_LOCATION ${WebP_LIBRARY}
            INTERFACE_INCLUDE_DIRECTORIES ${WebP_INCLUDE_DIR})
    endif()
    if(NOT TARGET WebP::webpmux)
        add_library(WebP::webpmux UNKNOWN IMPORTED)
        set_target_properties(WebP::webpmux PROPERTIES
            IMPORTED_LOCATION ${WebP_MUX_LIBRARY}
            INTERFACE_INCLUDE_DIRECTORIES ${WebP_MUX_INCLUDE_DIR})
        target_link_libraries(WebP::webpmux INTERFACE WebP::webp)
    endif()
endif()

mark_as_advanced(WebP_INCLUDE_DIR WebP_MUX_INCLUDE_DIR WebP_LIBRARY WebP_MUX_LIBRARY)
