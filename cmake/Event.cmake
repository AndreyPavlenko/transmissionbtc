
include(ExternalProject)
include(ProcessorCount)

set(EVENT_INSTALL_DIR "${CMAKE_CURRENT_BINARY_DIR}")
set(EVENT_CMAKE_ARGS -DEVENT__LIBRARY_TYPE=STATIC -DEVENT__DISABLE_BENCHMARK=true
        -DEVENT__DISABLE_TESTS=true -DEVENT__DISABLE_REGRESS=true -DEVENT__DISABLE_SAMPLES=true
        ${EXT_CMAKE_ARGS})

set(EVENT_LIBRARIES "${EVENT_INSTALL_DIR}${CMAKE_INSTALL_PREFIX}/lib/libevent.a")
set(EVENT_INCLUDE_DIR "${EVENT_INSTALL_DIR}${CMAKE_INSTALL_PREFIX}/include")

ProcessorCount(NCPU)
find_program(MAKE_EXE NAMES make gmake nmake)

ExternalProject_Add(libevent
        URL "https://github.com/libevent/libevent/releases/download/release-2.1.11-stable/libevent-2.1.11-stable.tar.gz"
        PREFIX libevent
        BUILD_IN_SOURCE 1
        CONFIGURE_COMMAND ${CMAKE_COMMAND} ${EVENT_CMAKE_ARGS}
        PATCH_COMMAND ${CMAKE_COMMAND} -E touch cmake/Uninstall.cmake.in
        BUILD_COMMAND ${MAKE_EXE} -j${NCPU}
        INSTALL_COMMAND ${MAKE_EXE} install DESTDIR=${EVENT_INSTALL_DIR}
        BUILD_BYPRODUCTS ${EVENT_LIBRARIES})

add_dependencies(libevent openssl)
add_dependencies(${PROJECT_NAME} libevent)
