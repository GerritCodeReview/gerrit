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
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.server.cache.CacheModule;
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
import org.eclipse.jgit.lib.Ref;
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
  @Nullable private final SearchingChangeCacheImpl searchingChangeCacheImpl;
  private final ChangeData.Factory cdFactory;

  @Inject
  ChangesByProjectCache(
      @Named(CACHE_NAME) Cache<Project.NameKey, ProjectCache> cache,
      @Nullable SearchingChangeCacheImpl searchingChangeCacheImpl,
      ChangeData.Factory cdFactory) {
    this.cache = cache;
    this.searchingChangeCacheImpl = searchingChangeCacheImpl;
    this.cdFactory = cdFactory;
  }

  public Map<Change.Id, ChangeData> getChangeDataByChange(Project.NameKey project, Repository repo)
      throws IOException {
    Map<Change.Id, Ref> metaRefByChange = getMetaRefByChange(repo);
    Map<Change.Id, ChangeData> cachedCdByChange = Collections.emptyMap();
    ProjectCache projectCache = null;
    try {
      projectCache =
          cache.get(
              project, () -> new ProjectCache(loadChangeDataByChange(project, metaRefByChange)));
      cachedCdByChange = projectCache.getChangeDataByChange(cdFactory);
    } catch (ExecutionException e) {
      logger.atWarning().withCause(e).log("Cannot load %s for %s", CACHE_NAME, project.get());
    }

    Map<Change.Id, ChangeData> cdByChange = new HashMap<>();
    for (Map.Entry<Change.Id, Ref> e : metaRefByChange.entrySet()) {
      Change.Id id = e.getKey();
      ChangeData cached = cachedCdByChange.get(id);
      ObjectId revision = e.getValue().getObjectId();
      ChangeData cd = cached;
      if (cd == null || !cached.revisionOrThrow().equals(revision)) {
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
      Project.NameKey project, Map<Change.Id, Ref> metaRefByChange) {
    if (searchingChangeCacheImpl != null) {
      List<ChangeData> cds = searchingChangeCacheImpl.getChangeData(project);
      for (ChangeData cd : cds) {
        // ToDo: Add the metaRef to the index so that its up-to-dateness can
        // be determined non-racily.
        cd.setRevision(metaRefByChange.get(cd.getId()).getObjectId());
      }
      return cds;
    }
    List<ChangeData> cds = new ArrayList<>();
    for (Map.Entry<Change.Id, Ref> e : metaRefByChange.entrySet()) {
      cds.add(cdFactory.create(project, e.getKey(), e.getValue().getObjectId()));
    }
    return cds;
  }

  private static Map<Change.Id, Ref> getMetaRefByChange(Repository repo) throws IOException {
    Map<Change.Id, Ref> refByChange = new HashMap<>();
    for (Ref r : repo.getRefDatabase().getRefsByPrefix(RefNames.REFS_CHANGES)) {
      if (r.getName().endsWith(RefNames.META_SUFFIX)) {
        Change.Id id = Change.Id.fromRef(r.getName());
        if (id != null) {
          refByChange.put(id, r);
        }
      }
    }
    return refByChange;
  }

  private synchronized ProjectCache update(ChangeData old, ChangeData updated) {
    ProjectCache projectCache = cache.getIfPresent(updated.project());
    if (projectCache != null) {
      cache.put(updated.project(), projectCache.update(old, updated));
    }
    return projectCache;
  }

  public static class ProjectCache {
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
            .put(cd.getId(), cd.revisionOrThrow());
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
