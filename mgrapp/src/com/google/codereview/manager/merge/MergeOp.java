// Copyright 2008 Google Inc.
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

package com.google.codereview.manager.merge;

import com.google.codereview.internal.PendingMerge.PendingMergeItem;
import com.google.codereview.internal.PendingMerge.PendingMergeResponse;
import com.google.codereview.internal.PostMergeResult.MergeResultItem;
import com.google.codereview.internal.PostMergeResult.MissingDependencyItem;
import com.google.codereview.internal.PostMergeResult.PostMergeResultRequest;
import com.google.codereview.manager.Backend;
import com.google.codereview.manager.InvalidRepositoryException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.spearce.jgit.errors.IncorrectObjectTypeException;
import org.spearce.jgit.errors.MissingObjectException;
import org.spearce.jgit.lib.AnyObjectId;
import org.spearce.jgit.lib.Commit;
import org.spearce.jgit.lib.Constants;
import org.spearce.jgit.lib.ObjectId;
import org.spearce.jgit.lib.PersonIdent;
import org.spearce.jgit.lib.Ref;
import org.spearce.jgit.lib.RefUpdate;
import org.spearce.jgit.lib.Repository;
import org.spearce.jgit.merge.MergeStrategy;
import org.spearce.jgit.merge.Merger;
import org.spearce.jgit.revwalk.RevCommit;
import org.spearce.jgit.revwalk.RevSort;
import org.spearce.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Merges changes in submission order into a single branch.
 * <p>
 * Branches are reduced to the minimum number of heads needed to merge
 * everything. This allows commits to be entered into the queue in any order
 * (such as ancestors before descendants) and only the most recent commit on any
 * line of development will be merged. All unmerged commits along a line of
 * development must be in the submission queue in order to merge the tip of that
 * line.
 * <p>
 * Conflicts are handled by discarding the entire line of development and
 * marking it as conflicting, even if an earlier commit along that same line can
 * be merged cleanly.
 */
class MergeOp {
  private static final Log LOG = LogFactory.getLog(MergeOp.class);

  static String mergePinName(final AnyObjectId id) {
    return mergePinName(id.name());
  }

  static String mergePinName(final String idstr) {
    return "refs/merges/" + idstr;
  }

  private final Backend server;
  private final PendingMergeResponse in;
  private final PersonIdent mergeIdent;
  private final Collection<MergeResultItem> updates;
  private final List<CodeReviewCommit> toMerge;
  private Repository db;
  private RevWalk rw;
  private CodeReviewCommit branchTip;
  private CodeReviewCommit mergeTip;
  private final List<CodeReviewCommit> newChanges;

  MergeOp(final Backend be, final PendingMergeResponse mergeInfo) {
    server = be;
    in = mergeInfo;
    mergeIdent = server.newMergeIdentity();
    updates = new ArrayList<MergeResultItem>();
    toMerge = new ArrayList<CodeReviewCommit>();
    newChanges = new ArrayList<CodeReviewCommit>();
  }

  PostMergeResultRequest merge() {
    final String loc = in.getDestProjectName() + " " + in.getDestBranchName();
    LOG.debug("Merging " + loc);
    try {
      mergeImpl();

      final PostMergeResultRequest.Builder update;
      update = PostMergeResultRequest.newBuilder();
      update.setDestBranchKey(in.getDestBranchKey());
      update.addAllChange(updates);
      return update.build();
    } catch (MergeException ee) {
      LOG.error("Error merging " + loc, ee);

      mergeTip = null;

      final PostMergeResultRequest.Builder update;
      update = PostMergeResultRequest.newBuilder();
      update.setDestBranchKey(in.getDestBranchKey());
      for (final PendingMergeItem pmi : in.getChangeList()) {
        update.addChange(suspend(pmi));
      }
      return update.build();
    }
  }

  CodeReviewCommit getMergeTip() {
    return mergeTip;
  }

  Collection<CodeReviewCommit> getNewChanges() {
    return Collections.unmodifiableCollection(newChanges);
  }

  private void mergeImpl() throws MergeException {
    openRepository();
    openBranch();
    validateChangeList();
    reduceToMinimalMerge();
    mergeTopics();
    markCleanMerges();
    pinMergeCommit();
  }

  private void openRepository() throws MergeException {
    final String name = in.getDestProjectName();
    try {
      db = server.getRepositoryCache().get(name);
    } catch (InvalidRepositoryException notGit) {
      final String m = "Repository \"" + name + "\" unknown.";
      throw new MergeException(m, notGit);
    }

    rw = new RevWalk(db) {
      @Override
      protected RevCommit createCommit(final AnyObjectId id) {
        return new CodeReviewCommit(id);
      }
    };
  }

  private void openBranch() throws MergeException {
    try {
      final RefUpdate ru = db.updateRef(in.getDestBranchName());
      if (ru.getOldObjectId() != null) {
        branchTip = (CodeReviewCommit) rw.parseCommit(ru.getOldObjectId());
      } else {
        branchTip = null;
      }
    } catch (IOException e) {
      throw new MergeException("Cannot open branch", e);
    }
  }

  private void validateChangeList() throws MergeException {
    final Set<ObjectId> tips = new HashSet<ObjectId>();
    for (final Ref r : db.getAllRefs().values()) {
      tips.add(r.getObjectId());
    }

    int commitOrder = 0;
    for (final PendingMergeItem pmi : in.getChangeList()) {
      final String idstr = pmi.getRevisionId();
      final ObjectId id;
      try {
        id = ObjectId.fromString(idstr);
      } catch (IllegalArgumentException iae) {
        throw new MergeException("Invalid ObjectId: " + idstr);
      }

      if (!tips.contains(id)) {
        // TODO Technically the proper way to do this test is to use a
        // RevWalk on "$id --not --all" and test for an empty set. But
        // that is way slower than looking for a ref directly pointing
        // at the desired tip. We should always have a ref available.
        //
        // TODO this is actually an error, the branch is gone but we
        // want to merge the issue. We can't safely do that if the
        // tip is not reachable.

        LOG.error("Cannot find branch head for " + id.name());
        updates.add(suspend(pmi));
        continue;
      }

      final CodeReviewCommit commit;
      try {
        commit = (CodeReviewCommit) rw.parseCommit(id);
      } catch (IOException e) {
        throw new MergeException("Invalid issue commit " + id, e);
      }
      commit.patchsetKey = pmi.getPatchsetKey();
      commit.originalOrder = commitOrder++;
      LOG.debug("Commit " + commit.name() + " is " + commit.patchsetKey);

      if (branchTip != null) {
        // If this commit is already merged its a bug in the queuing code
        // that we got back here. Just mark it complete and move on. Its
        // merged and that is all that mattered to the requestor.
        //
        try {
          if (rw.isMergedInto(commit, branchTip)) {
            commit.statusCode = MergeResultItem.CodeType.ALREADY_MERGED;
            updates.add(toResult(commit));
            LOG.debug("Already merged " + commit.name());
            continue;
          }
        } catch (IOException err) {
          throw new MergeException("Cannot perform merge base test", err);
        }
      }

      toMerge.add(commit);
    }
  }

  private void reduceToMinimalMerge() throws MergeException {
    final Collection<CodeReviewCommit> heads;
    try {
      heads = new MergeSorter(rw, branchTip).sort(toMerge);
    } catch (IOException e) {
      throw new MergeException("Branch head sorting failed", e);
    }

    for (final CodeReviewCommit c : toMerge) {
      if (c.statusCode != null) {
        updates.add(toResult(c));
      }
    }

    toMerge.clear();
    toMerge.addAll(heads);
    Collections.sort(toMerge, new Comparator<CodeReviewCommit>() {
      public int compare(final CodeReviewCommit a, final CodeReviewCommit b) {
        return a.originalOrder - b.originalOrder;
      }
    });
  }

  private void mergeTopics() throws MergeException {
    mergeTip = branchTip;

    // Take the first fast-forward available, if any is available in the set.
    //
    for (final Iterator<CodeReviewCommit> i = toMerge.iterator(); i.hasNext();) {
      try {
        final CodeReviewCommit n = i.next();
        if (mergeTip == null || rw.isMergedInto(mergeTip, n)) {
          mergeTip = n;
          i.remove();
          LOG.debug("Fast-forward to " + n.name());
          break;
        }
      } catch (IOException e) {
        throw new MergeException("Cannot fast-forward test during merge", e);
      }
    }

    // For every other commit do a pair-wise merge.
    //
    while (!toMerge.isEmpty()) {
      final CodeReviewCommit n = toMerge.remove(0);
      final Merger m = MergeStrategy.SIMPLE_TWO_WAY_IN_CORE.newMerger(db);
      try {
        if (m.merge(new AnyObjectId[] {mergeTip, n})) {
          writeMergeCommit(m, n);

          LOG.debug("Merged " + n.name());
        } else {
          rw.reset();
          rw.markStart(n);
          rw.markUninteresting(mergeTip);
          CodeReviewCommit failed;
          while ((failed = (CodeReviewCommit) rw.next()) != null) {
            if (failed.patchsetKey != null) {
              failed.statusCode = MergeResultItem.CodeType.PATH_CONFLICT;
              updates.add(toResult(failed));
            }
          }
          LOG.debug("Rejected (path conflict) " + n.name());
        }
      } catch (IOException e) {
        throw new MergeException("Cannot merge " + n.name(), e);
      }
    }
  }

  private void writeMergeCommit(final Merger m, final CodeReviewCommit n)
      throws IOException, MissingObjectException, IncorrectObjectTypeException {
    final Commit mergeCommit = new Commit(db);
    mergeCommit.setTreeId(m.getResultTreeId());
    mergeCommit.setParentIds(new ObjectId[] {mergeTip, n});
    mergeCommit.setAuthor(mergeIdent);
    mergeCommit.setCommitter(mergeCommit.getAuthor());
    mergeCommit.setMessage("Merge");

    final ObjectId id = m.getObjectWriter().writeCommit(mergeCommit);
    mergeTip = (CodeReviewCommit) rw.parseCommit(id);
  }

  private void markCleanMerges() throws MergeException {
    try {
      rw.reset();
      rw.sort(RevSort.REVERSE);
      rw.markStart(mergeTip);
      if (branchTip != null) {
        rw.markUninteresting(branchTip);
      } else {
        for (final Ref r : db.getAllRefs().values()) {
          if (r.getName().startsWith(Constants.R_HEADS)
              || r.getName().startsWith(Constants.R_TAGS)) {
            try {
              rw.markUninteresting(rw.parseCommit(r.getObjectId()));
            } catch (IncorrectObjectTypeException iote) {
              // Not a commit? Skip over it.
            }
          }
        }
      }

      CodeReviewCommit c;
      while ((c = (CodeReviewCommit) rw.next()) != null) {
        if (c.patchsetKey != null) {
          c.statusCode = MergeResultItem.CodeType.CLEAN_MERGE;
          updates.add(toResult(c));
          newChanges.add(c);
        }
      }
    } catch (IOException e) {
      throw new MergeException("Cannot mark clean merges", e);
    }
  }

  private void pinMergeCommit() throws MergeException {
    final String name = mergePinName(mergeTip.getId());
    final RefUpdate.Result r;
    try {
      final RefUpdate u = db.updateRef(name);
      u.setNewObjectId(mergeTip.getId());
      u.setRefLogMessage("Merged submit queue", false);
      r = u.update();
    } catch (IOException err) {
      final String m = "Failure creating " + name;
      throw new MergeException(m, err);
    }

    if (r == RefUpdate.Result.NEW) {
    } else if (r == RefUpdate.Result.FAST_FORWARD) {
    } else if (r == RefUpdate.Result.FORCED) {
    } else if (r == RefUpdate.Result.NO_CHANGE) {
    } else {
      final String m = "Failure creating " + name + ": " + r.name();
      throw new MergeException(m);
    }
  }

  private static MergeResultItem suspend(final PendingMergeItem pmi) {
    final MergeResultItem.Builder delay = MergeResultItem.newBuilder();
    delay.setStatusCode(MergeResultItem.CodeType.MISSING_DEPENDENCY);
    delay.setPatchsetKey(pmi.getPatchsetKey());
    return delay.build();
  }

  private static MergeResultItem toResult(final CodeReviewCommit c) {
    final MergeResultItem.Builder delay = MergeResultItem.newBuilder();
    delay.setStatusCode(c.statusCode);
    delay.setPatchsetKey(c.patchsetKey);

    if (c.statusCode == MergeResultItem.CodeType.MISSING_DEPENDENCY) {
      for (final CodeReviewCommit m : c.missing) {
        final MissingDependencyItem.Builder d;

        d = MissingDependencyItem.newBuilder();
        if (m.patchsetKey != null) {
          d.setPatchsetKey(m.patchsetKey);
        } else {
          d.setRevisionId(m.getId().name());
        }
        delay.addMissing(d);
      }
    }

    return delay.build();
  }
}
