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
import com.google.common.cache.Weigher;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.ReviewerSet;
import com.google.gerrit.server.cache.CacheModule;
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
import java.util.stream.Stream;
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
      cache(CACHE_NAME, Project.NameKey.class, CachedProjectChanges.class)
          .weigher(ChangesByProjetCacheWeigher.class);
      bind(ChangesByProjectCache.class).to(ChangesByProjectCacheImpl.class);
    }
  }

  private final Cache<Project.NameKey, CachedProjectChanges> cache;
  private final ChangeData.Factory cdFactory;
  private final UseIndex useIndex;
  private final Provider<InternalChangeQuery> queryProvider;

  @Inject
  ChangesByProjectCacheImpl(
      @Named(CACHE_NAME) Cache<Project.NameKey, CachedProjectChanges> cache,
      ChangeData.Factory cdFactory,
      UseIndex useIndex,
      Provider<InternalChangeQuery> queryProvider) {
    this.cache = cache;
    this.cdFactory = cdFactory;
    this.useIndex = useIndex;
    this.queryProvider = queryProvider;
  }

  /** {@inheritDoc} */
  @Override
  public Stream<ChangeData> streamChangeDatas(Project.NameKey project, Repository repo)
      throws IOException {
    CachedProjectChanges projectChanges = cache.getIfPresent(project);
    if (projectChanges != null) {
      return projectChanges
          .getUpdatedChangeDatas(
              project, repo, cdFactory, ChangeNotes.Factory.scanChangeIds(repo), "Updating")
          .stream();
    }
    if (UseIndex.TRUE.equals(useIndex)) {
      return queryChangeDatasAndLoad(project).stream();
    }
    return scanChangeDatasAndLoad(project, repo).stream();
  }

  private Collection<ChangeData> scanChangeDatasAndLoad(Project.NameKey project, Repository repo)
      throws IOException {
    CachedProjectChanges ours = new CachedProjectChanges();
    CachedProjectChanges projectChanges = ours;
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
    cache.put(project, new CachedProjectChanges(cds));
    return cds;
  }

  private Collection<ChangeData> queryChangeDatas(Project.NameKey project) {
    try (TraceTimer timer =
        TraceContext.newTimer(
            "Querying changes of project", Metadata.builder().projectName(project.get()).build())) {
      return queryProvider
          .get()
          .setRequestedFields(
              ChangeField.CHANGE_SPEC, ChangeField.REVIEWER_SPEC, ChangeField.REF_STATE_SPEC)
          .byProject(project);
    }
  }

  private static class CachedProjectChanges {
    Map<String, Map<Change.Id, ObjectId>> metaObjectIdByNonPrivateChangeByBranch =
        new ConcurrentHashMap<>(); // BranchNameKey "normalized" to a String to dedup project
    Map<Change.Id, PrivateChange> privateChangeById = new ConcurrentHashMap<>();

    public CachedProjectChanges() {}

    public CachedProjectChanges(Collection<ChangeData> cds) {
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
        Map<Change.Id, ChangeData> cachedCdByChange = getChangeDataByChange(project, cdFactory);
        List<ChangeData> cds = new ArrayList<>();
        for (Map.Entry<Change.Id, ObjectId> e : metaObjectIdByChange.entrySet()) {
          Change.Id id = e.getKey();
          ChangeData cached = cachedCdByChange.get(id);
          ChangeData cd = cached;
          try {
            if (cd == null || !cached.metaRevisionOrThrow().equals(e.getValue())) {
              cd = cdFactory.create(project, id);
              update(cached, cd);
            }
          } catch (Exception ex) {
            // Do not let a bad change prevent other changes from being available.
            logger.atFinest().withCause(ex).log("Can't load changeData for %s", id);
          }
          cds.add(cd);
        }
        return cds;
      }
    }

    public CachedProjectChanges update(ChangeData old, ChangeData updated) {
      if (old != null) {
        if (old.isPrivateOrThrow()) {
          privateChangeById.remove(old.getId());
        } else {
          Map<Change.Id, ObjectId> metaObjectIdByNonPrivateChange =
              metaObjectIdByNonPrivateChangeByBranch.get(old.branchOrThrow().branch());
          if (metaObjectIdByNonPrivateChange != null) {
            metaObjectIdByNonPrivateChange.remove(old.getId());
          }
        }
      }
      return insert(updated);
    }

    public CachedProjectChanges insert(ChangeData cd) {
      if (cd.isPrivateOrThrow()) {
        privateChangeById.put(
            cd.getId(),
            new AutoValue_ChangesByProjectCacheImpl_PrivateChange(
                cd.change(), cd.reviewers(), cd.metaRevisionOrThrow()));
      } else {
        metaObjectIdByNonPrivateChangeByBranch
            .computeIfAbsent(cd.branchOrThrow().branch(), b -> new ConcurrentHashMap<>())
            .put(cd.getId(), cd.metaRevisionOrThrow());
      }
      return this;
    }

    public Map<Change.Id, ChangeData> getChangeDataByChange(
        Project.NameKey project, ChangeData.Factory cdFactory) {
      Map<Change.Id, ChangeData> cdByChange = new HashMap<>(privateChangeById.size());
      for (PrivateChange pc : privateChangeById.values()) {
        try {
          ChangeData cd = cdFactory.create(pc.change());
          cd.setReviewers(pc.reviewers());
          cd.setMetaRevision(pc.metaRevision());
          cdByChange.put(cd.getId(), cd);
        } catch (Exception ex) {
          // Do not let a bad change prevent other changes from being available.
          logger.atFinest().withCause(ex).log("Can't load changeData for %s", pc.change().getId());
        }
      }

      for (Map.Entry<String, Map<Change.Id, ObjectId>> e :
          metaObjectIdByNonPrivateChangeByBranch.entrySet()) {
        BranchNameKey branch = BranchNameKey.create(project, e.getKey());
        for (Map.Entry<Change.Id, ObjectId> e2 : e.getValue().entrySet()) {
          Change.Id id = e2.getKey();
          try {
            cdByChange.put(id, cdFactory.createNonPrivate(branch, id, e2.getValue()));
          } catch (Exception ex) {
            // Do not let a bad change prevent other changes from being available.
            logger.atFinest().withCause(ex).log("Can't load changeData for %s", id);
          }
        }
      }
      return cdByChange;
    }

    public int weigh() {
      int size = 0;
      size += 24 * 2; // guess at basic ConcurrentHashMap overhead * 2
      for (Map.Entry<String, Map<Change.Id, ObjectId>> e :
          metaObjectIdByNonPrivateChangeByBranch.entrySet()) {
        size += JavaWeights.REFERENCE + e.getKey().length();
        size +=
            e.getValue().size()
                * (JavaWeights.REFERENCE
                    + JavaWeights.OBJECT // Map.Entry
                    + JavaWeights.REFERENCE
                    + GerritWeights.CHANGE_NUM
                    + JavaWeights.REFERENCE
                    + GerritWeights.OBJECTID);
      }
      for (Map.Entry<Change.Id, PrivateChange> e : privateChangeById.entrySet()) {
        size += JavaWeights.REFERENCE + GerritWeights.CHANGE_NUM;
        size += JavaWeights.REFERENCE + e.getValue().weigh();
      }
      return size;
    }
  }

  @AutoValue
  abstract static class PrivateChange {
    // Fields needed to serve permission checks on private Changes
    abstract Change change();

    @Nullable
    abstract ReviewerSet reviewers();

    abstract ObjectId metaRevision(); // Needed to confirm whether up-to-date

    public int weigh() {
      int size = 0;
      size += JavaWeights.OBJECT; // this
      size += JavaWeights.REFERENCE + weigh(change());
      size += JavaWeights.REFERENCE + weigh(reviewers());
      size += JavaWeights.REFERENCE + GerritWeights.OBJECTID; // metaRevision
      return size;
    }

    private static int weigh(Change c) {
      int size = 0;
      size += JavaWeights.OBJECT; // change
      size += JavaWeights.REFERENCE + GerritWeights.KEY_INT; // changeId
      size += JavaWeights.REFERENCE + (c.getServerId() == null ? 0 : c.getServerId().length());
      size += JavaWeights.REFERENCE + JavaWeights.OBJECT + 40; // changeKey;
      size += JavaWeights.REFERENCE + GerritWeights.TIMESTAMP; // createdOn;
      size += JavaWeights.REFERENCE + GerritWeights.TIMESTAMP; // lastUpdatedOn;
      size += JavaWeights.REFERENCE + GerritWeights.ACCOUNT_ID; // owner;
      size +=
          JavaWeights.REFERENCE
              + c.getDest().project().get().length()
              + c.getDest().branch().length();
      size += JavaWeights.CHAR; // status;
      size += JavaWeights.INT; // currentPatchSetId;
      size += JavaWeights.REFERENCE + c.getSubject().length();
      size += JavaWeights.REFERENCE + (c.getTopic() == null ? 0 : c.getTopic().length());
      size +=
          JavaWeights.REFERENCE
              + (c.getOriginalSubject().equals(c.getSubject())
                  ? 0
                  : c.getOriginalSubject().length());
      size +=
          JavaWeights.REFERENCE + (c.getSubmissionId() == null ? 0 : c.getSubmissionId().length());
      size += JavaWeights.REFERENCE + JavaWeights.BOOLEAN; // isPrivate;
      size += JavaWeights.REFERENCE + JavaWeights.BOOLEAN; // workInProgress;
      size += JavaWeights.REFERENCE + JavaWeights.BOOLEAN; // reviewStarted;
      size += JavaWeights.REFERENCE + (c.getRevertOf() == null ? 0 : GerritWeights.CHANGE_NUM);
      size +=
          JavaWeights.REFERENCE + (c.getCherryPickOf() == null ? 0 : GerritWeights.PACTCH_SET_ID);
      return size;
    }

    private static int weigh(ReviewerSet rs) {
      int size = 0;
      size += JavaWeights.OBJECT; // ReviewerSet
      size += JavaWeights.REFERENCE; // table
      size +=
          rs.asTable().cellSet().size()
              * (JavaWeights.OBJECT // cell (at least one object)
                  + JavaWeights.REFERENCE // ReviewerStateInternal
                  + (JavaWeights.REFERENCE + GerritWeights.ACCOUNT_ID)
                  + (JavaWeights.REFERENCE + GerritWeights.TIMESTAMP));
      size += JavaWeights.REFERENCE; // accounts
      return size;
    }
  }

  private static class ChangesByProjetCacheWeigher
      implements Weigher<Project.NameKey, CachedProjectChanges> {
    @Override
    public int weigh(Project.NameKey project, CachedProjectChanges changes) {
      int size = 0;
      size += project.get().length();
      size += changes.weigh();
      return size;
    }
  }

  private static class GerritWeights {
    public static final int KEY_INT = JavaWeights.OBJECT + JavaWeights.INT; // IntKey
    public static final int CHANGE_NUM = KEY_INT;
    public static final int ACCOUNT_ID = KEY_INT;
    public static final int PACTCH_SET_ID =
        JavaWeights.OBJECT
            + (JavaWeights.REFERENCE + GerritWeights.CHANGE_NUM) // PatchSet.Id.changeId
            + JavaWeights.INT; // PatchSet.Id patch_num;
    public static final int TIMESTAMP = JavaWeights.OBJECT + 8; // Timestamp
    public static final int OBJECTID = JavaWeights.OBJECT + (5 * JavaWeights.INT); // (w1-w5)
  }

  private static class JavaWeights {
    public static final int BOOLEAN = 1;
    public static final int CHAR = 1;
    public static final int INT = 4;
    public static final int OBJECT = 16;
    public static final int REFERENCE = 8;
  }
}
