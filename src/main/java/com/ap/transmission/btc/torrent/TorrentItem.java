package com.ap.transmission.btc.torrent;

/**
 * @author Andrey Pavlenko
 */
public interface TorrentItem {

  String getId();

  String getName();

  boolean isComplete();

  boolean isDnd();

  TorrentItemContainer getParent();

  TorrentFs getFs();
}
