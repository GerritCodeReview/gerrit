// Copyright (C) 2023 The Android Open Source Project
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

import com.google.common.cache.Cache;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;

/**
 * Lightweight cache of changes in each project.
 *
 * <p>This cache is intended to be used when filtering references and stores only the minimal fields
 * required for a read permission check. It will use the {@link
 * com.google.gerrit.server.git.SearchingChangeCacheImpl} if it's available.
 */
@Singleton
public class ChangesByProjectCache {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String CACHE_NAME = "changes_by_project";

  public static class ChangesByProjectCacheModule extends CacheModule {
    @Override
    protected void configure() {
      cache(CACHE_NAME, Project.NameKey.class, ProjectCache.class);
      bind(ChangesByProjectCache.class);
    }
  }

  private final Cache<Project.NameKey, ProjectCache> cache;
  private final ChangeData.Factory cdFactory;
  @Nullable private final SearchingChangeCacheImpl searchingChangeCacheImpl;

  @Inject
  ChangesByProjectCache(
      @Named(CACHE_NAME) Cache<Project.NameKey, ProjectCache> cache,
      ChangeData.Factory cdFactory,
      @Nullable SearchingChangeCacheImpl searchingChangeCacheImpl) {
    this.cache = cache;
    this.cdFactory = cdFactory;
    this.searchingChangeCacheImpl = searchingChangeCacheImpl;
  }

  public Map<Change.Id, ChangeData> getChangeDataByChange(Project.NameKey project, Repository repo)
      throws IOException {
    Map<Change.Id, ObjectId> metaRefIdByChange = ChangeNotes.Factory.scanChangeIds(repo);
    Map<Change.Id, ChangeData> cachedCdByChange = Collections.emptyMap();
    ProjectCache projectCache = null;
    try {
      projectCache =
          cache.get(
              project, () -> new ProjectCache(loadChangeDataByChange(project, metaRefIdByChange)));
      cachedCdByChange = projectCache.getChangeDataByChange(cdFactory);
    } catch (ExecutionException e) {
      logger.atWarning().withCause(e).log("Cannot load %s for %s", CACHE_NAME, project.get());
    }

    Map<Change.Id, ChangeData> cdByChange = new HashMap<>();
    for (Map.Entry<Change.Id, ObjectId> e : metaRefIdByChange.entrySet()) {
      Change.Id id = e.getKey();
      ChangeData cached = cachedCdByChange.get(id);
      ChangeData cd = cached;
      if (cd == null || !cached.metaRevisionOrThrow().equals(e.getValue())) {
        cd = cdFactory.create(project, id);
        if (projectCache != null) {
          update(cached, cd);
        }
      }
      cdByChange.put(id, cd);
    }
    return cdByChange;
  }

  private List<ChangeData> loadChangeDataByChange(
      Project.NameKey project, Map<Change.Id, ObjectId> metaRefIdByChange) {
    if (searchingChangeCacheImpl != null) {
      List<ChangeData> cds = searchingChangeCacheImpl.getChangeData(project);
      for (ChangeData cd : cds) {
        // ToDo: Use the metaRefId from the index so that its up-to-dateness can
        // be determined non-racily.
        cd.setMetaRevision(metaRefIdByChange.get(cd.getId()));
      }
      return cds;
    }
    List<ChangeData> cds = new ArrayList<>();
    for (Map.Entry<Change.Id, ObjectId> e : metaRefIdByChange.entrySet()) {
      cds.add(cdFactory.create(project, e.getKey(), e.getValue()));
    }
    return cds;
  }

  private synchronized ProjectCache update(ChangeData old, ChangeData updated) {
    ProjectCache projectCache = cache.getIfPresent(updated.project());
    if (projectCache != null) {
      cache.put(updated.project(), projectCache.update(old, updated));
    }
    return projectCache;
  }

  private static class ProjectCache {
    Map<BranchNameKey, Map<Change.Id, ObjectId>> metaObjectIdByPublicChangeByBranch =
        new ConcurrentHashMap<>();
    Map<Change.Id, ChangeData> privateChangeDataByChange = new ConcurrentHashMap<>();

    public ProjectCache(Collection<ChangeData> cds) {
      cds.stream().forEach(cd -> insert(cd));
    }

    public ProjectCache update(ChangeData old, ChangeData updated) {
      if (old != null) {
        if (old.isPublicOrThrow()) {
          Map<Change.Id, ObjectId> metaObjectIdByPublicChange =
              metaObjectIdByPublicChangeByBranch.get(old.branchOrThrow());
          if (metaObjectIdByPublicChange != null) {
            metaObjectIdByPublicChange.remove(old.getId());
          }
        } else {
          privateChangeDataByChange.remove(old.getId());
        }
      }
      return insert(updated);
    }

    public ProjectCache insert(ChangeData cd) {
      if (cd.isPublicOrThrow()) {
        metaObjectIdByPublicChangeByBranch
            .computeIfAbsent(cd.branchOrThrow(), b -> new ConcurrentHashMap<>())
            .put(cd.getId(), cd.metaRevisionOrThrow());
      } else {
        privateChangeDataByChange.put(cd.getId(), cd);
      }
      return this;
    }

    public Map<Change.Id, ChangeData> getChangeDataByChange(ChangeData.Factory cdFactory) {
      Map<Change.Id, ChangeData> cdByChange = new HashMap<>(privateChangeDataByChange);
      for (Map.Entry<BranchNameKey, Map<Change.Id, ObjectId>> e :
          metaObjectIdByPublicChangeByBranch.entrySet()) {
        for (Map.Entry<Change.Id, ObjectId> e2 : e.getValue().entrySet()) {
          Change.Id id = e2.getKey();
          cdByChange.put(id, cdFactory.createPublic(e.getKey(), id, e2.getValue()));
        }
      }
      return cdByChange;
    }
  }
}
