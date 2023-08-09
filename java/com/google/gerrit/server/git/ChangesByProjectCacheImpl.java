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

import com.google.auto.value.AutoValue;
import com.google.common.cache.Cache;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.ReviewerSet;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.git.ChangesByProjectCache.UseIndex;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.logging.TraceContext.TraceTimer;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
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
 * required for a read permission check.
 */
@Singleton
public class ChangesByProjectCacheImpl implements ChangesByProjectCache {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String CACHE_NAME = "changes_by_project";

  public static class Module extends CacheModule {
    @Override
    protected void configure() {
      cache(CACHE_NAME, Project.NameKey.class, ProjectChanges.class);
      bind(ChangesByProjectCache.class).to(ChangesByProjectCacheImpl.class);
    }
  }

  private final Cache<Project.NameKey, ProjectChanges> cache;
  private final ChangeData.Factory cdFactory;
  private final UseIndex useIndex;
  private final Provider<InternalChangeQuery> queryProvider;

  @Inject
  ChangesByProjectCacheImpl(
      @Named(CACHE_NAME) Cache<Project.NameKey, ProjectChanges> cache,
      ChangeData.Factory cdFactory,
      UseIndex useIndex,
      Provider<InternalChangeQuery> queryProvider) {
    this.cache = cache;
    this.cdFactory = cdFactory;
    this.useIndex = useIndex;
    this.queryProvider = queryProvider;
  }

  @Override
  public Collection<ChangeData> getChangeDatas(Project.NameKey project, Repository repo)
      throws IOException {
    ProjectChanges projectChanges = cache.getIfPresent(project);
    if (projectChanges != null) {
      return projectChanges.getUpdatedChangeDatas(
          project, repo, cdFactory, ChangeNotes.Factory.scanChangeIds(repo), "Updating");
    }
    if (useIndex == UseIndex.TRUE) {
      return queryChangeDatasAndLoad(project);
    }
    return scanChangeDatasAndLoad(project, repo);
  }

  private Collection<ChangeData> scanChangeDatasAndLoad(Project.NameKey project, Repository repo)
      throws IOException {
    ProjectChanges ours = new ProjectChanges();
    ProjectChanges projectChanges = ours;
    try {
      projectChanges = cache.get(project, () -> ours);
    } catch (ExecutionException e) {
      logger.atWarning().withCause(e).log("Cannot load %s for %s", CACHE_NAME, project.get());
    }
    return projectChanges.getUpdatedChangeDatas(
        project,
        repo,
        cdFactory,
        ChangeNotes.Factory.scanChangeIds(repo),
        ours == projectChanges ? "Scanning" : "Updating");
  }

  private Collection<ChangeData> queryChangeDatasAndLoad(Project.NameKey project) {
    Collection<ChangeData> cds = queryChangeDatas(project);
    cache.put(project, new ProjectChanges(cds));
    return cds;
  }

  private Collection<ChangeData> queryChangeDatas(Project.NameKey project) {
    try (TraceTimer timer =
        TraceContext.newTimer(
            "Querying changes of project", Metadata.builder().projectName(project.get()).build())) {
      return queryProvider
          .get()
          .setRequestedFields(ChangeField.CHANGE, ChangeField.REVIEWER, ChangeField.REF_STATE)
          .byProject(project);
    }
  }

  public static class ProjectChanges {
    Map<BranchNameKey, Map<Change.Id, ObjectId>> metaObjectIdByNonPrivateChangeByBranch =
        new ConcurrentHashMap<>();
    Map<Change.Id, PrivateChange> privateChangeById = new ConcurrentHashMap<>();

    public ProjectChanges() {}

    public ProjectChanges(Collection<ChangeData> cds) {
      cds.stream().forEach(cd -> insert(cd));
    }

    public Collection<ChangeData> getUpdatedChangeDatas(
        Project.NameKey project,
        Repository repo,
        ChangeData.Factory cdFactory,
        Map<Change.Id, ObjectId> metaObjectIdByChange,
        String operation) {
      try (TraceTimer timer =
          TraceContext.newTimer(
              operation + " changes of project",
              Metadata.builder().projectName(project.get()).build())) {
        Map<Change.Id, ChangeData> cachedCdByChange = getChangeDataByChange(cdFactory);
        List<ChangeData> cds = new ArrayList<>();
        for (Map.Entry<Change.Id, ObjectId> e : metaObjectIdByChange.entrySet()) {
          Change.Id id = e.getKey();
          ChangeData cached = cachedCdByChange.get(id);
          ChangeData cd = cached;
          if (cd == null || !cached.metaRevisionOrThrow().equals(e.getValue())) {
            cd = cdFactory.create(project, id);
            update(cached, cd);
          }
          cds.add(cd);
        }
        return cds;
      }
    }

    public ProjectChanges update(ChangeData old, ChangeData updated) {
      if (old != null) {
        if (old.isPrivateOrThrow()) {
          privateChangeById.remove(old.getId());
        } else {
          Map<Change.Id, ObjectId> metaObjectIdByNonPrivateChange =
              metaObjectIdByNonPrivateChangeByBranch.get(old.branchOrThrow());
          if (metaObjectIdByNonPrivateChange != null) {
            metaObjectIdByNonPrivateChange.remove(old.getId());
          }
        }
      }
      return insert(updated);
    }

    public ProjectChanges insert(ChangeData cd) {
      if (cd.isPrivateOrThrow()) {
        privateChangeById.put(
            cd.getId(),
            new AutoValue_ChangesByProjectCacheImpl_PrivateChange(
                cd.change(), cd.reviewers(), cd.metaRevisionOrThrow()));
      } else {
        metaObjectIdByNonPrivateChangeByBranch
            .computeIfAbsent(cd.branchOrThrow(), b -> new ConcurrentHashMap<>())
            .put(cd.getId(), cd.metaRevisionOrThrow());
      }
      return this;
    }

    public Map<Change.Id, ChangeData> getChangeDataByChange(ChangeData.Factory cdFactory) {
      Map<Change.Id, ChangeData> cdByChange = new HashMap<>(privateChangeById.size());
      for (PrivateChange pc : privateChangeById.values()) {
        ChangeData cd = cdFactory.create(pc.change());
        cd.setReviewers(pc.reviewers());
        cd.setMetaRevision(pc.metaRevision());
        cdByChange.put(cd.getId(), cd);
      }

      for (Map.Entry<BranchNameKey, Map<Change.Id, ObjectId>> e :
          metaObjectIdByNonPrivateChangeByBranch.entrySet()) {
        for (Map.Entry<Change.Id, ObjectId> e2 : e.getValue().entrySet()) {
          Change.Id id = e2.getKey();
          cdByChange.put(id, cdFactory.createNonPrivate(e.getKey(), id, e2.getValue()));
        }
      }
      return cdByChange;
    }
  }

  @AutoValue
  abstract static class PrivateChange {
    // Fields needed to serve permission checks on private Changes
    abstract Change change();

    @Nullable
    abstract ReviewerSet reviewers();

    abstract ObjectId metaRevision(); // Needed to confirm whether up-to-date
  }
}
