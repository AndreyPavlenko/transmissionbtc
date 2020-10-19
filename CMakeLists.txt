cmake_minimum_required(VERSION 3.4.1)
project(transmissionbtc VERSION 1.0.0 LANGUAGES C)

if (NOT CMAKE_BUILD_TYPE AND NOT CMAKE_CONFIGURATION_TYPES)
    set(CMAKE_BUILD_TYPE "Release" CACHE STRING "Build type: [Debug|Release]" FORCE)
    set_property(CACHE CMAKE_BUILD_TYPE PROPERTY STRINGS "Debug" "Release")
endif ()

set(EXT_C_FLAGS "-DANDROID -fno-unwind-tables -no-canonical-prefixes -D_FORTIFY_SOURCE=1")
set(EXT_EXE_LINKER_FLAGS "-Wl,--exclude-libs,libgcc.a -Wl,--exclude-libs,libgcc_real.a \
                          -Wl,--exclude-libs,libatomic.a -Wl,--gc-sections -static-libstdc++")

if (CMAKE_BUILD_TYPE STREQUAL "Debug")
    set(EXT_C_FLAGS "-g -O0 ${EXT_C_FLAGS}")
    set(EXT_EXE_LINKER_FLAGS "-O0 ${EXT_EXE_LINKER_FLAGS}")
    set(CMAKE_C_FLAGS_DEBUG ${EXT_C_FLAGS})
    set(CMAKE_EXE_LINKER_FLAGS_DEBUG ${EXT_EXE_LINKER_FLAGS})
else ()
    set(EXT_C_FLAGS "-O3 -DNDEBUG -flto -fvisibility=hidden -fdata-sections -ffunction-sections ${EXT_C_FLAGS}")
    set(EXT_EXE_LINKER_FLAGS "-flto -O3 -Wl,--strip-all ${EXT_EXE_LINKER_FLAGS}")
    set(CMAKE_C_FLAGS_RELEASE ${EXT_C_FLAGS})
    set(CMAKE_EXE_LINKER_FLAGS_RELEASE ${EXT_EXE_LINKER_FLAGS})
endif ()

set(EXT_CMAKE_ARGS -DCMAKE_TOOLCHAIN_FILE=${CMAKE_TOOLCHAIN_FILE}
        -DCMAKE_BUILD_TYPE=${CMAKE_BUILD_TYPE}
        -DANDROID_ABI=${ANDROID_ABI}
        -DANDROID_PLATFORM=${ANDROID_NATIVE_API_LEVEL}
        -DCMAKE_FIND_ROOT_PATH=${CMAKE_CURRENT_BINARY_DIR}
        -DCMAKE_FIND_ROOT_PATH_MODE_PROGRAM=true
        -DCMAKE_FIND_ROOT_PATH_MODE_LIBRARY=true
        -DCMAKE_FIND_ROOT_PATH_MODE_INCLUDE=true
        -DCMAKE_FIND_ROOT_PATH_MODE_PACKAGE=true
        -DCMAKE_PREFIX_PATH=${CMAKE_PREFIX_PATH}
        -DCMAKE_DEBUG_POSTFIX=''
        -DCMAKE_C_FLAGS=${EXT_C_FLAGS}
        -DCMAKE_EXE_LINKER_FLAGS=${EXT_EXE_LINKER_FLAGS}
        )

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

list(APPEND CMAKE_MODULE_PATH ${CMAKE_CURRENT_SOURCE_DIR}/cmake)
include(OpenSSL)
include(cURL)
include(Event)
include(Transmission)

include_directories(SYSTEM
        ${OPENSSL_INCLUDE_DIR}
        ${CURL_INCLUDE_DIR}
        ${EVENT_INCLUDE_DIR}
        ${TR_INCLUDE_DIR})

target_link_libraries(${PROJECT_NAME}
        ${TR_LIBRARIES}
        ${CURL_LIBRARIES}
        ${EVENT_LIBRARIES}
        ${OPENSSL_LIBRARIES}
        log
        z)
