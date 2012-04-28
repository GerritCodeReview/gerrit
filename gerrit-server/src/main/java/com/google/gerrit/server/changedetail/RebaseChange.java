// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.changedetail;

import com.google.gerrit.common.ChangeHookRunner;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetAncestor;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.client.Change.Status;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.mail.EmailException;
import com.google.gerrit.server.mail.RebasedPatchSetSender;
import com.google.gerrit.server.mail.ReplacePatchSetSender;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtjsonrpc.common.VoidResult;
import com.google.gwtorm.server.AtomicUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.merge.ThreeWayMerger;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.kohsuke.args4j.Argument;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

public class RebaseChange implements Callable<VoidResult> {

  private final ChangeControl.Factory changeControlFactory;
  private final ReviewDb db;
  private final IdentifiedUser currentUser;
  private final ChangeHookRunner hooks;
  private final RebasedPatchSetSender.Factory rebasedPatchSetSenderFactory;
  private final GitRepositoryManager gitManager;
  private final PatchSetInfoFactory patchSetInfoFactory;
  private final GitReferenceUpdated replication;
  private final PersonIdent myIdent;
  private final ApprovalsUtil approvalsUtil;

  @Argument(index = 0, required = true, multiValued = false, usage = "patch set to rebase")
  private PatchSet.Id patchSetId;

  public void setPatchSetId(final PatchSet.Id patchSetId) {
    this.patchSetId = patchSetId;
  }

  @Inject
  RebaseChange(final ChangeControl.Factory changeControlFactory,
      final ReviewDb db, final IdentifiedUser currentUser,
      final ChangeHookRunner hooks,
      final RebasedPatchSetSender.Factory rebasedPatchSetSenderFactory,
      final GitRepositoryManager gitManager,
      final PatchSetInfoFactory patchSetInfoFactory,
      final GitReferenceUpdated replication,
      @GerritPersonIdent final PersonIdent myIdent,
      final ApprovalsUtil approvalsUtil) {
    this.changeControlFactory = changeControlFactory;
    this.db = db;
    this.currentUser = currentUser;
    this.hooks = hooks;
    this.rebasedPatchSetSenderFactory = rebasedPatchSetSenderFactory;
    this.gitManager = gitManager;
    this.patchSetInfoFactory = patchSetInfoFactory;
    this.replication = replication;
    this.myIdent = myIdent;
    this.approvalsUtil = approvalsUtil;

    patchSetId = null;
  }

  @Override
  public VoidResult call() throws NoSuchChangeException,
      EmailException, OrmException, MissingObjectException,
      IncorrectObjectTypeException, IOException,
      PatchSetInfoNotAvailableException, InvalidChangeOperationException{

    rebaseChange(patchSetId, currentUser, db,
        rebasedPatchSetSenderFactory, hooks, gitManager, patchSetInfoFactory,
        replication, myIdent, changeControlFactory, approvalsUtil);

    return VoidResult.INSTANCE;
  }

  public static void rebaseChange(final PatchSet.Id patchSetId,
      final IdentifiedUser user, final ReviewDb db,
      RebasedPatchSetSender.Factory rebasedPatchSetSenderFactory,
      final ChangeHookRunner hooks, GitRepositoryManager gitManager,
      final PatchSetInfoFactory patchSetInfoFactory,
      final GitReferenceUpdated replication, PersonIdent myIdent,
      final ChangeControl.Factory changeControlFactory,
      final ApprovalsUtil approvalsUtil) throws NoSuchChangeException,
      EmailException, OrmException, MissingObjectException,
      IncorrectObjectTypeException, IOException,
      PatchSetInfoNotAvailableException, InvalidChangeOperationException {

    final Change.Id changeId = patchSetId.getParentKey();
    final ChangeControl changeControl =
        changeControlFactory.validateFor(changeId);

    if (!changeControl.canRebase()) {
      throw new InvalidChangeOperationException(
          "Cannot rebase: New patch sets are not allowed to be added to change: "
              + changeId.toString());
    }

    Change change = changeControl.getChange();
    final Repository git = gitManager.openRepository(change.getProject());
    try {
      final RevWalk revWalk = new RevWalk(git);
      try {
        final PatchSet originalPatchSet = db.patchSets().get(patchSetId);

        final String rebasedOnRev = getRebaseRevision(patchSetId, db, change,
            git, null, null, null);
        RevCommit branchTipCommit = revWalk.parseCommit(ObjectId.fromString(rebasedOnRev));

        final RevCommit originalCommit =
            revWalk.parseCommit(ObjectId.fromString(originalPatchSet
                .getRevision().get()));

        CommitBuilder rebasedCommitBuilder =
            rebaseCommit(git, originalCommit, branchTipCommit, myIdent);

        final ObjectInserter oi = git.newObjectInserter();
        final ObjectId rebasedCommitId;
        try {
          rebasedCommitId = oi.insert(rebasedCommitBuilder);
          oi.flush();
        } finally {
          oi.release();
        }

        Change updatedChange =
            db.changes().atomicUpdate(changeId, new AtomicUpdate<Change>() {
              @Override
              public Change update(Change change) {
                if (change.getStatus().isOpen()) {
                  change.nextPatchSetId();
                  return change;
                } else {
                  return null;
                }
              }
            });

        if (updatedChange == null) {
          throw new InvalidChangeOperationException("Change is closed: "
              + change.toString());
        } else {
          change = updatedChange;
        }

        final PatchSet rebasedPatchSet = new PatchSet(change.currPatchSetId());
        rebasedPatchSet.setCreatedOn(change.getCreatedOn());
        rebasedPatchSet.setUploader(user.getAccountId());
        rebasedPatchSet.setRevision(new RevId(rebasedCommitId.getName()));

        ChangeUtil.insertAncestors(db, rebasedPatchSet.getId(),
            revWalk.parseCommit(rebasedCommitId));

        db.patchSets().insert(Collections.singleton(rebasedPatchSet));
        final PatchSetInfo info =
            patchSetInfoFactory.get(db, rebasedPatchSet.getId());

        change =
            db.changes().atomicUpdate(change.getId(),
                new AtomicUpdate<Change>() {
                  @Override
                  public Change update(Change change) {
                    change.setCurrentPatchSet(info);
                    ChangeUtil.updated(change);
                    return change;
                  }
                });

        final RefUpdate ru = git.updateRef(rebasedPatchSet.getRefName());
        ru.setNewObjectId(rebasedCommitId);
        ru.disableRefLog();
        if (ru.update(revWalk) != RefUpdate.Result.NEW) {
          throw new IOException("Failed to create ref "
              + rebasedPatchSet.getRefName() + " in " + git.getDirectory()
              + ": " + ru.getResult());
        }

        replication.fire(change.getProject(), ru.getName());

        List<PatchSetApproval> patchSetApprovals = approvalsUtil.copyVetosToLatestPatchSet(change);

        final Set<Account.Id> oldReviewers = new HashSet<Account.Id>();
        final Set<Account.Id> oldCC = new HashSet<Account.Id>();

        for (PatchSetApproval a : patchSetApprovals) {
          if (a.getValue() != 0) {
            oldReviewers.add(a.getAccountId());
          } else {
            oldCC.add(a.getAccountId());
          }
        }

        final ChangeMessage cmsg =
            new ChangeMessage(new ChangeMessage.Key(changeId,
                ChangeUtil.messageUUID(db)), user.getAccountId(), patchSetId);
        cmsg.setMessage("Patch Set " + patchSetId.get() + ": Rebased");
        db.changeMessages().insert(Collections.singleton(cmsg));

        final ReplacePatchSetSender cm =
            rebasedPatchSetSenderFactory.create(change);
        cm.setFrom(user.getAccountId());
        cm.setPatchSet(rebasedPatchSet);
        cm.addReviewers(oldReviewers);
        cm.addExtraCC(oldCC);
        cm.send();

        hooks.doPatchsetCreatedHook(change, rebasedPatchSet, db);
      } finally {
        revWalk.release();
      }
    } finally {
      git.close();
    }
  }

  /**
   * Rebases a commit
   *
   * @param git Repository to find commits in
   * @param original The commit to rebase
   * @param base Base to rebase against
   * @return CommitBuilder the newly rebased commit
   * @throws IOException Merged failed
   */
  public static CommitBuilder rebaseCommit(Repository git, RevCommit original,
      RevCommit base, PersonIdent committerIdent) throws IOException {

    final RevCommit parentCommit = original.getParent(0);

    if (base.equals(parentCommit)) {
      throw new IOException("Change is already up to date.");
    }

    final ThreeWayMerger merger = MergeStrategy.RESOLVE.newMerger(git, true);
    merger.setBase(parentCommit);
    merger.merge(original, base);

    if (merger.getResultTreeId() == null) {
      throw new IOException(
          "The rebase failed since conflicts occured during the merge.");
    }

    final CommitBuilder rebasedCommitBuilder = new CommitBuilder();

    rebasedCommitBuilder.setTreeId(merger.getResultTreeId());
    rebasedCommitBuilder.setParentId(base);
    rebasedCommitBuilder.setAuthor(original.getAuthorIdent());
    rebasedCommitBuilder.setMessage(original.getFullMessage());
    rebasedCommitBuilder.setCommitter(committerIdent);

    return rebasedCommitBuilder;
  }

  public static boolean canDoRebase(ReviewDb db,
      Change change, GitRepositoryManager gitManager,
      List<PatchSetAncestor> patchSetAncestors,
      List<PatchSet> depPatchSetList, List<Change> depChangeList)
      throws OrmException, RepositoryNotFoundException, IOException {

    if (patchSetAncestors != null && patchSetAncestors.size() > 0) {
      if (!change.currentPatchSetId().equals(
          patchSetAncestors.get(0).getPatchSet())) {
        patchSetAncestors = null;
        depPatchSetList = null;
        depChangeList = null;
      }
    }

    final Repository git = gitManager.openRepository(change.getProject());

    try {
      // If no exception is thrown, then we can do a rebase.
      getRebaseRevision(change.currentPatchSetId(), db, change, git,
          patchSetAncestors, depPatchSetList, depChangeList);
      return true;
    } catch (IOException e) {
      return false;
    } finally {
      git.close();
    }
  }

  private static String getRebaseRevision(PatchSet.Id patchSetId, ReviewDb db,
      Change change, Repository git, List<PatchSetAncestor> patchSetAncestors,
      List<PatchSet> depPatchSetList, List<Change> depChangeList)
      throws IOException, OrmException {

    String rev = null;

    if (patchSetAncestors == null) {
      patchSetAncestors =
          db.patchSetAncestors().ancestorsOf(patchSetId).toList();
    }

    if (patchSetAncestors.size() > 1) {
      throw new IOException(
          "Cannot rebase a change with multiple parents. Parents commits: "
              + patchSetAncestors.toString());
    }

    if (patchSetAncestors.size() == 0) {
      throw new IOException(
          "Cannot rebase a change without any parents (is this the initial commit?).");
    }

    RevId ancestorRev = patchSetAncestors.get(0).getAncestorRevision();
    if (depPatchSetList == null || depPatchSetList.size() != 1 ||
        !depPatchSetList.get(0).getRevision().equals(ancestorRev)) {
      depPatchSetList = db.patchSets().byRevision(ancestorRev).toList();
    }

    if (!depPatchSetList.isEmpty()) {
      PatchSet depPatchSet = depPatchSetList.get(0);

      Change.Id depChangeId = depPatchSet.getId().getParentKey();
      Change depChange;
      if (depChangeList == null || depChangeList.size() != 1 ||
          !depChangeList.get(0).getId().equals(depChangeId)) {
        depChange = db.changes().get(depChangeId);
      } else {
        depChange = depChangeList.get(0);
      }

      if (depChange.getStatus() == Status.ABANDONED) {
        throw new IOException("Cannot rebase a change with an abandoned parent: "
            + depChange.getKey().toString());
      }

      if (depChange.getStatus().isOpen()) {
        if (depPatchSet.getId().equals(depChange.currentPatchSetId())) {
          throw new IOException(
              "Change is already based on the latest patch set of the dependent change.");
        }
        PatchSet latestDepPatchSet =
            db.patchSets().get(depChange.currentPatchSetId());
        rev = latestDepPatchSet.getRevision().get();
      }
    }

    if (rev == null) {
      // We are dependent on a merged PatchSet or have no PatchSet
      // dependencies at all.
      Ref destRef = git.getRef(change.getDest().get());
      if (destRef == null) {
        throw new IOException(
            "The destination branch does not exist: "
                + change.getDest().get());
      }
      rev = destRef.getObjectId().getName();
      if (rev.equals(ancestorRev.get())) {
        throw new IOException("Change is already up to date.");
      }
    }
    return rev;
  }
}
