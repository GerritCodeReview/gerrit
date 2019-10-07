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
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.UsedAt;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.InMemoryInserter;
import com.google.gerrit.server.git.MergeUtil;
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

public class AutoMerger {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @UsedAt(UsedAt.Project.GOOGLE)
  public static boolean cacheAutomerge(Config cfg) {
    return cfg.getBoolean("change", null, "cacheAutomerge", true);
  }

  private final PersonIdent gerritIdent;
  private final boolean save;

  @Inject
  AutoMerger(@GerritServerConfig Config cfg, @GerritPersonIdent PersonIdent gerritIdent) {
    save = cacheAutomerge(cfg);
    this.gerritIdent = gerritIdent;
  }

  /**
   * Perform an auto-merge of the parents of the given merge commit.
   *
   * @return auto-merge commit or {@code null} if an auto-merge commit couldn't be created. Headers
   *     of the returned RevCommit are parsed.
   */
  public RevCommit merge(
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

    boolean couldMerge;
    try {
      couldMerge = m.merge(merge.getParents());
    } catch (IOException | RuntimeException e) {
      // It is not safe to continue further down in this method as throwing
      // an exception most likely means that the merge tree was not created
      // and m.getMergeResults() is empty. This would mean that all paths are
      // unmerged and Gerrit UI would show all paths in the patch list.
      logger.atWarning().withCause(e).log("Error attempting automerge %s", refName);
      return null;
    }

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
    cb.setMessage("Auto-merge of " + merge.name() + '\n');
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
    ru.forceUpdate();
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
