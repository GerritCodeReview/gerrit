// Copyright (C) 2008 The Android Open Source Project
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

import static java.util.concurrent.TimeUnit.SECONDS;

import net.sf.ehcache.CacheException;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import net.sf.ehcache.constructs.blocking.CacheEntryFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * A decorator for {@link Cache} which automatically constructs missing entries.
 * <p>
 * On a cache miss {@link EntryCreator#createEntry(Object)} is invoked, allowing
 * the application specific subclass to compute the entry and return it for
 * caching. During a miss the cache takes a lock related to the missing key,
 * ensuring that at most one thread performs the creation work, and other
 * threads wait for the result. Concurrent creations are possible if two
 * different keys miss and hash to different locks in the internal lock table.
 *
 * @param <K> type of key used to name cache entries.
 * @param <V> type of value stored within a cache entry.
 */
class PopulatingCache<K, V> implements Cache<K, V> {
  private static final Logger log =
      LoggerFactory.getLogger(PopulatingCache.class);

  private final net.sf.ehcache.constructs.blocking.SelfPopulatingCache self;
  private final EntryCreator<K, V> creator;

  PopulatingCache(Ehcache s, EntryCreator<K, V> entryCreator) {
    creator = entryCreator;
    final CacheEntryFactory f = new CacheEntryFactory() {
      @SuppressWarnings("unchecked")
      @Override
      public Object createEntry(Object key) throws Exception {
        return creator.createEntry((K) key);
      }
    };
    self = new net.sf.ehcache.constructs.blocking.SelfPopulatingCache(s, f);
  }

  /**
   * Get the element from the cache, or {@link EntryCreator#missing(Object)} if not found.
   * <p>
   * The {@link EntryCreator#missing(Object)} method is only invoked if:
   * <ul>
   * <li>{@code key == null}, in which case the application should return a
   * suitable return value that callers can accept, or throw a RuntimeException.
   * <li>{@code createEntry(key)} threw an exception, in which case the entry
   * was not stored in the cache. An entry was recorded in the application log,
   * but a return value is still required.
   * <li>The cache has been shutdown, and access is forbidden.
   * </ul>
   *
   * @param key key to locate.
   * @return either the cached entry, or {@code missing(key)} if not found.
   */
  @SuppressWarnings("unchecked")
  public V get(final K key) {
    if (key == null) {
      return creator.missing(key);
    }

    final Element m;
    try {
      m = self.get(key);
    } catch (IllegalStateException err) {
      log.error("Cannot lookup " + key + " in \"" + self.getName() + "\"", err);
      return creator.missing(key);
    } catch (CacheException err) {
      log.error("Cannot lookup " + key + " in \"" + self.getName() + "\"", err);
      return creator.missing(key);
    }
    return m != null ? (V) m.getObjectValue() : creator.missing(key);
  }

  public void remove(final K key) {
    if (key != null) {
      self.remove(key);
    }
  }

  /** Remove all cached items, forcing them to be created again on demand. */
  public void removeAll() {
    self.removeAll();
  }

  public void put(K key, V value) {
    self.put(new Element(key, value));
  }

  @Override
  public long getTimeToLive(final TimeUnit unit) {
    final long maxAge = self.getCacheConfiguration().getTimeToLiveSeconds();
    return unit.convert(maxAge, SECONDS);
  }

  @Override
  public String toString() {
    return "Cache[" + self.getName() + "]";
  }
}
