package com.ap.transmission.btc;

import android.support.annotation.NonNull;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @author Andrey Pavlenko
 */
public class CompletedFuture<T> implements Future<T> {
  public static final CompletedFuture<Void> VOID = new CompletedFuture<>(null);
  private final T result;

  public CompletedFuture(T result) {this.result = result;}

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    return false;
  }

  @Override
  public boolean isCancelled() {
    return false;
  }

  @Override
  public boolean isDone() {
    return true;
  }

  @Override
  public T get() {
    return result;
  }

  @Override
  public T get(long timeout, @NonNull TimeUnit unit) {
    return result;
  }
}
