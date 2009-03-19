// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.git;

import com.google.gerrit.client.data.ApprovalType;
import com.google.gerrit.client.reviewdb.ApprovalCategory;
import com.google.gerrit.client.reviewdb.Branch;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.ChangeApproval;
import com.google.gerrit.client.reviewdb.ChangeMessage;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.rpc.Common;
import com.google.gerrit.client.workflow.FunctionState;
import com.google.gerrit.server.ChangeMail;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.GerritServer;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.Transaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.mail.MessagingException;

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
public class MergeOp {
  private static final Logger log =
    LoggerFactory.getLogger(MergeOp.class);

  private final GerritServer server;
  private final PersonIdent myIdent;
  private final Branch.NameKey destBranch;
  private final List<CodeReviewCommit> toMerge;
  private List<Change> submitted;
  private final Map<Change.Id, CommitMergeStatus> status;
  private ReviewDb schema;
  private Repository db;
  private RevWalk rw;
  private CodeReviewCommit branchTip;
  private CodeReviewCommit mergeTip;
  private RefUpdate branchUpdate;

  public MergeOp(final GerritServer gs, final Branch.NameKey branch) {
    server = gs;
    myIdent = server.newGerritPersonIdent();
    destBranch = branch;
    toMerge = new ArrayList<CodeReviewCommit>();
    status = new HashMap<Change.Id, CommitMergeStatus>();
  }

  public void merge() throws MergeException {
    try {
      schema = Common.getSchemaFactory().open();
    } catch (OrmException e) {
      throw new MergeException("Cannot open database", e);
    }
    try {
      mergeImpl();
    } finally {
      schema.close();
      schema = null;
    }
  }

  private void mergeImpl() throws MergeException {
    openRepository();
    openBranch();
    listPendingSubmits();
    validateChangeList();
    reduceToMinimalMerge();
    mergeTopics();
    markCleanMerges();
    updateBranch();
    updateChangeStatus();
  }

  private void openRepository() throws MergeException {
    final String name = destBranch.getParentKey().get();
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
    rw.sort(RevSort.TOPO);
  }

  private void openBranch() throws MergeException {
    try {
      branchUpdate = db.updateRef(destBranch.get());
      if (branchUpdate.getOldObjectId() != null) {
        branchTip =
            (CodeReviewCommit) rw.parseCommit(branchUpdate.getOldObjectId());
      } else {
        branchTip = null;
      }
    } catch (IOException e) {
      throw new MergeException("Cannot open branch", e);
    }
  }

  private void listPendingSubmits() throws MergeException {
    try {
      submitted = schema.changes().submitted(destBranch).toList();
    } catch (OrmException e) {
      throw new MergeException("Cannot query the database", e);
    }
  }

  private void validateChangeList() throws MergeException {
    final Set<ObjectId> tips = new HashSet<ObjectId>();
    for (final Ref r : db.getAllRefs().values()) {
      tips.add(r.getObjectId());
    }

    int commitOrder = 0;
    for (final Change chg : submitted) {
      if (chg.currentPatchSetId() == null) {
        status.put(chg.getId(), CommitMergeStatus.NO_PATCH_SET);
        continue;
      }

      final PatchSet ps;
      try {
        ps = schema.patchSets().get(chg.currentPatchSetId());
      } catch (OrmException e) {
        throw new MergeException("Cannot query the database", e);
      }
      if (ps == null || ps.getRevision() == null
          || ps.getRevision().get() == null) {
        status.put(chg.getId(), CommitMergeStatus.NO_PATCH_SET);
        continue;
      }

      final String idstr = ps.getRevision().get();
      final ObjectId id;
      try {
        id = ObjectId.fromString(idstr);
      } catch (IllegalArgumentException iae) {
        status.put(chg.getId(), CommitMergeStatus.NO_PATCH_SET);
        continue;
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
        //
        status.put(chg.getId(), CommitMergeStatus.REVISION_GONE);
        continue;
      }

      final CodeReviewCommit commit;
      try {
        commit = (CodeReviewCommit) rw.parseCommit(id);
      } catch (IOException e) {
        throw new MergeException("Invalid issue commit " + id, e);
      }
      commit.patchsetId = ps.getId();
      commit.originalOrder = commitOrder++;

      if (branchTip != null) {
        // If this commit is already merged its a bug in the queuing code
        // that we got back here. Just mark it complete and move on. Its
        // merged and that is all that mattered to the requestor.
        //
        try {
          if (rw.isMergedInto(commit, branchTip)) {
            commit.statusCode = CommitMergeStatus.ALREADY_MERGED;
            status.put(chg.getId(), commit.statusCode);
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
        status.put(c.patchsetId.getParentKey(), c.statusCode);
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
          break;
        }
      } catch (IOException e) {
        throw new MergeException("Cannot fast-forward test during merge", e);
      }
    }

    // If this project only permits fast-forwards, abort everything else.
    //
    if ("true".equals(db.getConfig().getString("gerrit", null,
        "fastforwardonly"))) {
      while (!toMerge.isEmpty()) {
        final CodeReviewCommit n = toMerge.remove(0);
        n.statusCode = CommitMergeStatus.PATH_CONFLICT;
        status.put(n.patchsetId.getParentKey(), n.statusCode);
      }
      return;
    }

    // For every other commit do a pair-wise merge.
    //
    while (!toMerge.isEmpty()) {
      final CodeReviewCommit n = toMerge.remove(0);
      final Merger m = MergeStrategy.SIMPLE_TWO_WAY_IN_CORE.newMerger(db);
      try {
        if (m.merge(new AnyObjectId[] {mergeTip, n})) {
          writeMergeCommit(m, n);

        } else {
          rw.reset();
          rw.markStart(n);
          rw.markUninteresting(mergeTip);
          CodeReviewCommit failed;
          while ((failed = (CodeReviewCommit) rw.next()) != null) {
            if (failed.patchsetId == null) {
              continue;
            }

            failed.statusCode = CommitMergeStatus.PATH_CONFLICT;
            status.put(failed.patchsetId.getParentKey(), failed.statusCode);
          }
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
    mergeCommit.setAuthor(myIdent);
    mergeCommit.setCommitter(mergeCommit.getAuthor());
    mergeCommit.setMessage("Merge");

    final ObjectId id = m.getObjectWriter().writeCommit(mergeCommit);
    mergeTip = (CodeReviewCommit) rw.parseCommit(id);
  }

  private void markCleanMerges() throws MergeException {
    try {
      rw.reset();
      rw.sort(RevSort.TOPO);
      rw.sort(RevSort.REVERSE, true);
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
        if (c.patchsetId != null) {
          c.statusCode = CommitMergeStatus.CLEAN_MERGE;
          status.put(c.patchsetId.getParentKey(), c.statusCode);
        }
      }
    } catch (IOException e) {
      throw new MergeException("Cannot mark clean merges", e);
    }
  }

  private void updateBranch() throws MergeException {
    if (branchTip == null || branchTip != mergeTip) {
      branchUpdate.setForceUpdate(false);
      branchUpdate.setNewObjectId(mergeTip);
      branchUpdate.setRefLogMessage("merged", true);
      try {
        switch (branchUpdate.update(rw)) {
          case NEW:
          case FAST_FORWARD:
            PushQueue.scheduleUpdate(destBranch.getParentKey(), branchUpdate
                .getName());
            break;

          default:
            throw new IOException(branchUpdate.getResult().name());
        }
      } catch (IOException e) {
        throw new MergeException("Cannot update " + branchUpdate.getName(), e);
      }
    }
  }

  private void updateChangeStatus() {
    for (final Change c : submitted) {
      final CommitMergeStatus s = status.get(c.getId());
      if (s == null) {
        // Shouldn't ever happen, but leave the change alone. We'll pick
        // it up on the next pass.
        //
        continue;
      }

      switch (s) {
        case CLEAN_MERGE: {
          final String txt =
              "Change has been successfully merged into the git repository.";
          setMerged(c, message(c, txt));
          break;
        }

        case ALREADY_MERGED:
          setMerged(c, null);
          break;

        case PATH_CONFLICT: {
          final String txt =
              "Your change could not be merged due to a path conflict.\n"
                  + "\n"
                  + "Please merge (or rebase) the change locally and upload the resolution for review.";
          setNew(c, message(c, txt));
          break;
        }

        case MISSING_DEPENDENCY: {
          try {
            final String txt =
                "Change could not be merged because of a missing dependency.  As soon as its dependencies are submitted, the change will be submitted.";
            final List<ChangeMessage> msgList =
                schema.changeMessages().byChange(c.getId()).toList();
            if (msgList.size() > 0) {
              final ChangeMessage last = msgList.get(msgList.size() - 1);
              if (last.getAuthor() == null && txt.equals(last.getMessage())) {
                // The last message was written by us, and it said this
                // same message already. Its unlikely anything has changed
                // that would cause us to need to repeat ourselves.
                //
                break;
              }
            }

            schema.changeMessages().insert(
                Collections.singleton(message(c, txt)));
          } catch (OrmException e) {
          }
          break;
        }

        default:
          setNew(c, message(c, "Unspecified merge failure: " + s.name()));
          break;
      }
    }
  }

  private ChangeMessage message(final Change c, final String body) {
    final String uuid;
    try {
      uuid = ChangeUtil.messageUUID(schema);
    } catch (OrmException e) {
      return null;
    }
    final ChangeMessage m =
        new ChangeMessage(new ChangeMessage.Key(c.getId(), uuid), null);
    m.setMessage(body);
    return m;
  }

  private void setMerged(Change c, ChangeMessage msg) {
    final PatchSet.Id merged = c.currentPatchSetId();
    ChangeApproval submitter=null;
    for (int attempts = 0; attempts < 10; attempts++) {
      c.setStatus(Change.Status.MERGED);
      ChangeUtil.updated(c);
      try {
        final Transaction txn = schema.beginTransaction();

        // Flatten out all existing approvals based upon the current
        // permissions. Once the change is closed the approvals are
        // not updated at presentation view time, so we need to make.
        // sure they are accurate now. This way if permissions get
        // modified in the future, historical records stay accurate.
        //
        final List<ChangeApproval> approvals =
            schema.changeApprovals().byChange(c.getId()).toList();
        final FunctionState fs = new FunctionState(c, approvals);
        for (ApprovalType at : Common.getGerritConfig().getApprovalTypes()) {
          at.getCategory().getFunction().run(at, fs);
        }
        for (ChangeApproval a : approvals) {
          if (ApprovalCategory.SUBMIT.equals(a.getCategoryId())) {
            if (submitter == null
                || a.getGranted().compareTo(submitter.getGranted()) > 0) {
              submitter = a;
            }
          }
          a.cache(c);
        }
        schema.changeApprovals().update(approvals, txn);

        if (msg != null) {
          schema.changeMessages().insert(Collections.singleton(msg), txn);
        }
        schema.changes().update(Collections.singleton(c), txn);
        txn.commit();
        break;
      } catch (OrmException e) {
        try {
          c = schema.changes().get(c.getId());
          if (!merged.equals(c.currentPatchSetId())) {
            // Uncool; the patch set changed after we merged it.
            // Go back to the patch set that was actually merged.
            //
            c.setCurrentPatchSet(schema.patchSetInfo().get(merged));
          }
        } catch (OrmException e2) {
        }
      }
    }

    try {
      final ChangeMail cm = new ChangeMail(server, c);
      if (submitter != null) {
        cm.setFrom(submitter.getAccountId());
      }
      cm.setReviewDb(schema);
      cm.setPatchSet(schema.patchSets().get(c.currentPatchSetId()), schema
          .patchSetInfo().get(c.currentPatchSetId()));
      cm.sendMerged();
    } catch (OrmException e){
      log.error("Cannot send email for submitted patch set " + c.getId(), e);
    } catch (MessagingException e){
      log.error("Cannot send email for submitted patch set " + c.getId(), e);
    }
  }

  private void setNew(Change c, ChangeMessage msg) {
    for (int attempts = 0; attempts < 10; attempts++) {
      c.setStatus(Change.Status.NEW);
      ChangeUtil.updated(c);
      try {
        final Transaction txn = schema.beginTransaction();
        schema.changes().update(Collections.singleton(c), txn);
        if (msg != null) {
          schema.changeMessages().insert(Collections.singleton(msg), txn);
        }
        txn.commit();
        break;
      } catch (OrmException e) {
        try {
          c = schema.changes().get(c.getId());
          if (c.getStatus().isClosed()) {
            // Someone else marked it close while we noticed a failure.
            // That's fine, leave it closed.
            //
            break;
          }
        } catch (OrmException e2) {
        }
      }
    }
  }
}
