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

package com.google.gerrit.server.account;

import com.google.gerrit.client.reviewdb.AccountGroup;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.reviewdb.SystemConfig;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheException;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import net.sf.ehcache.constructs.blocking.CacheEntryFactory;
import net.sf.ehcache.constructs.blocking.SelfPopulatingCache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Tracks group objects in memory for effecient access. */
@Singleton
public class GroupCache {
  private static final Logger log = LoggerFactory.getLogger(GroupCache.class);

  private final SchemaFactory<ReviewDb> schema;
  private final SelfPopulatingCache self;

  private final AccountGroup.Id administrators;

  @Inject
  GroupCache(final SchemaFactory<ReviewDb> sf, final SystemConfig cfg,
      final CacheManager mgr) {
    schema = sf;
    administrators = cfg.adminGroupId;

    final Cache dc = mgr.getCache("groups");
    self = new SelfPopulatingCache(dc, new CacheEntryFactory() {
      @Override
      public Object createEntry(final Object key) throws Exception {
        return lookup((AccountGroup.Id) key);
      }
    });
    mgr.replaceCacheWithDecoratedCache(dc, self);
  }

  private AccountGroup lookup(final AccountGroup.Id groupId)
      throws OrmException {
    final ReviewDb db = schema.open();
    try {
      final AccountGroup group = db.accountGroups().get(groupId);
      if (group != null) {
        return group;
      } else {
        return missingGroup(groupId);
      }
    } finally {
      db.close();
    }
  }

  @SuppressWarnings("unchecked")
  public AccountGroup get(final AccountGroup.Id groupId) {
    if (groupId == null) {
      return null;
    }

    final Element m;
    try {
      m = self.get(groupId);
    } catch (IllegalStateException e) {
      log.error("Cannot lookup group " + groupId, e);
      return missingGroup(groupId);
    } catch (CacheException e) {
      log.error("Cannot lookup effective groups for " + groupId, e);
      return missingGroup(groupId);
    }

    if (m == null || m.getObjectValue() == null) {
      return missingGroup(groupId);
    }
    return (AccountGroup) m.getObjectValue();
  }

  private AccountGroup missingGroup(final AccountGroup.Id groupId) {
    final AccountGroup.NameKey name =
        new AccountGroup.NameKey("Deleted Group" + groupId.toString());
    final AccountGroup g = new AccountGroup(name, groupId);
    g.setAutomaticMembership(true);
    g.setOwnerGroupId(administrators);
    return g;
  }

  public void evict(final AccountGroup.Id groupId) {
    if (groupId != null) {
      self.remove(groupId);
    }
  }
}
