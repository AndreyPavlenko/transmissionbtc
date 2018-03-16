package com.ap.transmission.btc.torrent;

/**
 * @author Andrey Pavlenko
 */
@SuppressWarnings("unused")
public class DuplicateTorrentException extends TorrentException {
  public DuplicateTorrentException() {}
  public DuplicateTorrentException(String msg) {super(msg);}
  public DuplicateTorrentException(Throwable ex) {super(ex);}
  public DuplicateTorrentException(String msg, Throwable ex) {super(msg, ex);}
}
