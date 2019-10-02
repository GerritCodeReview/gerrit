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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.Cache;
import com.google.common.cache.Weigher;
import com.google.common.collect.FluentIterable;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.client.ChangeKind;
import com.google.gerrit.proto.Protos;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.cache.proto.Cache.ChangeKindKeyProto;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import com.google.gerrit.server.cache.serialize.EnumCacheSerializer;
import com.google.gerrit.server.cache.serialize.ObjectIdConverter;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.InMemoryInserter;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.ThreeWayMerger;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

public class ChangeKindCacheImpl implements ChangeKindCache {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String ID_CACHE = "change_kind";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        bind(ChangeKindCache.class).to(ChangeKindCacheImpl.class);
        persist(ID_CACHE, Key.class, ChangeKind.class)
            .maximumWeight(2 << 20)
            .weigher(ChangeKindWeigher.class)
            .version(1)
            .keySerializer(new Key.Serializer())
            .valueSerializer(new EnumCacheSerializer<>(ChangeKind.class));
      }
    };
  }

  public static class NoCache implements ChangeKindCache {
    private final boolean useRecursiveMerge;
    private final ChangeData.Factory changeDataFactory;
    private final GitRepositoryManager repoManager;

    @Inject
    NoCache(
        @GerritServerConfig Config serverConfig,
        ChangeData.Factory changeDataFactory,
        GitRepositoryManager repoManager) {
      this.useRecursiveMerge = MergeUtil.useRecursiveMerge(serverConfig);
      this.changeDataFactory = changeDataFactory;
      this.repoManager = repoManager;
    }

    @Override
    public ChangeKind getChangeKind(
        Project.NameKey project,
        @Nullable RevWalk rw,
        @Nullable Config repoConfig,
        ObjectId prior,
        ObjectId next) {
      try {
        Key key = Key.create(prior, next, useRecursiveMerge);
        return new Loader(key, repoManager, project, rw, repoConfig).call();
      } catch (IOException e) {
        logger.atWarning().withCause(e).log(
            "Cannot check trivial rebase of new patch set %s in %s", next.name(), project);
        return ChangeKind.REWORK;
      }
    }

    @Override
    public ChangeKind getChangeKind(Change change, PatchSet patch) {
      return getChangeKindInternal(this, change, patch, changeDataFactory, repoManager);
    }

    @Override
    public ChangeKind getChangeKind(
        @Nullable RevWalk rw, @Nullable Config repoConfig, ChangeData cd, PatchSet patch) {
      return getChangeKindInternal(this, rw, repoConfig, cd, patch);
    }
  }

  @AutoValue
  public abstract static class Key {
    public static Key create(AnyObjectId prior, AnyObjectId next, String strategyName) {
      return new AutoValue_ChangeKindCacheImpl_Key(prior.copy(), next.copy(), strategyName);
    }

    private static Key create(AnyObjectId prior, AnyObjectId next, boolean useRecursiveMerge) {
      return create(prior, next, MergeUtil.mergeStrategyName(true, useRecursiveMerge));
    }

    public abstract ObjectId prior();

    public abstract ObjectId next();

    public abstract String strategyName();

    @VisibleForTesting
    static class Serializer implements CacheSerializer<Key> {
      @Override
      public byte[] serialize(Key object) {
        ObjectIdConverter idConverter = ObjectIdConverter.create();
        return Protos.toByteArray(
            ChangeKindKeyProto.newBuilder()
                .setPrior(idConverter.toByteString(object.prior()))
                .setNext(idConverter.toByteString(object.next()))
                .setStrategyName(object.strategyName())
                .build());
      }

      @Override
      public Key deserialize(byte[] in) {
        ChangeKindKeyProto proto = Protos.parseUnchecked(ChangeKindKeyProto.parser(), in);
        ObjectIdConverter idConverter = ObjectIdConverter.create();
        return create(
            idConverter.fromByteString(proto.getPrior()),
            idConverter.fromByteString(proto.getNext()),
            proto.getStrategyName());
      }
    }
  }

  private static class Loader implements Callable<ChangeKind> {
    private final Key key;
    private final GitRepositoryManager repoManager;
    private final Project.NameKey projectName;
    private final RevWalk alreadyOpenRw;
    private final Config repoConfig;

    private Loader(
        Key key,
        GitRepositoryManager repoManager,
        Project.NameKey projectName,
        @Nullable RevWalk rw,
        @Nullable Config repoConfig) {
      checkArgument(
          (rw == null && repoConfig == null) || (rw != null && repoConfig != null),
          "must either provide both revwalk/config, or neither; got %s/%s",
          rw,
          repoConfig);
      this.key = key;
      this.repoManager = repoManager;
      this.projectName = projectName;
      this.alreadyOpenRw = rw;
      this.repoConfig = repoConfig;
    }

    @SuppressWarnings("resource") // Resources are manually managed.
    @Override
    public ChangeKind call() throws IOException {
      if (Objects.equals(key.prior(), key.next())) {
        return ChangeKind.NO_CODE_CHANGE;
      }

      RevWalk rw = alreadyOpenRw;
      Config config = repoConfig;
      Repository repo = null;
      if (alreadyOpenRw == null) {
        repo = repoManager.openRepository(projectName);
        rw = new RevWalk(repo);
        config = repo.getConfig();
      }
      try {
        RevCommit prior = rw.parseCommit(key.prior());
        rw.parseBody(prior);
        RevCommit next = rw.parseCommit(key.next());
        rw.parseBody(next);

        if (!next.getFullMessage().equals(prior.getFullMessage())) {
          if (isSameDeltaAndTree(rw, prior, next)) {
            return ChangeKind.NO_CODE_CHANGE;
          }
          return ChangeKind.REWORK;
        }

        if (isSameDeltaAndTree(rw, prior, next)) {
          return ChangeKind.NO_CHANGE;
        }

        if (prior.getParentCount() == 0 || next.getParentCount() == 0) {
          // At this point we have considered all the kinds that could be applicable to root
          // commits; the remainder of the checks in this method all assume that both commits have
          // at least one parent.
          return ChangeKind.REWORK;
        }

        if ((prior.getParentCount() > 1 || next.getParentCount() > 1)
            && !onlyFirstParentChanged(prior, next)) {
          // Trivial rebases done by machine only work well on 1 parent.
          return ChangeKind.REWORK;
        }

        // A trivial rebase can be detected by looking for the next commit
        // having the same tree as would exist when the prior commit is
        // cherry-picked onto the next commit's new first parent.
        try (ObjectInserter ins = new InMemoryInserter(rw.getObjectReader())) {
          ThreeWayMerger merger = MergeUtil.newThreeWayMerger(ins, config, key.strategyName());
          merger.setBase(prior.getParent(0));
          if (merger.merge(next.getParent(0), prior)
              && merger.getResultTreeId().equals(next.getTree())) {
            if (prior.getParentCount() == 1) {
              return ChangeKind.TRIVIAL_REBASE;
            }
            return ChangeKind.MERGE_FIRST_PARENT_UPDATE;
          }
        } catch (LargeObjectException e) {
          // Some object is too large for the merge attempt to succeed. Assume
          // it was a rework.
        }
        return ChangeKind.REWORK;
      } finally {
        if (repo != null) {
          rw.close();
          repo.close();
        }
      }
    }

    public static boolean onlyFirstParentChanged(RevCommit prior, RevCommit next) {
      return !sameFirstParents(prior, next) && sameRestOfParents(prior, next);
    }

    private static boolean sameFirstParents(RevCommit prior, RevCommit next) {
      if (prior.getParentCount() == 0) {
        return next.getParentCount() == 0;
      }
      return prior.getParent(0).equals(next.getParent(0));
    }

    private static boolean sameRestOfParents(RevCommit prior, RevCommit next) {
      Set<RevCommit> priorRestParents = allExceptFirstParent(prior.getParents());
      Set<RevCommit> nextRestParents = allExceptFirstParent(next.getParents());
      return priorRestParents.equals(nextRestParents);
    }

    private static Set<RevCommit> allExceptFirstParent(RevCommit[] parents) {
      return FluentIterable.from(Arrays.asList(parents)).skip(1).toSet();
    }

    private static boolean isSameDeltaAndTree(RevWalk rw, RevCommit prior, RevCommit next)
        throws IOException {
      if (!Objects.equals(next.getTree(), prior.getTree())) {
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
        // Parse parent commits so that their trees are available.
        rw.parseCommit(prior.getParent(i));
        rw.parseCommit(next.getParent(i));

        if (!Objects.equals(next.getParent(i).getTree(), prior.getParent(i).getTree())) {
          return false;
        }
      }
      return true;
    }
  }

  public static class ChangeKindWeigher implements Weigher<Key, ChangeKind> {
    @Override
    public int weigh(Key key, ChangeKind changeKind) {
      return 16
          + 2 * 36
          + 2 * key.strategyName().length() // Size of Key, 64 bit JVM
          + 2 * changeKind.name().length(); // Size of ChangeKind, 64 bit JVM
    }
  }

  private final Cache<Key, ChangeKind> cache;
  private final boolean useRecursiveMerge;
  private final ChangeData.Factory changeDataFactory;
  private final GitRepositoryManager repoManager;

  @Inject
  ChangeKindCacheImpl(
      @GerritServerConfig Config serverConfig,
      @Named(ID_CACHE) Cache<Key, ChangeKind> cache,
      ChangeData.Factory changeDataFactory,
      GitRepositoryManager repoManager) {
    this.cache = cache;
    this.useRecursiveMerge = MergeUtil.useRecursiveMerge(serverConfig);
    this.changeDataFactory = changeDataFactory;
    this.repoManager = repoManager;
  }

  @Override
  public ChangeKind getChangeKind(
      Project.NameKey project,
      @Nullable RevWalk rw,
      @Nullable Config repoConfig,
      ObjectId prior,
      ObjectId next) {
    try {
      Key key = Key.create(prior, next, useRecursiveMerge);
      return cache.get(key, new Loader(key, repoManager, project, rw, repoConfig));
    } catch (ExecutionException e) {
      logger.atWarning().withCause(e).log(
          "Cannot check trivial rebase of new patch set %s in %s", next.name(), project);
      return ChangeKind.REWORK;
    }
  }

  @Override
  public ChangeKind getChangeKind(Change change, PatchSet patch) {
    return getChangeKindInternal(this, change, patch, changeDataFactory, repoManager);
  }

  @Override
  public ChangeKind getChangeKind(
      @Nullable RevWalk rw, @Nullable Config repoConfig, ChangeData cd, PatchSet patch) {
    return getChangeKindInternal(this, rw, repoConfig, cd, patch);
  }

  private static ChangeKind getChangeKindInternal(
      ChangeKindCache cache,
      @Nullable RevWalk rw,
      @Nullable Config repoConfig,
      ChangeData change,
      PatchSet patch) {
    ChangeKind kind = ChangeKind.REWORK;
    // Trivial case: if we're on the first patch, we don't need to use
    // the repository.
    if (patch.id().get() > 1) {
      try {
        Collection<PatchSet> patchSetCollection = change.patchSets();
        PatchSet priorPs = patch;
        for (PatchSet ps : patchSetCollection) {
          if (ps.id().get() < patch.id().get()
              && (ps.id().get() > priorPs.id().get() || priorPs == patch)) {
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
              cache.getChangeKind(
                  change.project(), rw, repoConfig, priorPs.commitId(), patch.commitId());
        }
      } catch (StorageException e) {
        // Do nothing; assume we have a complex change
        logger.atWarning().withCause(e).log(
            "Unable to get change kind for patchSet %s of change %s",
            patch.number(), change.getId());
      }
    }
    return kind;
  }

  private static ChangeKind getChangeKindInternal(
      ChangeKindCache cache,
      Change change,
      PatchSet patch,
      ChangeData.Factory changeDataFactory,
      GitRepositoryManager repoManager) {
    // TODO - dborowitz: add NEW_CHANGE type for default.
    ChangeKind kind = ChangeKind.REWORK;
    // Trivial case: if we're on the first patch, we don't need to open
    // the repository.
    if (patch.id().get() > 1) {
      try (Repository repo = repoManager.openRepository(change.getProject());
          RevWalk rw = new RevWalk(repo)) {
        kind =
            getChangeKindInternal(
                cache, rw, repo.getConfig(), changeDataFactory.create(change), patch);
      } catch (IOException e) {
        // Do nothing; assume we have a complex change
        logger.atWarning().withCause(e).log(
            "Unable to get change kind for patchSet %s of change %s",
            patch.number(), change.getChangeId());
      }
    }
    return kind;
  }
}
