package com.ap.transmission.btc.func;

/**
 * @author Andrey Pavlenko
 */
public interface Consumer<T> {
  void accept(T t);
}
