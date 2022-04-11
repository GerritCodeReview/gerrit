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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.server.cache.PerThreadCache;
import com.google.gerrit.server.cache.PerThreadCache.Key;
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
public class RepoRefCache implements RefCache, PerThreadCache.CacheStalenessCheck {
  private static FluentLogger log = FluentLogger.forEnclosingClass();

  private final RefDatabase refdb;
  private final Map<String, Optional<ObjectId>> ids;
  private final Repository repo;
  private final AtomicBoolean open;

  /** TODO: REMOVE after merging to stable-3.2 */
  @SuppressWarnings("resource")
  public static Optional<RefCache> getOptional(Repository repo) {
    PerThreadCache cache = PerThreadCache.get();
    if (cache != null && cache.allowRepoRefsCache()) {
      Key<RepoRefCache> refCacheKey = PerThreadCache.Key.create(RepoRefCache.class, repo);
      RepoRefCache refCache = cache.get(refCacheKey, () -> new RepoRefCache(repo));
      if (cache.get(refCacheKey) != null) {
        return Optional.of(
            new RefCache() {
              private final AtomicBoolean wrapperClosed = new AtomicBoolean();

              @Override
              public Optional<ObjectId> get(String refName) throws IOException {
                return refCache.get(refName);
              }

              @Override
              public void close() {
                if (wrapperClosed.getAndSet(true)) {
                  refCache.close();
                }
              }
            });
      }
      return Optional.of(refCache);
    }

    return Optional.empty();
  }

  public RepoRefCache(Repository repo) {
    repo.incrementOpen();
    this.repo = repo;
    this.refdb = repo.getRefDatabase();
    this.ids = new HashMap<>();
    open = new AtomicBoolean(true);
  }

  @Override
  public Optional<ObjectId> get(String refName) throws IOException {
    checkIsOpen();
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
    checkIsOpen();
    return Collections.unmodifiableMap(ids);
  }

  @Override
  public void close() {
    checkIsOpen();

    repo.close();
    open.set(false);
  }

  private void checkIsOpen() {
    if (!open.get()) {
      throw new IllegalStateException("RepoRefCache for repository " + repo + " is already closed");
    }
  }

  /** TODO: DO NOT MERGE into stable-3.2 onwards. */
  @Override
  public void checkStaleness() {
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
