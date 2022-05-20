// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.git;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.server.cache.PerThreadCache;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;

/**
 * {@link RefCache} backed directly by a repository. TODO: DO NOT MERGE
 * PerThreadCache.CacheStalenessCheck into stable-3.2 onwards.
 */
public class RepoRefCache implements RefCache {

  /**
   * System property for enabling the check for stale cache entries. TODO: DO NOT MERGE into
   * stable-3.2 onwards.
   */
  public static final String REPO_REF_CACHE_CHECK_STALE_ENTRIES_PROPERTY =
      "RepoRefCache_checkStaleEntries";

  private static FluentLogger log = FluentLogger.forEnclosingClass();

  private final RefDatabase refdb;
  private final Map<String, Optional<ObjectId>> ids;
  private final Repository repo;
  private final boolean checkStaleEntries;
  private final AtomicBoolean closed;

  public static Optional<RefCache> getOptional(Repository repo) {
    PerThreadCache cache = PerThreadCache.get();
    if (cache != null && cache.allowRefCache()) {
      return cache
          .getWithLoader(
              PerThreadCache.Key.create(RepoRefCache.class, repo),
              () -> new RepoRefCache(repo),
              RepoRefCache::close)
          .map(c -> (RefCache) c);
    }

    return Optional.empty();
  }

  public RepoRefCache(Repository repo) {
    checkStaleEntries =
        Boolean.valueOf(System.getProperty(REPO_REF_CACHE_CHECK_STALE_ENTRIES_PROPERTY, "false"));

    repo.incrementOpen();
    this.repo = repo;
    this.refdb = repo.getRefDatabase();
    this.ids = new HashMap<>();
    this.closed = new AtomicBoolean();
  }

  @Override
  public Optional<ObjectId> get(String refName) throws IOException {
    Optional<ObjectId> id = ids.get(refName);
    if (id != null) {
      return id;
    }
    Ref ref = refdb.exactRef(refName);
    id = Optional.ofNullable(ref).map(Ref::getObjectId);
    ids.put(refName, id);
    return id;
  }

  /** @return an unmodifiable view of the refs that have been cached by this instance. */
  public Map<String, Optional<ObjectId>> getCachedRefs() {
    return Collections.unmodifiableMap(ids);
  }

  @Override
  public void close() {
    if (closed.getAndSet(true)) {
      log.atWarning().log("RepoRefCache of {} closed more than once", repo.getDirectory());
      return;
    }

    if (checkStaleEntries) {
      checkStaleness();
    }

    repo.close();
  }

  /** TODO: DO NOT MERGE into stable-3.2 onwards. */
  @VisibleForTesting
  void checkStaleness() {
    List<String> staleRefs = staleRefs();
    if (staleRefs.size() > 0) {
      throw new IllegalStateException(
          "Repository "
              + repo
              + " had modifications on refs "
              + staleRefs
              + " during a readonly window");
    }
  }

  private List<String> staleRefs() {
    return ids.entrySet().stream()
        .filter(this::isStale)
        .map(Map.Entry::getKey)
        .collect(Collectors.toList());
  }

  private boolean isStale(Map.Entry<String, Optional<ObjectId>> refEntry) {
    String refName = refEntry.getKey();
    Optional<ObjectId> id = ids.get(refName);
    if (id == null) {
      return false;
    }

    try {
      ObjectId diskId = refdb.exactRef(refName).getObjectId();
      boolean isStale = !Optional.ofNullable(diskId).equals(id);
      if (isStale) {
        log.atSevere().log(
            "Repository "
                + repo
                + " has a stale ref "
                + refName
                + " (cache="
                + id
                + ", disk="
                + diskId
                + ")");
      }
      return isStale;
    } catch (IOException e) {
      log.atSevere().withCause(e).log(
          "Unable to check if ref={} from repository={} is stale", refName, repo);
      return true;
    }
  }
}
