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

package com.google.gerrit.server.change;

import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.errors.EmailException;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Change.Status;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetAncestor;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.change.PatchSetInserter.ValidatePolicy;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MergeConflictException;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.ThreeWayMerger;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.util.List;
import java.util.TimeZone;

@Singleton
public class RebaseChange {
  private final ChangeControl.GenericFactory changeControlFactory;
  private final Provider<ReviewDb> db;
  private final GitRepositoryManager gitManager;
  private final TimeZone serverTimeZone;
  private final MergeUtil.Factory mergeUtilFactory;
  private final PatchSetInserter.Factory patchSetInserterFactory;

  @Inject
  RebaseChange(final ChangeControl.GenericFactory changeControlFactory,
      final Provider<ReviewDb> db,
      @GerritPersonIdent final PersonIdent myIdent,
      final GitRepositoryManager gitManager,
      final MergeUtil.Factory mergeUtilFactory,
      final PatchSetInserter.Factory patchSetInserterFactory) {
    this.changeControlFactory = changeControlFactory;
    this.db = db;
    this.gitManager = gitManager;
    this.serverTimeZone = myIdent.getTimeZone();
    this.mergeUtilFactory = mergeUtilFactory;
    this.patchSetInserterFactory = patchSetInserterFactory;
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
   * @param change the change to perform the rebase for
   * @param patchSetId the id of the patch set
   * @param uploader the user that creates the rebased patch set
   * @param newBaseRev the commit that should be the new base
   * @throws NoSuchChangeException thrown if the change to which the patch set
   *         belongs does not exist or is not visible to the user
   * @throws EmailException thrown if sending the e-mail to notify about the new
   *         patch set fails
   * @throws OrmException thrown in case accessing the database fails
   * @throws IOException thrown if rebase is not possible or not needed
   * @throws InvalidChangeOperationException thrown if rebase is not allowed
   */
  public void rebase(Change change, PatchSet.Id patchSetId, final IdentifiedUser uploader,
      final String newBaseRev) throws NoSuchChangeException, EmailException, OrmException,
      IOException, InvalidChangeOperationException {
    final Change.Id changeId = patchSetId.getParentKey();
    final ChangeControl changeControl =
        changeControlFactory.validateFor(change, uploader);
    if (!changeControl.canRebase()) {
      throw new InvalidChangeOperationException(
          "Cannot rebase: New patch sets are not allowed to be added to change: "
              + changeId.toString());
    }
    try (Repository git = gitManager.openRepository(change.getProject());
        RevWalk rw = new RevWalk(git);
        ObjectInserter inserter = git.newObjectInserter()) {
      String baseRev = newBaseRev;
      if (baseRev == null) {
          baseRev = findBaseRevision(patchSetId, db.get(),
              change.getDest(), git, null, null, null);
      }
      ObjectId baseObjectId = git.resolve(baseRev);
      if (baseObjectId == null) {
        throw new InvalidChangeOperationException(
          "Cannot rebase: Failed to resolve baseRev: " + baseRev);
      }
      final RevCommit baseCommit = rw.parseCommit(baseObjectId);

      PersonIdent committerIdent =
          uploader.newCommitterIdent(TimeUtil.nowTs(),
              serverTimeZone);

      rebase(git, rw, inserter, patchSetId, change,
          uploader, baseCommit, mergeUtilFactory.create(
              changeControl.getProjectControl().getProjectState(), true),
          committerIdent, true, ValidatePolicy.GERRIT);
    } catch (MergeConflictException e) {
      throw new IOException(e.getMessage());
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
            "Cannot rebase a change with multiple parents. Parent commits: "
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

      for (PatchSet depPatchSet : depPatchSetList) {

        Change.Id depChangeId = depPatchSet.getId().getParentKey();
        Change depChange;
        if (depChangeList == null || depChangeList.size() != 1 ||
            !depChangeList.get(0).getId().equals(depChangeId)) {
          depChange = db.changes().get(depChangeId);
        } else {
          depChange = depChangeList.get(0);
        }
        if (!depChange.getDest().equals(destBranch)) {
          continue;
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
        break;
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
   * E-mail notification and triggering of hooks is only done for the creation of
   * the new patch set if `sendEmail` and `runHooks` are set to true.
   *
   * @param git the repository
   * @param revWalk the RevWalk
   * @param inserter the object inserter
   * @param patchSetId the id of the patch set
   * @param change the change that should be rebased
   * @param uploader the user that creates the rebased patch set
   * @param baseCommit the commit that should be the new base
   * @param mergeUtil merge utilities for the destination project
   * @param committerIdent the committer's identity
   * @param runHooks if hooks should be run for the new patch set
   * @param validate if commit validation should be run for the new patch set
   * @return the new patch set which is based on the given base commit
   * @throws NoSuchChangeException thrown if the change to which the patch set
   *         belongs does not exist or is not visible to the user
   * @throws OrmException thrown in case accessing the database fails
   * @throws IOException thrown if rebase is not possible or not needed
   * @throws InvalidChangeOperationException thrown if rebase is not allowed
   */
  public PatchSet rebase(final Repository git, final RevWalk revWalk,
      final ObjectInserter inserter, final PatchSet.Id patchSetId,
      final Change change, final IdentifiedUser uploader, final RevCommit baseCommit,
      final MergeUtil mergeUtil, PersonIdent committerIdent,
      boolean runHooks, ValidatePolicy validate)
          throws NoSuchChangeException,
      OrmException, IOException, InvalidChangeOperationException,
      MergeConflictException {
    if (!change.currentPatchSetId().equals(patchSetId)) {
      throw new InvalidChangeOperationException("patch set is not current");
    }
    final PatchSet originalPatchSet = db.get().patchSets().get(patchSetId);

    final RevCommit rebasedCommit;
    ObjectId oldId = ObjectId.fromString(originalPatchSet.getRevision().get());
    ObjectId newId = rebaseCommit(git, inserter, revWalk.parseCommit(oldId),
        baseCommit, mergeUtil, committerIdent);

    rebasedCommit = revWalk.parseCommit(newId);

    final ChangeControl changeControl =
        changeControlFactory.validateFor(change, uploader);

    PatchSetInserter patchSetInserter = patchSetInserterFactory
        .create(git, revWalk, changeControl, rebasedCommit)
        .setValidatePolicy(validate)
        .setDraft(originalPatchSet.isDraft())
        .setUploader(uploader.getAccountId())
        .setSendMail(false)
        .setRunHooks(runHooks);

    final PatchSet.Id newPatchSetId = patchSetInserter.getPatchSetId();
    final ChangeMessage cmsg = new ChangeMessage(
        new ChangeMessage.Key(change.getId(),
            ChangeUtil.messageUUID(db.get())), uploader.getAccountId(),
            TimeUtil.nowTs(), patchSetId);

    cmsg.setMessage("Patch Set " + newPatchSetId.get()
        + ": Patch Set " + patchSetId.get() + " was rebased");

    Change newChange = patchSetInserter
        .setMessage(cmsg)
        .insert();

    return db.get().patchSets().get(newChange.currentPatchSetId());
  }

  /**
   * Rebase a commit.
   *
   * @param git repository to find commits in.
   * @param inserter inserter to handle new trees and blobs.
   * @param original the commit to rebase.
   * @param base base to rebase against.
   * @param mergeUtil merge utilities for the destination project.
   * @param committerIdent committer identity.
   * @return the id of the rebased commit.
   * @throws MergeConflictException the rebase failed due to a merge conflict.
   * @throws IOException the merge failed for another reason.
   */
  private ObjectId rebaseCommit(Repository git, ObjectInserter inserter,
      RevCommit original, RevCommit base, MergeUtil mergeUtil,
      PersonIdent committerIdent) throws MergeConflictException, IOException {
    RevCommit parentCommit = original.getParent(0);

    if (base.equals(parentCommit)) {
      throw new IOException("Change is already up to date.");
    }

    ThreeWayMerger merger = mergeUtil.newThreeWayMerger(git, inserter);
    merger.setBase(parentCommit);
    merger.merge(original, base);

    if (merger.getResultTreeId() == null) {
      throw new MergeConflictException(
          "The change could not be rebased due to a conflict during merge.");
    }

    CommitBuilder cb = new CommitBuilder();
    cb.setTreeId(merger.getResultTreeId());
    cb.setParentId(base);
    cb.setAuthor(original.getAuthorIdent());
    cb.setMessage(original.getFullMessage());
    cb.setCommitter(committerIdent);
    ObjectId objectId = inserter.insert(cb);
    inserter.flush();
    return objectId;
  }

  public boolean canRebase(ChangeResource r) {
    Change c = r.getChange();
    return canRebase(c.getProject(), c.currentPatchSetId(), c.getDest());
  }

  public boolean canRebase(RevisionResource r) {
    return canRebase(r.getChange().getProject(),
        r.getPatchSet().getId(), r.getChange().getDest());
  }

  public boolean canRebase(Project.NameKey project,
      PatchSet.Id patchSetId, Branch.NameKey branch) {
    Repository git;
    try {
      git = gitManager.openRepository(project);
    } catch (RepositoryNotFoundException err) {
      return false;
    } catch (IOException err) {
      return false;
    }
    try {
      findBaseRevision(
          patchSetId,
          db.get(),
          branch,
          git,
          null,
          null,
          null);
      return true;
    } catch (IOException e) {
      return false;
    } catch (OrmException e) {
      return false;
    } finally {
      git.close();
    }
  }
}
