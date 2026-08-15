cmake_minimum_required(VERSION 4.1.2)
set(CMAKE_WARN_DEPRECATED OFF)
project(imagecodec)

# ========== 16KB Page Size ==========
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O2 -Wall -Wextra")
set(CMAKE_SHARED_LINKER_FLAGS 
    "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384")

# ========== Patch 应用工具 ==========
function(apply_patches SOURCE_DIR PATCH_DIR)
    if(NOT EXISTS ${PATCH_DIR})
        message(STATUS "No patches directory: ${PATCH_DIR}")
        return()
    endif()

    file(GLOB PATCH_FILES "${PATCH_DIR}/*.patch")
    list(SORT PATCH_FILES)

    foreach(PATCH_FILE ${PATCH_FILES})
        get_filename_component(PATCH_NAME ${PATCH_FILE} NAME)
        
        # 先检查 patch 是否已应用
        execute_process(
            COMMAND git apply --check ${PATCH_FILE}
            WORKING_DIRECTORY ${SOURCE_DIR}
            RESULT_VARIABLE CHECK_RESULT
            OUTPUT_QUIET
            ERROR_QUIET
        )

        if(CHECK_RESULT EQUAL 0)
            message(STATUS "Applying patch: ${PATCH_NAME}")
            execute_process(
                COMMAND git apply ${PATCH_FILE}
                WORKING_DIRECTORY ${SOURCE_DIR}
                RESULT_VARIABLE APPLY_RESULT
            )
            if(NOT APPLY_RESULT EQUAL 0)
                message(FATAL_ERROR "Failed to apply patch: ${PATCH_NAME}")
            endif()
        else()
            message(STATUS "Patch already applied or not applicable, skipping: ${PATCH_NAME}")
        endif()
    endforeach()
endfunction()

# ========== 应用 Patch ==========
apply_patches(
    "${CMAKE_CURRENT_SOURCE_DIR}/src/main/cpp/third_party/libjpeg-turbo"
    "${CMAKE_CURRENT_SOURCE_DIR}/patches/libjpeg-turbo"
)
apply_patches(
    "${CMAKE_CURRENT_SOURCE_DIR}/src/main/cpp/third_party/libpng"
    "${CMAKE_CURRENT_SOURCE_DIR}/patches/libpng"
)
apply_patches(
    "${CMAKE_CURRENT_SOURCE_DIR}/src/main/cpp/third_party/libwebp"
    "${CMAKE_CURRENT_SOURCE_DIR}/patches/libwebp"
)

# ========== libjpeg-turbo ==========
set(ENABLE_SHARED OFF CACHE BOOL "" FORCE)
set(ENABLE_STATIC ON CACHE BOOL "" FORCE)
set(WITH_TURBOJPEG ON CACHE BOOL "" FORCE)
set(WITH_JPEG8 ON CACHE BOOL "" FORCE)
add_subdirectory(src/main/cpp/third_party/libjpeg-turbo)
include_directories(src/main/cpp/third_party/libjpeg-turbo/src)

# ========== libpng ==========
find_package(ZLIB REQUIRED)
set(PNG_SHARED OFF CACHE BOOL "" FORCE)
set(PNG_STATIC ON CACHE BOOL "" FORCE)
set(PNG_TESTS OFF CACHE BOOL "" FORCE)
set(AWK "" CACHE FILEPATH "" FORCE)
add_subdirectory(src/main/cpp/third_party/libpng)
include_directories(src/main/cpp/third_party/libpng)

# ========== libwebp ==========
set(WEBP_BUILD_ANIM_UTILS OFF CACHE BOOL "" FORCE)
set(WEBP_BUILD_CWEBP OFF CACHE BOOL "" FORCE)
set(WEBP_BUILD_DWEBP OFF CACHE BOOL "" FORCE)
set(WEBP_BUILD_GIF2WEBP OFF CACHE BOOL "" FORCE)
set(WEBP_BUILD_IMG2WEBP OFF CACHE BOOL "" FORCE)
set(WEBP_BUILD_VWEBP OFF CACHE BOOL "" FORCE)
set(WEBP_BUILD_WEBPINFO OFF CACHE BOOL "" FORCE)
set(WEBP_BUILD_WEBPMUX OFF CACHE BOOL "" FORCE)
set(WEBP_BUILD_EXTRAS OFF CACHE BOOL "" FORCE)
set(WEBP_BUILD_LIBWEBPMUX OFF CACHE BOOL "" FORCE)
add_subdirectory(src/main/cpp/third_party/libwebp)
include_directories(src/main/cpp/third_party/libwebp/src)

# ========== 编译本模块 ==========
add_library(imagecodec SHARED
    src/main/cpp/image_codec.cpp
    src/main/cpp/jpeg_wrapper.cpp
    src/main/cpp/png_wrapper.cpp
    src/main/cpp/webp_wrapper.cpp
)

find_library(log-lib log)

target_link_libraries(imagecodec
    turbojpeg-static
    png_static
    webp
    webpdecoder
    ${log-lib}
)

# ========== 构建后打反向补丁 ==========
add_custom_command(TARGET imagecodec POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E echo "反向应用补丁..."
    COMMAND cd ${CMAKE_CURRENT_SOURCE_DIR}/src/main/cpp/third_party/libjpeg-turbo && git apply -R ${CMAKE_CURRENT_SOURCE_DIR}/patches/libjpeg-turbo/*.patch 2>/dev/null
    COMMAND cd ${CMAKE_CURRENT_SOURCE_DIR}/src/main/cpp/third_party/libpng && git apply -R ${CMAKE_CURRENT_SOURCE_DIR}/patches/libpng/*.patch 2>/dev/null
  #  COMMAND cd ${CMAKE_CURRENT_SOURCE_DIR}/src/main/cpp/third_party/libwebp && git apply -R ${CMAKE_CURRENT_SOURCE_DIR}/patches/libwebp/*.patch 2>/dev/null
    COMMENT "反向补丁成功！"
)