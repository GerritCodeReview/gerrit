// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.ehcache;

import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.gerrit.server.cache.Cache;

import net.sf.ehcache.CacheException;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * A fast in-memory and/or on-disk based cache.
 *
 * @type <K> type of key used to lookup entries in the cache.
 * @type <V> type of value stored within each cache entry.
 */
final class SimpleCache<K, V> implements Cache<K, V> {
  private static final Logger log = LoggerFactory.getLogger(SimpleCache.class);

  private final Ehcache self;

  SimpleCache(final Ehcache self) {
    this.self = self;
  }

  Ehcache getEhcache() {
    return self;
  }

  @SuppressWarnings("unchecked")
  public V get(final K key) {
    if (key == null) {
      return null;
    }
    final Element m;
    try {
      m = self.get(key);
    } catch (IllegalStateException err) {
      log.error("Cannot lookup " + key + " in \"" + self.getName() + "\"", err);
      return null;
    } catch (CacheException err) {
      log.error("Cannot lookup " + key + " in \"" + self.getName() + "\"", err);
      return null;
    }
    return m != null ? (V) m.getObjectValue() : null;
  }

  public void put(final K key, final V value) {
    self.put(new Element(key, value));
  }

  public void remove(final K key) {
    if (key != null) {
      self.remove(key);
    }
  }

  public void removeAll() {
    self.removeAll();
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
