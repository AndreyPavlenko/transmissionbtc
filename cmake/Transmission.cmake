
include(ExternalProject)
include(ProcessorCount)

option(TR_SRC_DIR "Transmission sources" "${CMAKE_CURRENT_BINARY_DIR}/transmission/src")
set(TR_BUILD_DIR "${CMAKE_CURRENT_BINARY_DIR}/transmission/src/transmission-build")
set(TR_CMAKE_ARGS -DENABLE_TESTS=OFF -DENABLE_DAEMON=OFF -DINSTALL_DOC=OFF -DENABLE_UTILS=OFF
        -DENABLE_CLI=OFF -DENABLE_GTK=OFF -DENABLE_QT=OFF -DENABLE_MAC=OFF -DINSTALL_DOC=OFF
        -DENABLE_WEB=ON ${EXT_CMAKE_ARGS})

set(TR_LIBRARIES
        "${TR_BUILD_DIR}/libtransmission/libtransmission.a"
        "${TR_BUILD_DIR}/third-party/dht/lib/libdht.a"
        "${TR_BUILD_DIR}/third-party/arc4/src/libarc4.a"
        "${TR_BUILD_DIR}/third-party/b64/lib/libb64.a"
        "${TR_BUILD_DIR}/third-party/natpmp/lib/libnatpmp.a"
        "${TR_BUILD_DIR}/third-party/miniupnpc/lib/libminiupnpc.a"
        "${TR_BUILD_DIR}/third-party/utp/lib/libutp.a")

set(TR_INCLUDE_DIR "${TR_SRC_DIR}" "${TR_BUILD_DIR}")

ProcessorCount(NCPU)
find_program(MAKE_EXE NAMES make gmake nmake)

ExternalProject_Add(transmission
        GIT_REPOSITORY "https://github.com/AndreyPavlenko/transmission.git"
        GIT_TAG "transmissionbtc"
        SOURCE_DIR ${TR_SRC_DIR}
        PREFIX transmission
        GIT_SHALLOW 1
        GIT_PROGRESS 1
        GIT_SUBMODULES_RECURSE 1
        CONFIGURE_COMMAND ${CMAKE_COMMAND} ${TR_SRC_DIR} ${TR_CMAKE_ARGS}
        BUILD_COMMAND ${MAKE_EXE} -j${NCPU}
        INSTALL_COMMAND ${MAKE_EXE} install "DESTDIR=${TR_WEB_INSTALL_DIR}"
        BUILD_BYPRODUCTS ${TR_LIBRARIES})

add_dependencies(transmission openssl curl libevent)
add_dependencies(${PROJECT_NAME} transmission)
