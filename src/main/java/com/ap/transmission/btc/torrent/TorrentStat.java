package com.ap.transmission.btc.torrent;

/**
 * @author Andrey Pavlenko
 */
public class TorrentStat {
  private long[] stat;
  private int offset;
  private String error;

  TorrentStat(long[] stat, int offset, String error) {
    this.stat = stat;
    this.offset = offset;
    this.error = error;
  }

  void update(long[] stat, int offset, String error) {
    this.stat = stat;
    this.offset = offset;
    this.error = error;
  }

  public Status getStatus() {
    return Status.values[(int) stat[offset + 1]];
  }

  public int getProgress() {
    return (int) stat[offset + 2];
  }

  public long getTotalLength() {
    return stat[offset + 3];
  }

  public long getRemainingLength() {
    return stat[offset + 4];
  }

  public long getUploadedLength() {
    return stat[offset + 5];
  }

  public int getPeersUp() {
    return (int) stat[offset + 6];
  }

  public int getPeersDown() {
    return (int) stat[offset + 7];
  }

  public int getSpeedUp() {
    return (int) stat[offset + 8];
  }

  public int getSpeedDown() {
    return (int) stat[offset + 9];
  }

  public String getError() {
    return error;
  }

  public enum Status {
    STOPPED, CHECK, DOWNLOAD, SEED, ERROR;
    private static final Status[] values = values();
  }
}
