// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.change;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.eclipse.jgit.lib.ObjectIdSerialization.readNotNull;
import static org.eclipse.jgit.lib.ObjectIdSerialization.writeNotNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.Weigher;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.ThreeWayMerger;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

public class ChangeKindCacheImpl implements ChangeKindCache {
  private static final Logger log =
      LoggerFactory.getLogger(ChangeKindCacheImpl.class);

  private static final String ID_CACHE = "change_kind";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        bind(ChangeKindCache.class).to(ChangeKindCacheImpl.class);
        persist(ID_CACHE, Key.class, ChangeKind.class)
            .maximumWeight(2 << 20)
            .weigher(ChangeKindWeigher.class)
            .loader(Loader.class);
      }
    };
  }

  @VisibleForTesting
  public static class NoCache implements ChangeKindCache {
    private final boolean useRecursiveMerge;
    private final ChangeData.Factory changeDataFactory;
    private final ProjectCache projectCache;
    private final GitRepositoryManager repoManager;


    @Inject
    NoCache(
        @GerritServerConfig Config serverConfig,
        ChangeData.Factory changeDataFactory,
        ProjectCache projectCache,
        GitRepositoryManager repoManager) {
      this.useRecursiveMerge = MergeUtil.useRecursiveMerge(serverConfig);
      this.changeDataFactory = changeDataFactory;
      this.projectCache = projectCache;
      this.repoManager = repoManager;
    }

    @Override
    public ChangeKind getChangeKind(ProjectState project, Repository repo,
        ObjectId prior, ObjectId next) {
      try {
        return new Loader().load(
            new Key(repo, prior, next, useRecursiveMerge));
      } catch (IOException e) {
        log.warn("Cannot check trivial rebase of new patch set " + next.name()
            + " in " + project.getProject().getName(), e);
        return ChangeKind.REWORK;
      }
    }

    @Override
    public ChangeKind getChangeKind(ReviewDb db, Change change, PatchSet patch) {
      return getChangeKindInternal(this, db, change, patch, changeDataFactory,
          projectCache, repoManager);
    }
  }

  public static class Key implements Serializable {
    private static final long serialVersionUID = 1L;

    private transient ObjectId prior;
    private transient ObjectId next;
    private transient String strategyName;

    private transient Repository repo; // Passed through to loader on miss.

    private Key(Repository repo, ObjectId prior,
        ObjectId next, boolean useRecursiveMerge) {
      checkNotNull(next, "next");
      String strategyName = MergeUtil.mergeStrategyName(
          true, useRecursiveMerge);
      this.prior = prior.copy();
      this.next = next.copy();
      this.strategyName = strategyName;
      this.repo = repo;
    }

    public Key(ObjectId prior, ObjectId next, String strategyName) {
      this.prior = prior;
      this.next = next;
      this.strategyName = strategyName;
    }

    public ObjectId getPrior() {
      return prior;
    }

    public ObjectId getNext() {
      return next;
    }

    public String getStrategyName() {
      return strategyName;
    }

    @Override
    public boolean equals(Object o) {
      if (o instanceof Key) {
        Key k = (Key) o;
        return Objects.equals(prior, k.prior)
            && Objects.equals(next, k.next)
            && Objects.equals(strategyName, k.strategyName);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return Objects.hash(prior, next, strategyName);
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
      writeNotNull(out, prior);
      writeNotNull(out, next);
      out.writeUTF(strategyName);
    }

    private void readObject(ObjectInputStream in) throws IOException {
      prior = readNotNull(in);
      next = readNotNull(in);
      strategyName = in.readUTF();
    }
  }

  @Singleton
  private static class Loader extends CacheLoader<Key, ChangeKind> {
    @Override
    public ChangeKind load(Key key) throws IOException {
      if (Objects.equals(key.prior, key.next)) {
        return ChangeKind.NO_CODE_CHANGE;
      }

      RevWalk walk = new RevWalk(key.repo);
      try {
        RevCommit prior = walk.parseCommit(key.prior);
        walk.parseBody(prior);
        RevCommit next = walk.parseCommit(key.next);
        walk.parseBody(next);

        if (!next.getFullMessage().equals(prior.getFullMessage())) {
          if (isSameDeltaAndTree(prior, next)) {
            return ChangeKind.NO_CODE_CHANGE;
          } else {
            return ChangeKind.REWORK;
          }
        }

        if (isSameDeltaAndTree(prior, next)) {
          return ChangeKind.NO_CHANGE;
        }

        if (prior.getParentCount() != 1 || next.getParentCount() != 1) {
          // Trivial rebases done by machine only work well on 1 parent.
          return ChangeKind.REWORK;
        }

        // A trivial rebase can be detected by looking for the next commit
        // having the same tree as would exist when the prior commit is
        // cherry-picked onto the next commit's new first parent.
        ThreeWayMerger merger = MergeUtil.newThreeWayMerger(
            key.repo, MergeUtil.createDryRunInserter(key.repo), key.strategyName);
        merger.setBase(prior.getParent(0));
        if (merger.merge(next.getParent(0), prior)
            && merger.getResultTreeId().equals(next.getTree())) {
          return ChangeKind.TRIVIAL_REBASE;
        } else {
          return ChangeKind.REWORK;
        }
      } finally {
        key.repo = null;
        walk.release();
      }
    }

    private static boolean isSameDeltaAndTree(RevCommit prior, RevCommit next) {
      if (next.getTree() != prior.getTree()) {
        return false;
      }

      if (prior.getParentCount() != next.getParentCount()) {
        return false;
      } else if (prior.getParentCount() == 0) {
        return true;
      }

      // Make sure that the prior/next delta is the same - not just the tree.
      // This is done by making sure that the parent trees are equal.
      for (int i = 0; i < prior.getParentCount(); i++) {
        if (next.getParent(i).getTree() != prior.getParent(i).getTree()) {
          return false;
        }
      }
      return true;
    }
  }

  public static class ChangeKindWeigher implements Weigher<Key, ChangeKind> {
    @Override
    public int weigh(Key key, ChangeKind changeKind) {
      return 16 + 2*36 + 2*key.strategyName.length() // Size of Key, 64 bit JVM
          + 2*changeKind.name().length(); // Size of ChangeKind, 64 bit JVM
    }
  }

  private final LoadingCache<Key, ChangeKind> cache;
  private final boolean useRecursiveMerge;
  private final ChangeData.Factory changeDataFactory;
  private final ProjectCache projectCache;
  private final GitRepositoryManager repoManager;

  @Inject
  ChangeKindCacheImpl(
      @GerritServerConfig Config serverConfig,
      @Named(ID_CACHE) LoadingCache<Key, ChangeKind> cache,
      ChangeData.Factory changeDataFactory,
      ProjectCache projectCache,
      GitRepositoryManager repoManager) {
    this.cache = cache;
    this.useRecursiveMerge = MergeUtil.useRecursiveMerge(serverConfig);
    this.changeDataFactory = changeDataFactory;
    this.projectCache = projectCache;
    this.repoManager = repoManager;
  }

  @Override
  public ChangeKind getChangeKind(ProjectState project, Repository repo,
      ObjectId prior, ObjectId next) {
    try {
      return cache.get(new Key(repo, prior, next, useRecursiveMerge));
    } catch (ExecutionException e) {
      log.warn("Cannot check trivial rebase of new patch set " + next.name()
          + " in " + project.getProject().getName(), e);
      return ChangeKind.REWORK;
    }
  }

  @Override
  public ChangeKind getChangeKind(ReviewDb db, Change change, PatchSet patch) {
    return getChangeKindInternal(this, db, change, patch, changeDataFactory,
        projectCache, repoManager);
  }

  private static ChangeKind getChangeKindInternal(
      ChangeKindCache cache,
      ReviewDb db,
      Change change,
      PatchSet patch,
      ChangeData.Factory changeDataFactory,
      ProjectCache projectCache,
      GitRepositoryManager repoManager) {
    Repository repo = null;
    // TODO - dborowitz: add NEW_CHANGE type for default.
    ChangeKind kind = ChangeKind.REWORK;
    // Trivial case: if we're on the first patch, we don't need to open
    // the repository.
    if (patch.getId().get() > 1) {
      try {
        ProjectState projectState = projectCache.checkedGet(change.getProject());

        repo = repoManager.openRepository(change.getProject());

        ChangeData cd = changeDataFactory.create(db, change);
        Collection<PatchSet> patchSetCollection = cd.patches();
        PatchSet priorPs = patch;
        for (PatchSet ps : patchSetCollection) {
          if (ps.getId().get() < patch.getId().get() &&
              (ps.getId().get() > priorPs.getId().get() ||
                  priorPs == patch)) {
            // We only want the previous patch set, so walk until the last one
            priorPs = ps;
          }
        }

        // If we still think the previous patch is the current patch,
        // we only have one patch set.  Return the default.
        // This can happen if a user creates a draft, uploads a second patch,
        // and deletes the draft.
        if (priorPs != patch) {
          kind =
              cache.getChangeKind(projectState, repo,
                  ObjectId.fromString(priorPs.getRevision().get()),
                  ObjectId.fromString(patch.getRevision().get()));
        }
      } catch (IOException | OrmException e) {
        // Do nothing; assume we have a complex change
        log.warn("Unable to get change kind for patchSet " + patch.getPatchSetId() +
            "of change " + change.getChangeId(), e);
      } finally {
        if (repo != null) {
          repo.close();
        }
      }
    }
    return kind;
  }
}
