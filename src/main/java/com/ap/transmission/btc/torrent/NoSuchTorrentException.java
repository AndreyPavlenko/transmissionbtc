package com.ap.transmission.btc.torrent;

/**
 * @author Andrey Pavlenko
 */
@SuppressWarnings("unused")
public class NoSuchTorrentException extends TorrentException {
  public NoSuchTorrentException() {}
  public NoSuchTorrentException(String msg) {super(msg);}
  public NoSuchTorrentException(Throwable ex) {super(ex);}
  public NoSuchTorrentException(String msg, Throwable ex) {super(msg, ex);}
}
