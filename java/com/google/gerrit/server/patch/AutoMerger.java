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

import com.google.common.base.Throwables;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.UsedAt;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.git.LockFailureException;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.InMemoryInserter;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryableAction.ActionType;
import com.google.inject.Inject;
import java.io.IOException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.ResolveMerger;
import org.eclipse.jgit.merge.ThreeWayMergeStrategy;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;

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
public class AutoMerger {
  public static final String AUTO_MERGE_MSG_PREFIX = "Auto-merge of ";

  @UsedAt(UsedAt.Project.GOOGLE)
  public static boolean cacheAutomerge(Config cfg) {
    return cfg.getBoolean("change", null, "cacheAutomerge", true);
  }

  private final RetryHelper retryHelper;
  private final PersonIdent gerritIdent;
  private final boolean save;

  @Inject
  AutoMerger(
      RetryHelper retryHelper,
      @GerritServerConfig Config cfg,
      @GerritPersonIdent PersonIdent gerritIdent) {
    this.retryHelper = retryHelper;
    save = cacheAutomerge(cfg);
    this.gerritIdent = gerritIdent;
  }

  /**
   * Creates an auto-merge commit of the parents of the given merge commit.
   *
   * <p>In case of an exception the creation of the auto-merge commit is retried a few times. E.g.
   * this allows the operation to succeed if a Git update fails due to a temporary issue.
   *
   * @return auto-merge commit. Headers of the returned RevCommit are parsed.
   */
  public RevCommit merge(
      Repository repo,
      RevWalk rw,
      ObjectInserter ins,
      RevCommit merge,
      ThreeWayMergeStrategy mergeStrategy)
      throws IOException {
    try {
      return retryHelper
          .action(
              ActionType.GIT_UPDATE,
              "createAutoMerge",
              () -> createAutoMergeCommit(repo, rw, ins, merge, mergeStrategy))
          .call();
    } catch (Exception e) {
      Throwables.throwIfUnchecked(e);
      Throwables.throwIfInstanceOf(e, IOException.class);
      throw new IllegalStateException(e);
    }
  }

  /**
   * Creates an auto-merge commit of the parents of the given merge commit.
   *
   * @return auto-merge commit. Headers of the returned RevCommit are parsed.
   */
  private RevCommit createAutoMergeCommit(
      Repository repo,
      RevWalk rw,
      ObjectInserter ins,
      RevCommit merge,
      ThreeWayMergeStrategy mergeStrategy)
      throws IOException {
    checkArgument(rw.getObjectReader().getCreatedFromInserter() == ins);

    InMemoryInserter tmpIns = null;
    if (ins instanceof InMemoryInserter) {
      // Caller gave us an in-memory inserter, so ensure anything we write from
      // this method is visible to them.
      tmpIns = (InMemoryInserter) ins;
    } else if (!save) {
      // If we don't plan on saving results, use a fully in-memory inserter.
      // Using just a non-flushing wrapper is not sufficient, since in
      // particular DfsInserter might try to write to storage after exceeding an
      // internal buffer size.
      tmpIns = new InMemoryInserter(rw.getObjectReader());
    }

    rw.parseHeaders(merge);
    String refName = RefNames.refsCacheAutomerge(merge.name());
    Ref ref = repo.getRefDatabase().exactRef(refName);
    if (ref != null && ref.getObjectId() != null) {
      RevObject obj = rw.parseAny(ref.getObjectId());
      if (obj instanceof RevCommit) {
        return (RevCommit) obj;
      }
      return commit(repo, rw, tmpIns, ins, refName, obj, merge);
    }

    ResolveMerger m = (ResolveMerger) mergeStrategy.newMerger(repo, true);
    DirCache dc = DirCache.newInCore();
    m.setDirCache(dc);
    m.setObjectInserter(tmpIns == null ? new NonFlushingWrapper(ins) : tmpIns);

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

    return commit(repo, rw, tmpIns, ins, refName, treeId, merge);
  }

  private RevCommit commit(
      Repository repo,
      RevWalk rw,
      @Nullable InMemoryInserter tmpIns,
      ObjectInserter ins,
      String refName,
      ObjectId tree,
      RevCommit merge)
      throws IOException {
    rw.parseHeaders(merge);
    // For maximum stability, choose a single ident using the committer time of
    // the input commit, using the server name and timezone.
    PersonIdent ident =
        new PersonIdent(
            gerritIdent, merge.getCommitterIdent().getWhen(), gerritIdent.getTimeZone());
    CommitBuilder cb = new CommitBuilder();
    cb.setAuthor(ident);
    cb.setCommitter(ident);
    cb.setTreeId(tree);
    cb.setMessage(AUTO_MERGE_MSG_PREFIX + merge.name() + '\n');
    for (RevCommit p : merge.getParents()) {
      cb.addParentId(p);
    }

    if (!save) {
      checkArgument(tmpIns != null);
      try (ObjectReader tmpReader = tmpIns.newReader();
          RevWalk tmpRw = new RevWalk(tmpReader)) {
        return tmpRw.parseCommit(tmpIns.insert(cb));
      }
    }

    checkArgument(tmpIns == null);
    checkArgument(!(ins instanceof InMemoryInserter));
    ObjectId commitId = ins.insert(cb);
    ins.flush();

    RefUpdate ru = repo.updateRef(refName);
    ru.setNewObjectId(commitId);
    ru.disableRefLog();
    switch (ru.forceUpdate()) {
      case FAST_FORWARD:
      case FORCED:
      case NEW:
      case NO_CHANGE:
        return rw.parseCommit(commitId);
      case LOCK_FAILURE:
        throw new LockFailureException(
            String.format("Failed to create auto-merge of %s", merge.name()), ru);
      case IO_FAILURE:
      case NOT_ATTEMPTED:
      case REJECTED:
      case REJECTED_CURRENT_BRANCH:
      case REJECTED_MISSING_OBJECT:
      case REJECTED_OTHER_REASON:
      case RENAMED:
      default:
        throw new IOException(
            String.format(
                "Failed to create auto-merge of %s: Cannot write %s (%s)",
                merge.name(), refName, ru.getResult()));
    }
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
