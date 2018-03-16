cmake_minimum_required(VERSION 3.4.1)

add_library(transmissionbtc SHARED
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

include_directories(SYSTEM
                    "${depends}/include/"
)

target_link_libraries(transmissionbtc
                      log
                      ${depends}/lib/libtransmission.a
                      ${depends}/lib/libutp.a
                      ${depends}/lib/libdht.a
                      ${depends}/lib/libb64.a
                      ${depends}/lib/libnatpmp.a
                      ${depends}/lib/libminiupnpc.a
                      ${depends}/lib/libcurl.a
                      ${depends}/lib/libevent.a
                      ${depends}/lib/libssl.a
                      ${depends}/lib/libcrypto.a
                      ${depends}/lib/libiconv.a
                      ${depends}/lib/libz.a
)