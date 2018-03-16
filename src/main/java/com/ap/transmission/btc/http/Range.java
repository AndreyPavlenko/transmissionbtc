package com.ap.transmission.btc.http;

/**
 * @author Andrey Pavlenko
 */
public class Range {
  long start;
  long end;

  public Range(long start, long end) {
    this.start = start;
    this.end = end;
  }

  public long getStart() {
    return start;
  }

  public long getEnd() {
    return end;
  }

  public void allign(long length) {
    if (start < 0) {
      start = -start;
      end = length - 1;
    } else if (end < 0) {
      start = 0;
      end = length + end - 1;
    } else if (end >= length) {
      end = length - 1;
    }
  }

  public boolean isSatisfiable(long length) {
    return (start < length) && (end < length) && (start <= end);
  }

  public long getLength() {
    return getEnd() - getStart() + 1;
  }

  @Override
  public String toString() {
    if (start < 0) {
      return -start + "-";
    } else if (end < 0) {
      return -end + "";
    } else {
      return start + "-" + end;
    }
  }
}
