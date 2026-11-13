// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.server.cache.mem;

import static java.util.Objects.requireNonNull;

import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.RemovalListener;
import com.google.common.cache.Cache;
import com.google.common.cache.ForwardingCache;
import com.google.common.cache.ForwardingLoadingCache;
import com.google.common.cache.LoadingCache;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/** Reports explicit invalidations even when the cache has no entry for the key. */
class InvalidationTrackingCache<K, V> extends ForwardingCache.SimpleForwardingCache<K, V> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Class<?> keyType;
  private final RemovalListener<K, V> removalListener;
  private final Executor executor;

  InvalidationTrackingCache(
      Cache<K, V> delegate,
      Class<?> keyType,
      RemovalListener<K, V> removalListener,
      @Nullable Executor executor) {
    super(delegate);
    this.keyType = keyType;
    this.removalListener = removalListener;
    this.executor = executor != null ? executor : ForkJoinPool.commonPool();
  }

  @Override
  @SuppressWarnings("unchecked")
  public void invalidate(Object key) {
    requireNonNull(key);
    // Removing atomically avoids racing a presence check against another cache operation.
    // The delegate already notifies the listener when it removes a live entry.
    // Cache values cannot be null, so a null result means no live entry was removed.
    if (delegate().asMap().remove(key) == null && keyType.isInstance(key)) {
      // Match Caffeine's bounded cache handling of listener and executor failures.
      Runnable notification =
          () -> {
            try {
              removalListener.onRemoval((K) key, null, RemovalCause.EXPLICIT);
            } catch (Throwable t) {
              logger.atWarning().withCause(t).log("Exception thrown by removal listener");
            }
          };
      try {
        executor.execute(notification);
      } catch (Throwable t) {
        logger.atSevere().withCause(t).log("Exception thrown when submitting removal listener");
        notification.run();
      }
    }
  }

  @Override
  public void invalidateAll(Iterable<?> keys) {
    keys.forEach(this::invalidate);
  }

  static class Loading<K, V> extends ForwardingLoadingCache.SimpleForwardingLoadingCache<K, V> {
    private final InvalidationTrackingCache<K, V> invalidationTrackingCache;

    Loading(
        LoadingCache<K, V> delegate,
        Class<?> keyType,
        RemovalListener<K, V> removalListener,
        @Nullable Executor executor) {
      super(delegate);
      invalidationTrackingCache =
          new InvalidationTrackingCache<>(delegate, keyType, removalListener, executor);
    }

    @Override
    public void invalidate(Object key) {
      invalidationTrackingCache.invalidate(key);
    }

    @Override
    public void invalidateAll(Iterable<?> keys) {
      invalidationTrackingCache.invalidateAll(keys);
    }
  }
}
