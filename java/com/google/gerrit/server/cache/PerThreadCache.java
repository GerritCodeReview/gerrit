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
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.server.git.RefCache;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;

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
   * Optional HTTP request associated with the per-thread cache, should the thread be associated
   * with the incoming HTTP thread pool.
   */
  private final Optional<HttpServletRequest> httpRequest;

  /**
   * System property for disabling caching specific key types. TODO: DO NOT MERGE into stable-3.2
   * onwards.
   */
  public static final String PER_THREAD_CACHE_DISABLED_TYPES_PROPERTY =
      "PerThreadCache_disabledTypes";

  /**
   * True when the current thread is associated with an incoming API request that is not changing
   * any repository /meta refs and therefore caching repo refs is safe. TODO: DO NOT MERGE into
   * stable-3.2 onwards.
   */
  private boolean allowRefCache;

  /**
   * Sets the request status flag to read-only temporarily. TODO: DO NOT MERGE into stable-3.2
   * onwards.
   */
  public interface ReadonlyRequestWindow extends AutoCloseable {

    /**
     * Close the request read-only status, restoring the previous value.
     *
     * <p>NOTE: If the previous status was not read-only, the cache is getting cleared for making
     * sure that all potential stale entries coming from a read-only windows are cleared.
     */
    @Override
    default void close() {}
  }

  private class ReadonlyRequestWindowImpl implements ReadonlyRequestWindow {
    private final boolean oldAllowRepoRefsCache;

    private ReadonlyRequestWindowImpl() {
      oldAllowRepoRefsCache = allowRefCache();
      allowRefCache(true);
    }

    @Override
    public void close() {
      allowRefCache(oldAllowRepoRefsCache);
    }
  }

  private final Map<Key<?>, Consumer<Object>> unloaders =
      Maps.newHashMapWithExpectedSize(PER_THREAD_CACHE_SIZE);

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
     * The identifier needs to implement {@code equals()} and {@hashCode()}.
     */
    public static <T> Key<T> create(Class<T> clazz, Object identifier) {
      return new Key<>(clazz, ImmutableList.of(identifier));
    }

    /**
     * Returns a key based on the value's class and a set of identifiers that uniquely identify the
     * value. Identifiers need to implement {@code equals()} and {@hashCode()}.
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

  /**
   * Creates a thread-local cache associated to an incoming HTTP request.
   *
   * <p>The request is considered as read-only if the associated method is GET or HEAD.
   *
   * @param httpRequest HTTP request associated with the thread-local cache
   * @return thread-local cache
   */
  public static PerThreadCache create(@Nullable HttpServletRequest httpRequest) {
    checkState(CACHE.get() == null, "called create() twice on the same request");
    PerThreadCache cache = new PerThreadCache(httpRequest, false);
    CACHE.set(cache);
    return cache;
  }

  /**
   * Creates a thread-local cache associated to an incoming read-only request.
   *
   * @return thread-local cache
   */
  public static PerThreadCache createReadOnly() {
    checkState(CACHE.get() == null, "called create() twice on the same request");
    PerThreadCache cache = new PerThreadCache(null, true);
    CACHE.set(cache);
    return cache;
  }

  @Nullable
  public static PerThreadCache get() {
    return CACHE.get();
  }

  /**
   * Return a cached value associated with a key fetched with a loader and released with an unloader
   * function.
   *
   * @param <T> The data type of the cached value
   * @param key the key associated with the value
   * @param loader the loader function for fetching the value from the key
   * @param unloader the unloader function for releasing the value when unloaded from the cache
   * @return Optional of the cached value or empty if the value could not be cached for any reason
   *     (e.g. cache full)
   */
  public static <T> Optional<T> get(Key<T> key, Supplier<T> loader, Consumer<T> unloader) {
    return Optional.ofNullable(get()).flatMap(c -> c.getWithLoader(key, loader, unloader));
  }

  /**
   * Legacy way for retrieving a cached element through a loader.
   *
   * <p>This method is deprecated because it was error-prone due to the unclear ownership of the
   * objects created through the loader. When the cache has space available, the entries are loaded
   * and cached, hence owned and reused by the cache.
   *
   * <p>When the cache is full, this method just short-circuit to the invocation of the loader and
   * the objects created aren't owned or stored by the cache, leaving the space for potential memory
   * and resources leaks.
   *
   * <p>Because of the unclear semantics of the method (who owns the instances? are they reused?)
   * this is now deprecated the the caller should use instead the {@link PerThreadCache#get(Key,
   * Supplier, Consumer)} which has a clear ownership policy.
   *
   * @deprecated use {@link PerThreadCache#get(Key, Supplier, Consumer)}
   */
  public static <T> T getOrCompute(Key<T> key, Supplier<T> loader) {
    PerThreadCache cache = get();
    return cache != null ? cache.get(key, loader) : loader.get();
  }

  private final Map<Key<?>, Object> cache = Maps.newHashMapWithExpectedSize(PER_THREAD_CACHE_SIZE);
  private final ImmutableSet<String> disabledTypes;

  private PerThreadCache(@Nullable HttpServletRequest req, boolean alwaysCacheRepoRefs) {
    httpRequest = Optional.ofNullable(req);

    disabledTypes =
        ImmutableSet.copyOf(
            Splitter.on(',')
                .split(System.getProperty(PER_THREAD_CACHE_DISABLED_TYPES_PROPERTY, "")));

    allowRefCache =
        alwaysCacheRepoRefs
            || (req != null
                && (req.getMethod().equalsIgnoreCase("GET")
                    || req.getMethod().equalsIgnoreCase("HEAD")));
  }

  /**
   * Legacy way of retrieving an instance of {@code T} that was either loaded from the cache or
   * obtained from the provided {@link Supplier}.
   *
   * <p>This method is deprecated because it was error-prone due to the unclear ownership of the
   * objects created through the loader. When the cache has space available, the entries are loaded
   * and cached, hence owned and reused by the cache.
   *
   * <p>When the cache is full, this method just short-circuit to the invocation of the loader and
   * the objects created aren't owned or stored by the cache, leaving the space for potential memory
   * and resources leaks.
   *
   * <p>Because of the unclear semantics of the method (who owns the instances? are they reused?)
   * this is now deprecated the the caller should use instead the {@link PerThreadCache#get(Key,
   * Supplier, Consumer)} which has a clear ownership policy.
   *
   * @deprecated use {@link PerThreadCache#getWithLoader(Key, Supplier, Consumer)}
   */
  public <T> T get(Key<T> key, Supplier<T> loader) {
    return getWithLoader(key, loader, null).orElse(loader.get());
  }

  @SuppressWarnings("unchecked")
  public <T> Optional<T> getWithLoader(
      Key<T> key, Supplier<T> loader, @Nullable Consumer<T> unloader) {
    if (disabledTypes.contains(key.clazz.getCanonicalName())) {
      return Optional.empty();
    }

    T value = (T) cache.get(key);
    if (value == null && cache.size() < PER_THREAD_CACHE_SIZE) {
      value = loader.get();
      cache.put(key, value);
      if (unloader != null) {
        unloaders.put(key, (Consumer<Object>) unloader);
      }
    }
    return Optional.ofNullable(value);
  }

  /** Returns the optional HTTP request associated with the local thread cache. */
  public Optional<HttpServletRequest> getHttpRequest() {
    return httpRequest;
  }

  /** Returns true if there is an HTTP request associated and is a GET or HEAD */
  public boolean hasReadonlyRequest() {
    return httpRequest
        .map(HttpServletRequest::getMethod)
        .filter(m -> m.equalsIgnoreCase("GET") || m.equalsIgnoreCase("HEAD"))
        .isPresent();
  }

  /** Returns an instance of {@code T} that is already loaded from the cache or null otherwise. */
  @SuppressWarnings("unchecked")
  public <T> T get(Key<T> key) {
    return (T) cache.get(key);
  }

  /**
   * Returns true if the associated request is read-only and therefore the repo refs are safe to be
   * cached
   */
  public boolean allowRefCache() {
    return allowRefCache;
  }

  /**
   * Set the cache read-only request status temporarily, for enabling caching of all entries.
   *
   * @return {@link ReadonlyRequestWindow} associated with the incoming request
   */
  public static ReadonlyRequestWindow openReadonlyRequestWindow() {
    PerThreadCache perThreadCache = CACHE.get();
    return perThreadCache == null
        ? new ReadonlyRequestWindow() {}
        : perThreadCache.new ReadonlyRequestWindowImpl();
  }

  @Override
  public void close() {
    unload(unloaders.entrySet());
    CACHE.remove();
  }

  private void unload(Collection<Entry<Key<?>, Consumer<Object>>> entriesToUnload) {
    ImmutableSet<Entry<Key<?>, Consumer<Object>>> toUnload = ImmutableSet.copyOf(entriesToUnload);
    try {
      toUnload.stream().forEach(this::unload);
    } finally {
      toUnload.stream()
          .forEach(
              e -> {
                cache.remove(e.getKey());
                unloaders.remove(e.getKey());
              });
    }
  }

  private <T> void unload(Entry<Key<?>, Consumer<Object>> unloaderEntry) {
    Object valueToUnload = cache.get(unloaderEntry.getKey());
    unloaderEntry.getValue().accept(valueToUnload);
    cache.remove(unloaderEntry.getKey());
  }

  private void allowRefCache(boolean allowed) {
    allowRefCache = allowed;

    if (!allowRefCache) {
      unload(
          unloaders.entrySet().stream()
              .filter(e -> RefCache.class.isAssignableFrom(e.getKey().clazz))
              .collect(Collectors.toSet()));
    }
  }
}
