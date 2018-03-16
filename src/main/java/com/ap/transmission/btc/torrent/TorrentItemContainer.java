package com.ap.transmission.btc.torrent;

import java.util.List;

/**
 * @author Andrey Pavlenko
 */
public interface TorrentItemContainer extends TorrentItem {
  List<TorrentItem> ls();
}
