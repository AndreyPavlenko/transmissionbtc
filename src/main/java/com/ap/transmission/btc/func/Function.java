package com.ap.transmission.btc.func;

/**
 * @author Andrey Pavlenko
 */
public interface Function<T, R> {
  R apply(T t);
}
