cmake_minimum_required(VERSION 3.4.1)
project(transmissionbtc VERSION 1.0.0 LANGUAGES C)

if (NOT CMAKE_BUILD_TYPE AND NOT CMAKE_CONFIGURATION_TYPES)
    set(CMAKE_BUILD_TYPE "RELEASE" CACHE STRING "Build type: [DEBUG|RELEASE|GPROF|PFG|PFU]" FORCE)
    set_property(CACHE CMAKE_BUILD_TYPE PROPERTY STRINGS "DEBUG" "RELEASE" "GPROF" "PFG" "PFU")
endif ()


string(TOUPPER ${CMAKE_BUILD_TYPE} BUILD_TYPE)

add_library(${PROJECT_NAME} SHARED
        "${CMAKE_SOURCE_DIR}/src/main/cpp/native_to_java.c"
        "${CMAKE_SOURCE_DIR}/src/main/cpp/env.c"
        "${CMAKE_SOURCE_DIR}/src/main/cpp/sem.c"
        "${CMAKE_SOURCE_DIR}/src/main/cpp/curl.c"
        "${CMAKE_SOURCE_DIR}/src/main/cpp/hash.c"
        "${CMAKE_SOURCE_DIR}/src/main/cpp/commons.c"
        "${CMAKE_SOURCE_DIR}/src/main/cpp/torrent.c"
        "${CMAKE_SOURCE_DIR}/src/main/cpp/transmission.c"
        "${CMAKE_SOURCE_DIR}/src/main/cpp/stdredirect.c"
        )

set(depends "${DEPENDS_ROOT}/${ANDROID_ARCH_NAME}-api${ANDROID_PLATFORM_LEVEL}")

include_directories(SYSTEM "${depends}/usr/include/")

if (BUILD_TYPE STREQUAL "DEBUG")
    set(CMAKE_C_FLAGS_DEBUG "-g -O0 -flto -Wno-unused-command-line-argument")
    set(CMAKE_EXE_LINKER_FLAGS_DEBUG "-flto -O0")
else ()
    set(CMAKE_C_FLAGS_RELEASE "-O3 -DNDEBUG -flto -fvisibility=hidden -fdata-sections -ffunction-sections -Wno-unused-command-line-argument")
    set(CMAKE_EXE_LINKER_FLAGS_RELEASE "-flto -O3 -Wl,--gc-sections -Wl,--strip-all")
endif ()

set(LINK_LIBS
        log
        ${depends}/usr/lib/libtransmission.a
        ${depends}/usr/lib/libdht.a
        ${depends}/usr/lib/libb64.a
        ${depends}/usr/lib/libnatpmp.a
        ${depends}/usr/lib/libminiupnpc.a
        ${depends}/usr/lib/libcurl.a
        ${depends}/usr/lib/libevent.a
        ${depends}/usr/lib/libssl.a
        ${depends}/usr/lib/libcrypto.a
        ${depends}/usr/lib/libz.a)

if (ANDROID_ABI STREQUAL "arm64-v8a" OR ANDROID_ABI STREQUAL "x86_64")
    set(LINK_LIBS ${LINK_LIBS} ${depends}/usr/lib/libutp.a)
endif ()

target_link_libraries(${PROJECT_NAME} ${LINK_LIBS})