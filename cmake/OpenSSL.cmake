
include(ExternalProject)
include(ProcessorCount)

if (ANDROID_ABI MATCHES "armeabi")
    set(OPENSSL_ANDROID_ABI "android-arm")
elseif (ANDROID_ABI STREQUAL "arm64-v8a")
    set(OPENSSL_ANDROID_ABI "android-arm64")
elseif (ANDROID_ABI STREQUAL "x86_64")
    set(OPENSSL_ANDROID_ABI "android-x86_64")
elseif (ANDROID_ABI STREQUAL "x86")
    set(OPENSSL_ANDROID_ABI "android-x86")
else ()
    message(FATAL_ERROR "Unsupported ANDROID_ABI: ${ANDROID_ABI}")
endif ()

ProcessorCount(NCPU)
find_program(MAKE_EXE NAMES make gmake nmake)

set(OPENSSL_PATH "${ANDROID_TOOLCHAIN_ROOT}/bin:$ENV{PATH}")
set(OPENSSL_INSTALL_DIR "${CMAKE_CURRENT_BINARY_DIR}")
set(OPENSSL_ENV "export ANDROID_NDK_HOME='${ANDROID_NDK}' && \
                 export PATH='${ANDROID_TOOLCHAIN_ROOT}/bin:$ENV{PATH}'")
set(OPENSSL_CONFIGURE_CMD eval ${OPENSSL_ENV} && ./Configure)
set(OPENSSL_BUILD_CMD eval ${OPENSSL_ENV} && ${MAKE_EXE} -j${NCPU})

set(OPENSSL_CONFIGURE_OPTS --prefix=${CMAKE_INSTALL_PREFIX} no-shared no-idea no-camellia
        no-seed no-bf no-cast no-rc2 no-md2 no-md4 no-mdc2 no-dsa no-err no-engine
        no-tests no-unit-test no-external-tests no-dso no-dynamic-engine no-stdio zlib
        ${OPENSSL_ANDROID_ABI} -D__ANDROID_API__=${ANDROID_NATIVE_API_LEVEL})

set(OPENSSL_ROOT_DIR "${OPENSSL_INSTALL_DIR}${CMAKE_INSTALL_PREFIX}")
set(OPENSSL_LIB_DIR "${OPENSSL_ROOT_DIR}/lib")
set(OPENSSL_LIBRARIES "${OPENSSL_LIB_DIR}/libssl.a" "${OPENSSL_LIB_DIR}/libcrypto.a")
set(OPENSSL_INCLUDE_DIR "${OPENSSL_ROOT_DIR}/include")

message(STATUS "OpenSSL config: ${OPENSSL_CONFIGURE_OPTS}")

ExternalProject_Add(openssl
        URL "https://www.openssl.org/source/openssl-1.1.1j.tar.gz"
        PREFIX openssl
        BUILD_IN_SOURCE 1
        PATCH_COMMAND patch -p1 -i ${CMAKE_SOURCE_DIR}/patches/openssl_ndk22.patch
        CONFIGURE_COMMAND ${OPENSSL_CONFIGURE_CMD} ${OPENSSL_CONFIGURE_OPTS}
        BUILD_COMMAND ${OPENSSL_BUILD_CMD}
        INSTALL_COMMAND ${MAKE_EXE} install_sw DESTDIR=${OPENSSL_INSTALL_DIR}
        BUILD_BYPRODUCTS ${OPENSSL_LIBRARIES})

add_dependencies(${PROJECT_NAME} openssl)
