// Copyright (C) 2012 The Android Open Source Project
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
// limitations under the License.package com.google.gerrit.server.git;

package com.google.gerrit.server.git;

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.cache.Cache;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Singleton
public class ChangeCache {
  private static final String ID_CACHE = "changes";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        TypeLiteral<Cache<Project.NameKey, ChangeCacheEntryVal>> cache =
            new TypeLiteral<Cache<Project.NameKey, ChangeCacheEntryVal>>() {};
        core(cache, ID_CACHE)
          .memoryLimit(1024)
          .maxAge(120, TimeUnit.MINUTES);
      }
    };
  }

  private final Cache<Project.NameKey, ChangeCacheEntryVal> cache;
  private final Object createLock = new Object();

  private class ChangeCacheEntryVal {
    private final Set<Change> changes;
    private final Integer numRefs;

    public ChangeCacheEntryVal(final Set<Change> changes, final Integer numRefs) {
      this.changes = changes;
      this.numRefs = numRefs;
    }

    public Set<Change> getChanges() {
      return changes;
    }

    public Integer getNumRefs() {
      return numRefs;
    }
  }

  @Inject
  ChangeCache(@Named(ID_CACHE) Cache<Project.NameKey, ChangeCacheEntryVal> cache) {
    this.cache = cache;
  }

  Set<Change> get(Project.NameKey name, ReviewDb reviewDb, Integer numRefs) throws OrmException {
    ChangeCacheEntryVal cacheEntry = cache.get(name);
    if (cacheEntry == null) {
      synchronized (createLock) {
        cacheEntry = cache.get(name);
        // If no entry exists, or if the number of refs has change,
        // we need a fresh set of changes.
        if (cacheEntry == null || !numRefs.equals(cacheEntry.getNumRefs())) {
          Set<Change> changes = new HashSet<Change>();
          for (Change c : reviewDb.changes().byProject(name)) {
            changes.add(c);
          }
          cacheEntry = new ChangeCacheEntryVal(changes, numRefs);
          cache.put(name, cacheEntry);
        }
      }
    }
    return cacheEntry.getChanges();
  }

  public void remove(Project.NameKey name) {
    synchronized (createLock) {
      cache.remove(name);
    }
  }
}
