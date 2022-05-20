// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.server.cache;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.gerrit.common.Nullable;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Caches object instances for a request as {@link ThreadLocal} in the serving thread.
 *
 * <p>This class is intended to cache objects that have a high instantiation cost, are specific to
 * the current request and potentially need to be instantiated multiple times while serving a
 * request.
 *
 * <p>This is different from the key-value storage in {@code CurrentUser}: {@code CurrentUser}
 * offers a key-value storage by providing thread-safe {@code get} and {@code put} methods. Once the
 * value is retrieved through {@code get} there is not thread-safety anymore - apart from the
 * retrieved object guarantees. Depending on the implementation of {@code CurrentUser}, it might be
 * shared between the request serving thread as well as sub- or background treads.
 *
 * <p>In comparison to that, this class guarantees thread safety even on non-thread-safe objects as
 * its cache is tied to the serving thread only. While allowing to cache non-thread-safe objects, it
 * has the downside of not sharing any objects with background threads or executors.
 *
 * <p>Lastly, this class offers a cache, that requires callers to also provide a {@code Supplier} in
 * case the object is not present in the cache, while {@code CurrentUser} provides a storage where
 * just retrieving stored values is a valid operation.
 *
 * <p>To prevent OOM errors on requests that would cache a lot of objects, this class enforces an
 * internal limit after which no new elements are cached. All {@code get} calls are served by
 * invoking the {@code Supplier} after that.
 */
public class PerThreadCache implements AutoCloseable {
  private static final ThreadLocal<PerThreadCache> CACHE = new ThreadLocal<>();
  /**
   * Cache at maximum 25 values per thread. This value was chosen arbitrarily. Some endpoints (like
   * ListProjects) break the assumption that the data cached in a request is limited. To prevent
   * this class from accumulating an unbound number of objects, we enforce this limit.
   */
  private static final int PER_THREAD_CACHE_SIZE = 25;

  /**
   * Unique key for key-value mappings stored in PerThreadCache. The key is based on the value's
   * class and a list of identifiers that in combination uniquely set the object apart form others
   * of the same class.
   */
  public static final class Key<T> {
    private final Class<T> clazz;
    private final ImmutableList<Object> identifiers;

    /**
     * Returns a key based on the value's class and an identifier that uniquely identify the value.
     * The identifier needs to implement {@code equals()} and {@code hashCode()}.
     */
    public static <T> Key<T> create(Class<T> clazz, Object identifier) {
      return new Key<>(clazz, ImmutableList.of(identifier));
    }

    /**
     * Returns a key based on the value's class and a set of identifiers that uniquely identify the
     * value. Identifiers need to implement {@code equals()} and {@code hashCode()}.
     */
    public static <T> Key<T> create(Class<T> clazz, Object... identifiers) {
      return new Key<>(clazz, ImmutableList.copyOf(identifiers));
    }

    private Key(Class<T> clazz, ImmutableList<Object> identifiers) {
      this.clazz = clazz;
      this.identifiers = identifiers;
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(clazz, identifiers);
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof Key)) {
        return false;
      }
      Key<?> other = (Key<?>) o;
      return this.clazz == other.clazz && this.identifiers.equals(other.identifiers);
    }
  }

  public static PerThreadCache create() {
    checkState(CACHE.get() == null, "called create() twice on the same request");
    PerThreadCache cache = new PerThreadCache();
    CACHE.set(cache);
    return cache;
  }

  @Nullable
  public static PerThreadCache get() {
    return CACHE.get();
  }

  public static <T> T getOrCompute(Key<T> key, Supplier<T> loader) {
    PerThreadCache cache = get();
    return cache != null ? cache.get(key, loader) : loader.get();
  }

  private final Map<Key<?>, Object> cache = Maps.newHashMapWithExpectedSize(PER_THREAD_CACHE_SIZE);

  private PerThreadCache() {}

  /**
   * Returns an instance of {@code T} that was either loaded from the cache or obtained from the
   * provided {@link Supplier}.
   */
  public <T> T get(Key<T> key, Supplier<T> loader) {
    @SuppressWarnings("unchecked")
    T value = (T) cache.get(key);
    if (value == null) {
      value = loader.get();
      if (cache.size() < PER_THREAD_CACHE_SIZE) {
        cache.put(key, value);
      }
    }
    return value;
  }

  @Override
  public void close() {
    CACHE.remove();
  }
}
