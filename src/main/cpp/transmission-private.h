#ifndef TRANSMISSION_PRIVATE_H
#define TRANSMISSION_PRIVATE_H
#define __TRANSMISSION__

#ifndef NDEBUG
#define NDEBUG
#include <libtransmission/tr-assert.h>
#undef NDEBUG
#else
#include <libtransmission/tr-assert.h>
#endif

#include <libtransmission/trevent.h>
#include <libtransmission/session.h>
#include <libtransmission/cache.h>
#include <libtransmission/peer-mgr.h>
#include <libtransmission/torrent.h>

#endif