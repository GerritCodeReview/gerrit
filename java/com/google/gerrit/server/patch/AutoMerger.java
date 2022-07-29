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
import org.eclipse.jgit.lib.Ref;
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

  private enum OperationType {
    CACHE_LOAD,
    IN_MEMORY_WRITE,
    ON_DISK_WRITE
  }

  private final Counter1<OperationType> counter;
  private final Timer1<OperationType> latency;
  private final Provider<PersonIdent> gerritIdentProvider;
  private final boolean save;
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
      Repository repo,
      RevWalk rw,
      InMemoryInserter ins,
      RevCommit merge,
      ThreeWayMergeStrategy mergeStrategy)
      throws IOException {
    checkArgument(rw.getObjectReader().getCreatedFromInserter() == ins);
    Optional<RevCommit> existingCommit =
        lookupCommit(repo, rw, RefNames.refsCacheAutomerge(merge.name()));
    if (existingCommit.isPresent()) {
      counter.increment(OperationType.CACHE_LOAD);
      return existingCommit.get();
    }
    counter.increment(OperationType.IN_MEMORY_WRITE);
    logger.atInfo().log("Computing in-memory AutoMerge for %s", merge.name());
    try (Timer1.Context<OperationType> ignored = latency.start(OperationType.IN_MEMORY_WRITE)) {
      return rw.parseCommit(createAutoMergeCommit(repo.getConfig(), rw, ins, merge, mergeStrategy));
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
      RepoView repoView, RevWalk rw, ObjectInserter ins, RevCommit maybeMergeCommit)
      throws IOException {
    if (maybeMergeCommit.getParentCount() != 2 || !save) {
      logger.atFine().log("AutoMerge not required");
      return Optional.empty();
    }

    String automergeRef = RefNames.refsCacheAutomerge(maybeMergeCommit.name());
    logger.atFine().log("AutoMerge ref=%s, mergeCommit=%s", automergeRef, maybeMergeCommit.name());
    if (repoView.getRef(automergeRef).isPresent()) {
      logger.atFine().log("AutoMerge alredy exists");
      return Optional.empty();
    }

    return Optional.of(
        new ReceiveCommand(
            ObjectId.zeroId(),
            createAutoMergeCommit(repoView, rw, ins, maybeMergeCommit),
            automergeRef));
  }

  /**
   * Creates an auto merge commit for the provided merge commit.
   *
   * <p>Callers are expected to ensure that the provided commit indeed has 2 parents.
   *
   * @return An auto-merge commit. Headers of the returned RevCommit are parsed.
   */
  ObjectId createAutoMergeCommit(
      RepoView repoView, RevWalk rw, ObjectInserter ins, RevCommit mergeCommit) throws IOException {
    ObjectId autoMerge;
    try (Timer1.Context<OperationType> ignored = latency.start(OperationType.ON_DISK_WRITE)) {
      autoMerge =
          createAutoMergeCommit(
              repoView.getConfig(), rw, ins, mergeCommit, configuredMergeStrategy);
    }
    counter.increment(OperationType.ON_DISK_WRITE);
    logger.atFine().log("Added %s AutoMerge ref update for commit", autoMerge.name());
    return autoMerge;
  }

  Optional<RevCommit> lookupCommit(Repository repo, RevWalk rw, String refName) throws IOException {
    Ref ref = repo.getRefDatabase().exactRef(refName);
    if (ref != null && ref.getObjectId() != null) {
      RevObject obj = rw.parseAny(ref.getObjectId());
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
    rw.parseHeaders(merge);
    ResolveMerger m = (ResolveMerger) mergeStrategy.newMerger(ins, repoConfig);
    DirCache dc = DirCache.newInCore();
    m.setDirCache(dc);
    // If we don't plan on saving results, use a fully in-memory inserter.
    // Using just a non-flushing wrapper is not sufficient, since in particular DfsInserter might
    // try to write to storage after exceeding an internal buffer size.
    m.setObjectInserter(ins instanceof InMemoryInserter ? new NonFlushingWrapper(ins) : ins);

    boolean couldMerge = m.merge(merge.getParents());

    ObjectId treeId;
    if (couldMerge) {
      treeId = m.getResultTreeId();
    } else {
      treeId =
          MergeUtil.mergeWithConflicts(
              rw,
              ins,
              dc,
              "HEAD",
              merge.getParent(0),
              "BRANCH",
              merge.getParent(1),
              m.getMergeResults());
    }
    logger.atFine().log("AutoMerge treeId=%s", treeId.name());

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
    ins.flush();

    if (ins instanceof InMemoryInserter) {
      // When using an InMemoryInserter we need to read back the values from that inserter because
      // they are not available.
      try (ObjectReader tmpReader = ins.newReader();
          RevWalk tmpRw = new RevWalk(tmpReader)) {
        return tmpRw.parseCommit(commitId);
      }
    }

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
  }
}
