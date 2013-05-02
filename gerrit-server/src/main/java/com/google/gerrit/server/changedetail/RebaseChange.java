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

import com.google.common.collect.Sets;
import com.google.gerrit.common.ChangeHookRunner;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.common.errors.EmailException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Change.Status;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetAncestor;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.mail.RebasedPatchSetSender;
import com.google.gerrit.server.mail.ReplacePatchSetSender;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gwtorm.server.AtomicUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.ThreeWayMerger;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class RebaseChange {
  private final ChangeControl.Factory changeControlFactory;
  private final PatchSetInfoFactory patchSetInfoFactory;
  private final ReviewDb db;
  private final GitRepositoryManager gitManager;
  private final PersonIdent myIdent;
  private final GitReferenceUpdated gitRefUpdated;
  private final RebasedPatchSetSender.Factory rebasedPatchSetSenderFactory;
  private final ChangeHookRunner hooks;
  private final ApprovalsUtil approvalsUtil;
  private final MergeUtil.Factory mergeUtilFactory;
  private final ProjectCache projectCache;

  @Inject
  RebaseChange(final ChangeControl.Factory changeControlFactory,
      final PatchSetInfoFactory patchSetInfoFactory, final ReviewDb db,
      @GerritPersonIdent final PersonIdent myIdent,
      final GitRepositoryManager gitManager,
      final GitReferenceUpdated gitRefUpdated,
      final RebasedPatchSetSender.Factory rebasedPatchSetSenderFactory,
      final ChangeHookRunner hooks, final ApprovalsUtil approvalsUtil,
      final MergeUtil.Factory mergeUtilFactory,
      final ProjectCache projectCache) {
    this.changeControlFactory = changeControlFactory;
    this.patchSetInfoFactory = patchSetInfoFactory;
    this.db = db;
    this.gitManager = gitManager;
    this.myIdent = myIdent;
    this.gitRefUpdated = gitRefUpdated;
    this.rebasedPatchSetSenderFactory = rebasedPatchSetSenderFactory;
    this.hooks = hooks;
    this.approvalsUtil = approvalsUtil;
    this.mergeUtilFactory = mergeUtilFactory;
    this.projectCache = projectCache;
  }

  /**
   * Rebases the change of the given patch set.
   *
   * It is verified that the current user is allowed to do the rebase.
   *
   * If the patch set has no dependency to an open change, then the change is
   * rebased on the tip of the destination branch.
   *
   * If the patch set depends on an open change, it is rebased on the latest
   * patch set of this change.
   *
   * The rebased commit is added as new patch set to the change.
   *
   * E-mail notification and triggering of hooks happens for the creation of the
   * new patch set.
   *
   * @param patchSetId the id of the patch set
   * @param uploader the user that creates the rebased patch set
   * @throws NoSuchChangeException thrown if the change to which the patch set
   *         belongs does not exist or is not visible to the user
   * @throws EmailException thrown if sending the e-mail to notify about the new
   *         patch set fails
   * @throws OrmException thrown in case accessing the database fails
   * @throws IOException thrown if rebase is not possible or not needed
   * @throws InvalidChangeOperationException thrown if rebase is not allowed
   */
  public void rebase(final PatchSet.Id patchSetId, final Account.Id uploader)
      throws NoSuchChangeException, EmailException, OrmException, IOException,
      InvalidChangeOperationException {
    final Change.Id changeId = patchSetId.getParentKey();
    final ChangeControl changeControl =
        changeControlFactory.validateFor(changeId);
    if (!changeControl.canRebase()) {
      throw new InvalidChangeOperationException(
          "Cannot rebase: New patch sets are not allowed to be added to change: "
              + changeId.toString());
    }
    final Change change = changeControl.getChange();
    Repository git = null;
    RevWalk rw = null;
    ObjectInserter inserter = null;
    try {
      git = gitManager.openRepository(change.getProject());
      rw = new RevWalk(git);
      inserter = git.newObjectInserter();

      final List<PatchSetApproval> oldPatchSetApprovals =
          db.patchSetApprovals().byChange(change.getId()).toList();

      final String baseRev = findBaseRevision(patchSetId, db,
          change.getDest(), git, null, null, null);
      final RevCommit baseCommit =
          rw.parseCommit(ObjectId.fromString(baseRev));

      final PatchSet newPatchSet =
          rebase(git, rw, inserter, patchSetId, change, uploader, baseCommit,
              mergeUtilFactory.create(
                  changeControl.getProjectControl().getProjectState(), true));

      final Set<Account.Id> oldReviewers = Sets.newHashSet();
      final Set<Account.Id> oldCC = Sets.newHashSet();
      for (PatchSetApproval a : oldPatchSetApprovals) {
        if (a.getValue() != 0) {
          oldReviewers.add(a.getAccountId());
        } else {
          oldCC.add(a.getAccountId());
        }
      }
      final ReplacePatchSetSender cm =
          rebasedPatchSetSenderFactory.create(change);
      cm.setFrom(uploader);
      cm.setPatchSet(newPatchSet);
      cm.addReviewers(oldReviewers);
      cm.addExtraCC(oldCC);
      cm.send();

      hooks.doPatchsetCreatedHook(change, newPatchSet, db);
    } catch (PathConflictException e) {
      throw new IOException(e.getMessage());
    } finally {
      if (inserter != null) {
        inserter.release();
      }
      if (rw != null) {
        rw.release();
      }
      if (git != null) {
        git.close();
      }
    }
  }

  /**
   * Finds the revision of commit on which the given patch set should be based.
   *
   * @param patchSetId the id of the patch set for which the new base commit
   *        should be found
   * @param db the ReviewDb
   * @param destBranch the destination branch
   * @param git the repository
   * @param patchSetAncestors the original PatchSetAncestor of the given patch
   *        set that should be based
   * @param depPatchSetList the original patch set list on which the rebased
   *        patch set depends
   * @param depChangeList the original change list on whose patch set the
   *        rebased patch set depends
   * @return the revision of commit on which the given patch set should be based
   * @throws IOException thrown if rebase is not possible or not needed
   * @throws OrmException thrown in case accessing the database fails
   */
    private static String findBaseRevision(final PatchSet.Id patchSetId,
        final ReviewDb db, final Branch.NameKey destBranch, final Repository git,
        List<PatchSetAncestor> patchSetAncestors, List<PatchSet> depPatchSetList,
        List<Change> depChangeList) throws IOException, OrmException {

      String baseRev = null;

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
          baseRev = latestDepPatchSet.getRevision().get();
        }
      }

      if (baseRev == null) {
        // We are dependent on a merged PatchSet or have no PatchSet
        // dependencies at all.
        Ref destRef = git.getRef(destBranch.get());
        if (destRef == null) {
          throw new IOException(
              "The destination branch does not exist: "
                  + destBranch.get());
        }
        baseRev = destRef.getObjectId().getName();
        if (baseRev.equals(ancestorRev.get())) {
          throw new IOException("Change is already up to date.");
        }
      }
      return baseRev;
    }

  /**
   * Rebases the change of the given patch set on the given base commit.
   *
   * The rebased commit is added as new patch set to the change.
   *
   * E-mail notification and triggering of hooks is NOT done for the creation of
   * the new patch set.
   *
   * @param git the repository
   * @param revWalk the RevWalk
   * @param inserter the object inserter
   * @param patchSetId the id of the patch set
   * @param chg the change that should be rebased
   * @param uploader the user that creates the rebased patch set
   * @param baseCommit the commit that should be the new base
   * @param mergeUtil merge utilities for the destination project
   * @return the new patch set which is based on the given base commit
   * @throws NoSuchChangeException thrown if the change to which the patch set
   *         belongs does not exist or is not visible to the user
   * @throws OrmException thrown in case accessing the database fails
   * @throws IOException thrown if rebase is not possible or not needed
   * @throws InvalidChangeOperationException thrown if rebase is not allowed
   */
  public PatchSet rebase(final Repository git, final RevWalk revWalk,
      final ObjectInserter inserter, final PatchSet.Id patchSetId,
      final Change chg, final Account.Id uploader, final RevCommit baseCommit,
      final MergeUtil mergeUtil) throws NoSuchChangeException,
      OrmException, IOException, InvalidChangeOperationException,
      PathConflictException {
    Change change = chg;
    final PatchSet originalPatchSet = db.patchSets().get(patchSetId);

    final RevCommit rebasedCommit;
    ObjectId oldId = ObjectId.fromString(originalPatchSet.getRevision().get());
    ObjectId newId = rebaseCommit(git, inserter, revWalk.parseCommit(oldId),
        baseCommit, mergeUtil, myIdent);

    rebasedCommit = revWalk.parseCommit(newId);

    PatchSet.Id id = ChangeUtil.nextPatchSetId(git, change.currentPatchSetId());
    final PatchSet newPatchSet = new PatchSet(id);
    newPatchSet.setCreatedOn(new Timestamp(System.currentTimeMillis()));
    newPatchSet.setUploader(uploader);
    newPatchSet.setRevision(new RevId(rebasedCommit.name()));
    newPatchSet.setDraft(originalPatchSet.isDraft());

    final PatchSetInfo info =
        patchSetInfoFactory.get(rebasedCommit, newPatchSet.getId());

    final RefUpdate ru = git.updateRef(newPatchSet.getRefName());
    ru.setExpectedOldObjectId(ObjectId.zeroId());
    ru.setNewObjectId(rebasedCommit);
    ru.disableRefLog();
    if (ru.update(revWalk) != RefUpdate.Result.NEW) {
      throw new IOException(String.format("Failed to create ref %s in %s: %s",
          newPatchSet.getRefName(), change.getDest().getParentKey().get(),
          ru.getResult()));
    }
    gitRefUpdated.fire(change.getProject(), ru);

    db.changes().beginTransaction(change.getId());
    try {
      Change updatedChange = db.changes().get(change.getId());
      if (updatedChange != null && change.getStatus().isOpen()) {
        change = updatedChange;
      } else {
        throw new InvalidChangeOperationException(String.format(
            "Change %s is closed", change.getId()));
      }

      ChangeUtil.insertAncestors(db, newPatchSet.getId(), rebasedCommit);
      db.patchSets().insert(Collections.singleton(newPatchSet));
      updatedChange =
          db.changes().atomicUpdate(change.getId(), new AtomicUpdate<Change>() {
            @Override
            public Change update(Change change) {
              if (change.getStatus().isClosed()) {
                return null;
              }
              if (!change.currentPatchSetId().equals(patchSetId)) {
                return null;
              }
              if (change.getStatus() != Change.Status.DRAFT) {
                change.setStatus(Change.Status.NEW);
              }
              change.setLastSha1MergeTested(null);
              change.setCurrentPatchSet(info);
              ChangeUtil.updated(change);
              return change;
            }
          });
      if (updatedChange != null) {
        change = updatedChange;
      } else {
        throw new InvalidChangeOperationException(String.format(
            "Change %s was modified", change.getId()));
      }

      final LabelTypes labelTypes =
          projectCache.get(change.getProject()).getLabelTypes();
      approvalsUtil.copyVetosToPatchSet(db, labelTypes,
          change.currentPatchSetId());

      final ChangeMessage cmsg =
          new ChangeMessage(new ChangeMessage.Key(change.getId(),
              ChangeUtil.messageUUID(db)), uploader, patchSetId);
      cmsg.setMessage("Patch Set " + change.currentPatchSetId().get()
          + ": Patch Set " + patchSetId.get() + " was rebased");
      db.changeMessages().insert(Collections.singleton(cmsg));
      db.commit();
    } finally {
      db.rollback();
    }

    return newPatchSet;
  }

  /**
   * Rebases a commit.
   *
   * @param git repository to find commits in
   * @param inserter inserter to handle new trees and blobs
   * @param original The commit to rebase
   * @param base Base to rebase against
   * @param mergeUtil merge utilities for the destination project
   * @param committerIdent committer identity
   * @return the id of the rebased commit
   * @throws IOException Merged failed
   * @throws PathConflictException the rebase failed due to a path conflict
   */
  private ObjectId rebaseCommit(final Repository git,
      final ObjectInserter inserter, final RevCommit original,
      final RevCommit base, final MergeUtil mergeUtil,
      final PersonIdent committerIdent) throws IOException,
      PathConflictException {

    final RevCommit parentCommit = original.getParent(0);

    if (base.equals(parentCommit)) {
      throw new IOException("Change is already up to date.");
    }

    final ThreeWayMerger merger = mergeUtil.newThreeWayMerger(git, inserter);
    merger.setBase(parentCommit);
    merger.merge(original, base);

    if (merger.getResultTreeId() == null) {
      throw new PathConflictException(
          "The change could not be rebased due to a path conflict during merge.");
    }

    final CommitBuilder cb = new CommitBuilder();
    cb.setTreeId(merger.getResultTreeId());
    cb.setParentId(base);
    cb.setAuthor(original.getAuthorIdent());
    cb.setMessage(original.getFullMessage());
    cb.setCommitter(committerIdent);
    final ObjectId objectId = inserter.insert(cb);
    inserter.flush();
    return objectId;
  }

  public static boolean canDoRebase(final ReviewDb db,
      final Change change, final GitRepositoryManager gitManager,
      List<PatchSetAncestor> patchSetAncestors,
      List<PatchSet> depPatchSetList, List<Change> depChangeList)
      throws OrmException, RepositoryNotFoundException, IOException {

    final Repository git = gitManager.openRepository(change.getProject());

    try {
      // If no exception is thrown, then we can do a rebase.
      findBaseRevision(change.currentPatchSetId(), db, change.getDest(), git,
          patchSetAncestors, depPatchSetList, depChangeList);
      return true;
    } catch (IOException e) {
      return false;
    } finally {
      git.close();
    }
  }
}
