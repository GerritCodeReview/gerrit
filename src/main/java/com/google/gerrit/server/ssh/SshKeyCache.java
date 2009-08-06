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

package com.google.gerrit.server.ssh;

import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import net.sf.ehcache.constructs.blocking.SelfPopulatingCache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;

/** Provides the {@link SshKeyCacheEntry}. */
@Singleton
public class SshKeyCache {
  private final Logger log = LoggerFactory.getLogger(getClass());
  private final SelfPopulatingCache self;

  @Inject
  SshKeyCache(final CacheManager mgr, final SchemaFactory<ReviewDb> db) {
    final Cache dc = mgr.getCache("sshkeys");
    self = new SelfPopulatingCache(dc, new SshKeyCacheEntryFactory(db));
    mgr.replaceCacheWithDecoratedCache(dc, self);
  }

  @SuppressWarnings("unchecked")
  public Iterable<SshKeyCacheEntry> get(String username) {
    try {
      final Element e = self.get(username);
      if (e == null || e.getObjectValue() == null) {
        log.warn("Can't get SSH keys for \"" + username + "\" from cache.");
        return Collections.emptyList();
      }
      return (Iterable<SshKeyCacheEntry>) e.getObjectValue();
    } catch (RuntimeException e) {
      log.error("Can't get SSH keys for \"" + username + "\" from cache.", e);
      return Collections.emptyList();
    }
  }

  public void evict(String username) {
    if (username != null) {
      self.remove(username);
    }
  }
}
