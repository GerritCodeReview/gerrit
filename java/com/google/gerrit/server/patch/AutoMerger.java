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

package com.google.gerrit.server.patch;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.UsedAt;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.metrics.Counter1;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.metrics.Timer1;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.InMemoryInserter;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.update.RepoView;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.ResolveMerger;
import org.eclipse.jgit.merge.ThreeWayMergeStrategy;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;

/**
 * Utility class for creating an auto-merge commit of a merge commit.
 *
 * <p>An auto-merge commit is the result of merging the 2 parents of a merge commit automatically.
 * If there are conflicts the auto-merge commit contains Git conflict markers that indicate these
 * conflicts.
 *
 * <p>Creating auto-merge commits for octopus merges (merge commits with more than 2 parents) is not
 * supported. In this case the auto-merge is created between the first 2 parent commits.
 *
 * <p>All created auto-merge commits are stored in the repository of their merge commit as {@code
 * refs/cache-automerge/} branches. These branches serve:
 *
 * <ul>
 *   <li>as a cache so that the each auto-merge gets computed only once
 *   <li>as base for merge commits on which users can comment
 * </ul>
 *
 * <p>The second point means that these commits are referenced from NoteDb. The consequence of this
 * is that these refs should never be deleted.
 */
@Singleton
public class AutoMerger {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static final String AUTO_MERGE_MSG_PREFIX = "Auto-merge of ";

  @UsedAt(UsedAt.Project.GOOGLE)
  public static boolean cacheAutomerge(Config cfg) {
    return cfg.getBoolean("change", null, "cacheAutomerge", true);
  }

  public static boolean diff3ConflictView(Config cfg) {
    return cfg.getBoolean("change", null, "diff3ConflictView", false);
  }

  private enum OperationType {
    CACHE_LOAD,
    IN_MEMORY_WRITE,
    ON_DISK_WRITE
  }

  private final Counter1<OperationType> counter;
  private final Timer1<OperationType> latency;
  private final Provider<PersonIdent> gerritIdentProvider;
  private final boolean save;
  private final boolean useDiff3;
  private final ThreeWayMergeStrategy configuredMergeStrategy;

  @Inject
  AutoMerger(
      MetricMaker metricMaker,
      @GerritServerConfig Config cfg,
      @GerritPersonIdent Provider<PersonIdent> gerritIdentProvider) {
    Field<OperationType> operationTypeField =
        Field.ofEnum(OperationType.class, "type", Metadata.Builder::operationName)
            .description("The type of the operation (CACHE_LOAD, IN_MEMORY_WRITE, ON_DISK_WRITE).")
            .build();
    this.counter =
        metricMaker.newCounter(
            "git/auto-merge/num_operations",
            new Description("AutoMerge computations").setRate().setUnit("auto merge computations"),
            operationTypeField);
    this.latency =
        metricMaker.newTimer(
            "git/auto-merge/latency",
            new Description("AutoMerge computation latency")
                .setCumulative()
                .setUnit("milliseconds"),
            operationTypeField);
    this.save = cacheAutomerge(cfg);
    this.useDiff3 = diff3ConflictView(cfg);
    this.gerritIdentProvider = gerritIdentProvider;
    this.configuredMergeStrategy = MergeUtil.getMergeStrategy(cfg);
  }

  /**
   * Reads or creates an auto-merge commit of the parents of the given merge commit.
   *
   * <p>The result is read from Git or computed in-memory and not written back to Git. This method
   * exists for backwards compatibility only. All new changes have their auto-merge commits written
   * transactionally when the change or patch set is created.
   *
   * @return auto-merge commit. Headers of the returned RevCommit are parsed.
   */
  public RevCommit lookupFromGitOrMergeInMemory(
      Repository repo, RevWalk rw, InMemoryInserter ins, RevCommit merge) throws IOException {
    checkArgument(rw.getObjectReader().getCreatedFromInserter() == ins);

    try (RepoView repoView = new RepoView(repo, rw, ins)) {
      Optional<RevCommit> existingCommit =
          lookupCommit(repoView, RefNames.refsCacheAutomerge(merge.name()));
      if (existingCommit.isPresent()) {
        counter.increment(OperationType.CACHE_LOAD);
        return existingCommit.get();
      }
      counter.increment(OperationType.IN_MEMORY_WRITE);
      logger.atInfo().log("Computing in-memory AutoMerge for %s", merge.name());
      try (Timer1.Context<OperationType> ignored = latency.start(OperationType.IN_MEMORY_WRITE)) {
        return rw.parseCommit(
            createAutoMergeCommit(repo.getConfig(), rw, ins, merge, configuredMergeStrategy));
      }
    }
  }

  /**
   * Creates an auto merge commit for the provided commit in case it is a merge commit. To be used
   * whenever Gerrit creates new patch sets.
   *
   * <p>Callers need to include the returned {@link ReceiveCommand} in their ref transaction.
   *
   * @return A {@link ReceiveCommand} wrapped in an {@link Optional} to be used in a {@link
   *     org.eclipse.jgit.lib.BatchRefUpdate}. {@link Optional#empty()} in case we don't need an
   *     auto merge commit.
   */
  public Optional<ReceiveCommand> createAutoMergeCommitIfNecessary(
      RepoView repoView, ObjectInserter ins, RevCommit maybeMergeCommit) throws IOException {
    if (maybeMergeCommit.getParentCount() != 2) {
      logger.atFine().log("AutoMerge not required");
      return Optional.empty();
    }
    if (!save) {
      logger.atFine().log("Saving AutoMerge is disabled");
      return Optional.empty();
    }

    String automergeRef = RefNames.refsCacheAutomerge(maybeMergeCommit.name());
    logger.atFine().log("AutoMerge ref=%s, mergeCommit=%s", automergeRef, maybeMergeCommit.name());
    if (repoView.getRef(automergeRef).isPresent()) {
      logger.atFine().log("AutoMerge already exists");
      return Optional.empty();
    }

    return Optional.of(
        new ReceiveCommand(
            ObjectId.zeroId(),
            createAutoMergeCommit(repoView, ins, maybeMergeCommit),
            automergeRef));
  }

  /**
   * Creates an auto merge commit for the provided merge commit.
   *
   * <p>Callers are expected to ensure that the provided commit indeed has 2 parents.
   *
   * @return An auto-merge commit. Headers of the returned RevCommit are parsed.
   */
  ObjectId createAutoMergeCommit(RepoView repoView, ObjectInserter ins, RevCommit mergeCommit)
      throws IOException {
    ObjectId autoMerge;
    try (Timer1.Context<OperationType> ignored = latency.start(OperationType.ON_DISK_WRITE)) {
      autoMerge =
          createAutoMergeCommit(
              repoView.getConfig(),
              repoView.getRevWalk(),
              ins,
              mergeCommit,
              configuredMergeStrategy);
    }
    counter.increment(OperationType.ON_DISK_WRITE);
    return autoMerge;
  }

  Optional<RevCommit> lookupCommit(RepoView repoView, String refName) throws IOException {
    Optional<ObjectId> commit = repoView.getRef(refName);
    if (commit.isPresent()) {
      RevObject obj = repoView.getRevWalk().parseAny(commit.get());
      if (obj instanceof RevCommit) {
        return Optional.of((RevCommit) obj);
      }
    }
    return Optional.empty();
  }

  /**
   * Creates an auto-merge commit of the parents of the given merge commit.
   *
   * @return auto-merge commit. Headers of the returned RevCommit are parsed.
   */
  private ObjectId createAutoMergeCommit(
      Config repoConfig,
      RevWalk rw,
      ObjectInserter ins,
      RevCommit merge,
      ThreeWayMergeStrategy mergeStrategy)
      throws IOException {
    // Use a non-flushing inserter to do the merging and do the flushing explicitly when we are done
    // with creating the AutoMerge commit.
    ObjectInserter nonFlushingInserter =
        ins instanceof InMemoryInserter ? ins : new NonFlushingWrapper(ins);

    rw.parseHeaders(merge);
    ResolveMerger m = (ResolveMerger) mergeStrategy.newMerger(nonFlushingInserter, repoConfig);
    DirCache dc = DirCache.newInCore();
    m.setDirCache(dc);

    boolean couldMerge = m.merge(merge.getParents());

    ObjectId treeId;
    if (couldMerge) {
      treeId = m.getResultTreeId();
      logger.atFine().log(
          "AutoMerge treeId=%s (no conflicts, inserter: %s)", treeId.name(), m.getObjectInserter());
    } else {
      if (m.getResultTreeId() != null) {
        // Merging with conflicts below uses the same DirCache instance that has been used by the
        // Merger to attempt the merge without conflicts.
        //
        // The Merger uses the DirCache to do the updates, and in particular to write the result
        // tree. DirCache caches a single DirCacheTree instance that is used to write the result
        // tree, but it writes the result tree only if there were no conflicts.
        //
        // Merging with conflicts uses the same DirCache instance to write the tree with conflicts
        // that has been used by the Merger. This means if the Merger unexpectedly wrote a result
        // tree although there had been conflicts, then merging with conflicts uses the same
        // DirCacheTree instance to write the tree with conflicts. However DirCacheTree#writeTree
        // writes a tree only once and then that tree is cached. Further invocations of
        // DirCacheTree#writeTree have no effect and return the previously created tree. This means
        // merging with conflicts can only successfully create the tree with conflicts if the Merger
        // didn't write a result tree yet. Hence this is checked here and we log a warning if the
        // result tree was already written.
        logger.atWarning().log(
            "result tree has already been written: %s (merge: %s, conflicts: %s, failed: %s)",
            m, m.getResultTreeId().name(), m.getUnmergedPaths(), m.getFailingPaths());
      }

      treeId =
          MergeUtil.mergeWithConflicts(
              rw,
              nonFlushingInserter,
              dc,
              "HEAD",
              merge.getParent(0),
              "BRANCH",
              merge.getParent(1),
              m.getMergeResults(),
              useDiff3);
      logger.atFine().log(
          "AutoMerge treeId=%s (with conflicts, inserter: %s)", treeId.name(), nonFlushingInserter);
    }

    rw.parseHeaders(merge);
    // For maximum stability, choose a single ident using the committer time of
    // the input commit, using the server name and timezone.
    PersonIdent ident =
        new PersonIdent(
            gerritIdentProvider.get(),
            merge.getCommitterIdent().getWhen(),
            gerritIdentProvider.get().getTimeZone());
    CommitBuilder cb = new CommitBuilder();
    cb.setAuthor(ident);
    cb.setCommitter(ident);
    cb.setTreeId(treeId);
    cb.setMessage(AUTO_MERGE_MSG_PREFIX + merge.name() + '\n');
    for (RevCommit p : merge.getParents()) {
      cb.addParentId(p);
    }

    ObjectId commitId = ins.insert(cb);
    logger.atFine().log("AutoMerge commitId=%s", commitId.name());

    if (ins instanceof InMemoryInserter) {
      // When using an InMemoryInserter we need to read back the values from that inserter because
      // they are not available.
      try (ObjectReader tmpReader = ins.newReader();
          RevWalk tmpRw = new RevWalk(tmpReader)) {
        return tmpRw.parseCommit(commitId);
      }
    }

    logger.atFine().log("flushing inserter %s", ins);
    ins.flush();
    return rw.parseCommit(commitId);
  }

  private static class NonFlushingWrapper extends ObjectInserter.Filter {
    private final ObjectInserter ins;

    private NonFlushingWrapper(ObjectInserter ins) {
      this.ins = ins;
    }

    @Override
    protected ObjectInserter delegate() {
      return ins;
    }

    @Override
    public void flush() {}

    @Override
    public void close() {}

    @Override
    public String toString() {
      return String.format("%s (wrapped inserter: %s)", super.toString(), ins.toString());
    }
  }
}
