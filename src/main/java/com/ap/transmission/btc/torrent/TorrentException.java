package com.ap.transmission.btc.torrent;

/**
 * @author Andrey Pavlenko
 */
@SuppressWarnings("unused")
public class TorrentException extends Exception {
  public TorrentException() {}
  public TorrentException(String msg) {super(msg);}
  public TorrentException(Throwable ex) {super(ex);}
  public TorrentException(String msg, Throwable ex) {super(msg, ex);}
}
