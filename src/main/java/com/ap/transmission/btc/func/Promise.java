package com.ap.transmission.btc.func;

/**
 * @author Andrey Pavlenko
 */
public interface Promise<T> {
  T get() throws Throwable;

  void cancel();

  @SuppressWarnings("unused")
  class Completed<T> implements Promise {
    private final T result;

    public Completed(T result) {this.result = result;}

    @Override
    public T get() {
      return result;
    }

    @Override
    public void cancel() { }
  }
}
