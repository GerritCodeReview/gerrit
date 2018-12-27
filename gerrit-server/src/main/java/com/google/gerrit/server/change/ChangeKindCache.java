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

import com.google.common.base.Objects;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.project.ProjectState;
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
import java.io.Serializable;
import java.util.concurrent.ExecutionException;

/**
 * Cache of {@link ChangeKind} per commit.
 * <p>
 * This is immutable conditioned on the merge strategy (unless the JGit strategy
 * implementation changes, which might invalidate old entries).
 */
public class ChangeKindCache {
  private static final Logger log =
      LoggerFactory.getLogger(ChangeKindCache.class);

  private static final String ID_CACHE = "change_kind";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        cache(ID_CACHE,
            Key.class,
            ChangeKind.class)
          .maximumWeight(0)
          .loader(Loader.class);
      }
    };
  }

  public static class Key implements Serializable {
    private static final long serialVersionUID = 1L;

    private final ObjectId prior;
    private final ObjectId next;
    private final String strategyName;
    private transient Repository repo;

    private Key(ObjectId prior, ObjectId next, String strategyName,
        Repository repo) {
      this.prior = prior.copy();
      this.next = next.copy();
      this.strategyName = strategyName;
      this.repo = repo;
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
        return Objects.equal(prior, k.prior)
            && Objects.equal(next, k.next)
            && Objects.equal(strategyName, k.strategyName);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(prior, next, strategyName);
    }
  }

  @Singleton
  private static class Loader extends CacheLoader<Key, ChangeKind> {
    @Override
    public ChangeKind load(Key key) throws IOException {
      RevWalk walk = new RevWalk(key.repo);
      try {
        RevCommit prior = walk.parseCommit(key.prior);
        walk.parseBody(prior);
        RevCommit next = walk.parseCommit(key.next);
        walk.parseBody(next);

        if (!next.getFullMessage().equals(prior.getFullMessage())) {
          if (next.getTree() == prior.getTree() && isSameParents(prior, next)) {
            return ChangeKind.NO_CODE_CHANGE;
          } else {
            return ChangeKind.REWORK;
          }
        }

        if (prior.getParentCount() != 1 || next.getParentCount() != 1) {
          // Trivial rebases done by machine only work well on 1 parent.
          return ChangeKind.REWORK;
        }

        if (next.getTree() == prior.getTree() &&
           isSameParents(prior, next)) {
          return ChangeKind.TRIVIAL_REBASE;
        }

        // A trivial rebase can be detected by looking for the next commit
        // having the same tree as would exist when the prior commit is
        // cherry-picked onto the next commit's new first parent.
        ThreeWayMerger merger = MergeUtil.newThreeWayMerger(
            key.repo, MergeUtil.createDryRunInserter(), key.strategyName);
        merger.setBase(prior.getParent(0));
        if (merger.merge(next.getParent(0), prior)
            && merger.getResultTreeId().equals(next.getTree())) {
          return ChangeKind.TRIVIAL_REBASE;
        } else {
          return ChangeKind.REWORK;
        }
      } finally {
        key.repo = null;
        walk.close();
      }
    }

    private static boolean isSameParents(RevCommit prior, RevCommit next) {
      if (prior.getParentCount() != next.getParentCount()) {
        return false;
      } else if (prior.getParentCount() == 0) {
        return true;
      }
      return prior.getParent(0).equals(next.getParent(0));
    }
  }

  private final LoadingCache<Key, ChangeKind> cache;
  private final boolean useRecursiveMerge;

  @Inject
  ChangeKindCache(
      @GerritServerConfig Config serverConfig,
      @Named(ID_CACHE) LoadingCache<Key, ChangeKind> cache) {
    this.cache = cache;
    this.useRecursiveMerge = MergeUtil.useRecursiveMerge(serverConfig);
  }

  public ChangeKind getChangeKind(ProjectState project, Repository repo,
      ObjectId prior, ObjectId next) {
    checkNotNull(next, "next");
    String strategyName = MergeUtil.mergeStrategyName(
        project.isUseContentMerge(), useRecursiveMerge);
    try {
      return cache.get(new Key(prior, next, strategyName, repo));
    } catch (ExecutionException e) {
      log.warn("Cannot check trivial rebase of new patch set " + next.name()
          + " in " + project.getProject().getName(), e);
      return ChangeKind.REWORK;
    }
  }
}
