
include(ExternalProject)
include(ProcessorCount)

set(CURL_INSTALL_DIR "${CMAKE_CURRENT_BINARY_DIR}")
set(CURL_CMAKE_ARGS -DBUILD_TESTING=false
        -DBUILD_SHARED_LIBS=false -DCURL_DISABLE_DICT=true -DCURL_DISABLE_DICT=true
        -DCURL_DISABLE_GOPHER=true -DCURL_DISABLE_IMAP=true -DCURL_DISABLE_POP3=true
        -DCURL_DISABLE_RTSP=true -DCURL_DISABLE_SMTP=true -DCURL_DISABLE_TELNET=true -DCURL_DISABLE_TFTP=true
        ${EXT_CMAKE_ARGS})

set(CURL_LIBRARIES "${CURL_INSTALL_DIR}${CMAKE_INSTALL_PREFIX}/lib/libcurl.a")
set(CURL_INCLUDE_DIR "${CURL_INSTALL_DIR}${CMAKE_INSTALL_PREFIX}/include")

ProcessorCount(NCPU)
find_program(MAKE_EXE NAMES make gmake nmake)

ExternalProject_Add(curl
        URL "https://curl.se/download/curl-7.76.1.tar.xz"
        PREFIX curl
        BUILD_IN_SOURCE 1
        CONFIGURE_COMMAND ${CMAKE_COMMAND} ${CURL_CMAKE_ARGS}
        BUILD_COMMAND ${MAKE_EXE} -j${NCPU}
        INSTALL_COMMAND ${MAKE_EXE} install DESTDIR=${CURL_INSTALL_DIR}
        BUILD_BYPRODUCTS ${CURL_LIBRARIES})

add_dependencies(curl openssl)
add_dependencies(${PROJECT_NAME} curl)
