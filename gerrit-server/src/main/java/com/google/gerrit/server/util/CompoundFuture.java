// Copyright (C) 2010 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.server.util;

import com.google.common.util.concurrent.ForwardingListenableFuture;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** A future that runs all of the futures given to it. */
public class CompoundFuture<V> extends ForwardingListenableFuture<V> {
  /**
   * Construct a new compound future around several futures.
   *
   * @param <V> the type of the result this future produces.
   * @param result the future that will provide the final result. This future
   *        will be waited on last.
   * @param other1 any other future that should also complete before this future
   *        completes. Their results will be discarded, and thus should be
   *        declared to return Void.
   * @return a future to wait on several futures.
   */
  @SuppressWarnings("unchecked")
  public static <V> CompoundFuture<V> wrap(ListenableFuture<V> result,
      Future<Void> other1) {
    return new CompoundFuture<V>(result, other1);
  }

  /**
   * Construct a new compound future around several futures.
   *
   * @param <V> the type of the result this future produces.
   * @param result the future that will provide the final result. This future
   *        will be waited on last.
   * @param other1 any other future that should also complete before this future
   *        completes. Their results will be discarded, and thus should be
   *        declared to return Void.
   * @param other2 any other future that should also complete before this future
   *        completes. Their results will be discarded, and thus should be
   *        declared to return Void.
   * @return a future to wait on several futures.
   */
  @SuppressWarnings("unchecked")
  public static <V> CompoundFuture<V> wrap(ListenableFuture<V> result,
      Future<Void> other1, Future<Void> other2) {
    return new CompoundFuture<V>(result, other1, other2);
  }

  /**
   * Construct a new compound future around several futures.
   *
   * @param <V> the type of the result this future produces.
   * @param result the future that will provide the final result. This future
   *        will be waited on last.
   * @param other1 any other future that should also complete before this future
   *        completes. Their results will be discarded, and thus should be
   *        declared to return Void.
   * @param other2 any other future that should also complete before this future
   *        completes. Their results will be discarded, and thus should be
   *        declared to return Void.
   * @param other3 any other future that should also complete before this future
   *        completes. Their results will be discarded, and thus should be
   *        declared to return Void.
   * @return a future to wait on several futures.
   */
  @SuppressWarnings("unchecked")
  public static <V> CompoundFuture<V> wrap(ListenableFuture<V> result,
      Future<Void> other1, Future<Void> other2, Future<Void> other3) {
    return new CompoundFuture<V>(result, other1, other2, other3);
  }

  /**
   * Construct a new compound future around several futures.
   *
   * @param <V> the type of the result this future produces.
   * @param result the future that will provide the final result. This future
   *        will be waited on last.
   * @param others any other futures that should also complete before this
   *        future completes. Their results will be discarded, and thus should
   *        be declared to return Void.
   * @return a future to wait on several futures.
   */
  public static <V> CompoundFuture<V> wrap(ListenableFuture<V> result,
      Future<Void>... others) {
    Future<Void>[] r = CompoundFuture.<Void> newArray(others.length);
    System.arraycopy(others, 0, r, 0, others.length);
    return new CompoundFuture<V>(result, r);
  }

  /**
   * Construct a new compound future around several futures.
   *
   * @param <V> the type of the result this future produces.
   * @param result the future that will provide the final result. This future
   *        will be waited on last.
   * @param others any other futures that should also complete before this
   *        future completes. Their results will be discarded, and thus should
   *        be declared to return Void.
   * @return a future to wait on several futures.
   */
  public static <V> CompoundFuture<V> wrap(ListenableFuture<V> result,
      Collection<Future<Void>> others) {
    Future<Void>[] r = CompoundFuture.<Void> newArray(others.size());
    return new CompoundFuture<V>(result, others.toArray(r));
  }

  @SuppressWarnings("unchecked")
  private static <T> Future<T>[] newArray(int sz) {
    return new Future[sz];
  }

  private final ListenableFuture<V> result;
  private final Future<Void>[] others;

  private CompoundFuture(ListenableFuture<V> result, Future<Void>... others) {
    this.result = result;
    this.others = others;
  }

  @Override
  protected ListenableFuture<V> delegate() {
    return result;
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    boolean res = super.cancel(mayInterruptIfRunning);
    for (Future<Void> f : others) {
      f.cancel(mayInterruptIfRunning);
    }
    return res;
  }

  @Override
  public V get() throws InterruptedException, ExecutionException {
    for (Future<Void> f : others) {
      if (!f.isDone()) {
        f.get();
      }
    }
    return super.get();
  }

  @Override
  public V get(long timeout, TimeUnit unit) throws InterruptedException,
      ExecutionException, TimeoutException {
    if (unit != TimeUnit.MILLISECONDS) {
      timeout = TimeUnit.MILLISECONDS.convert(timeout, unit);
      unit = TimeUnit.MILLISECONDS;
    }

    for (Future<Void> f : others) {
      if (!f.isDone()) {
        long start = System.currentTimeMillis();
        f.get(timeout, unit);

        timeout -= Math.max(0, System.currentTimeMillis() - start);
        if (timeout <= 0) {
          throw new TimeoutException();
        }
      }
    }

    return super.get(timeout, unit);
  }

  @Override
  public boolean isDone() {
    for (Future<Void> f : others) {
      if (!f.isDone()) {
        return false;
      }
    }
    return super.isDone();
  }
}
