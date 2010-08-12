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

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/** Utilities to work with futures. */
public class FutureUtil {
  private static final Logger log = LoggerFactory.getLogger(FutureUtil.class);

  /**
   * Construct a future that concatenates the resulting lists.
   *
   * @param <V> type of the list element.
   * @param all the futures whose results will be concatenated.
   * @return a future to get all results.
   */
  public static <V> ListenableFuture<List<V>> concat(
      List<ListenableFuture<List<V>>> all) {
    return new ConcatFuture<V>(all);
  }

  /**
   * Construct a future that concatenates the resulting lists.
   *
   * @param <V> type of the list element.
   * @param all the futures whose results will be concatenated.
   * @return a future to get all results.
   */
  public static <V> ListenableFuture<List<V>> concatSingletons(
      List<ListenableFuture<V>> all) {
    Function<V, List<V>> asList = new Function<V, List<V>>() {
      public List<V> apply(V item) {
        if (item != null) {
          return Collections.singletonList(item);
        } else {
          return Collections.<V> emptyList();
        }
      }
    };

    List<ListenableFuture<List<V>>> r = Lists.newArrayList();
    for (ListenableFuture<V> f : all) {
      r.add(Futures.compose(f, asList));
    }
    return new ConcatFuture<V>(r);
  }

  /**
   * Get the future's value and return it to the caller.
   *
   * If the method is interrupted during computation, or the future throws an
   * exception this is wrapped into an {@link FutureException} and rethrown.
   *
   * @param <V> the return type of the future.
   * @param future the future itself
   * @return the value of the future.
   * @throws FutureException if the future's get method threw an exception.
   */
  public static <V> V get(Future<V> future) {
    try {
      return future.get();
    } catch (InterruptedException e) {
      throw new FutureException(e);
    } catch (ExecutionException e) {
      throw new FutureException(e);
    }
  }

  /**
   * Get the future's value and return it to the caller.
   *
   * If the method is interrupted during computation, or the future throws an
   * exception this is wrapped into an {@link FutureException} and rethrown.
   *
   * @param <V> the return type of the future.
   * @param future the future itself
   * @return the value of the future; null if the future returned null or it
   *         threw an exception during completion.
   */
  public static <V> V getOrNull(Future<V> future) {
    try {
      return future.get();

    } catch (InterruptedException e) {
      log.warn("Interrupted while waiting on future, using empty result", e);
      return null;

    } catch (ExecutionException e) {
      log.warn("Interrupted while waiting on future, using empty result", e);
      return null;
    }
  }

  /**
   * Get the future's value and return it to the caller.
   *
   * If the method is interrupted during computation, or the future throws an
   * exception, the event is logged and an empty collection is returned.
   *
   * @param <V> the return type of the future.
   * @param future the future itself
   * @return the value of the future.
   */
  @SuppressWarnings("unchecked")
  public static <V> List<V> getOrEmptyList(Future<List<V>> future) {
    try {
      return future.get();

    } catch (InterruptedException e) {
      log.warn("Interrupted while waiting on future, using empty result", e);
      return Lists.newArrayListWithCapacity(0);

    } catch (ExecutionException e) {
      log.warn("Error in future collection, using empty result", e);
      return Lists.newArrayListWithCapacity(0);
    }
  }

  /**
   * Get the future's value and return it to the caller.
   *
   * If the method is interrupted during computation, or the future throws an
   * exception, the event is logged and an empty collection is returned.
   *
   * @param <V> the return type of the future.
   * @param future the future itself
   * @return the value of the future.
   */
  @SuppressWarnings("unchecked")
  public static <V> Set<V> getOrEmptySet(Future<Set<V>> future) {
    try {
      return future.get();

    } catch (InterruptedException e) {
      log.warn("Interrupted while waiting on future, using empty result", e);
      return Sets.newHashSetWithExpectedSize(0);

    } catch (ExecutionException e) {
      log.warn("Error in future collection, using empty result", e);
      return Sets.newHashSetWithExpectedSize(0);
    }
  }

  /**
   * Flatten a map of futures down to actual values.
   *
   * @param <K> type of the map entry key.
   * @param <V> type of the map entry value.
   * @param want the map of futures to resolve.
   * @return the resulting map.
   */
  public static <K, V> Map<K, V> getMap(Map<K, Future<V>> want) {
    Map<K, V> res = Maps.newHashMapWithExpectedSize(want.size());
    for (Map.Entry<K, Future<V>> ent : want.entrySet()) {
      res.put(ent.getKey(), get(ent.getValue()));
    }
    return res;
  }

  /**
   * Wait for a future to complete, and discard its results.
   *
   * @param future the future to wait for completion of.
   */
  public static void waitFor(Future<Void> future) {
    get(future);
  }

  /**
   * Wait for multiple futures to complete, and discard all results.
   *
   * @param futures the futures to wait for completion of.
   */
  public static void waitFor(Future<Void>... futures) {
    for (Future<Void> f : futures) {
      waitFor(f);
    }
  }

  /**
   * Wait for multiple futures to complete, and discard all results.
   *
   * @param futures the futures to wait for completion of.
   */
  public static void waitFor(Iterable<? extends Future<Void>> futures) {
    for (Future<Void> f : futures) {
      waitFor(f);
    }
  }

  private FutureUtil() {
  }
}
