package com.ap.transmission.btc;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

/**
 * @author Andrey Pavlenko
 */
public class Baos extends ByteArrayOutputStream {

  public Baos(int size) { super(size); }

  public byte[] buf() {
    return buf;
  }

  public ByteBuffer byteBuf() {
    return ByteBuffer.wrap(buf(), 0, size());
  }
}
