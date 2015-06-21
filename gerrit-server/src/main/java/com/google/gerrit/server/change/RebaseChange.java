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
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Change.Status;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.TimeZone;

@Singleton
public class RebaseChange {
  private static final Logger log = LoggerFactory.getLogger(RebaseChange.class);

  private final ChangeControl.GenericFactory changeControlFactory;
  private final Provider<ReviewDb> db;
  private final GitRepositoryManager gitManager;
  private final TimeZone serverTimeZone;
  private final MergeUtil.Factory mergeUtilFactory;
  private final PatchSetInserter.Factory patchSetInserterFactory;

  @Inject
  RebaseChange(ChangeControl.GenericFactory changeControlFactory,
      Provider<ReviewDb> db,
      @GerritPersonIdent PersonIdent myIdent,
      GitRepositoryManager gitManager,
      MergeUtil.Factory mergeUtilFactory,
      PatchSetInserter.Factory patchSetInserterFactory) {
    this.changeControlFactory = changeControlFactory;
    this.db = db;
    this.gitManager = gitManager;
    this.serverTimeZone = myIdent.getTimeZone();
    this.mergeUtilFactory = mergeUtilFactory;
    this.patchSetInserterFactory = patchSetInserterFactory;
  }

  /**
   * Rebase the change of the given patch set.
   * <p>
   * It is verified that the current user is allowed to do the rebase.
   * <p>
   * If the patch set has no dependency to an open change, then the change is
   * rebased on the tip of the destination branch.
   * <p>
   * If the patch set depends on an open change, it is rebased on the latest
   * patch set of this change.
   * <p>
   * The rebased commit is added as new patch set to the change.
   * <p>
   * E-mail notification and triggering of hooks happens for the creation of the
   * new patch set.
   *
   * @param git the repository.
   * @param rw the RevWalk.
   * @param change the change to rebase.
   * @param patchSetId the patch set ID to rebase.
   * @param uploader the user that creates the rebased patch set.
   * @param newBaseRev the commit that should be the new base.
   * @throws NoSuchChangeException if the change to which the patch set belongs
   *     does not exist or is not visible to the user.
   * @throws EmailException if sending the e-mail to notify about the new patch
   *     set fails.
   * @throws OrmException if accessing the database fails.
   * @throws IOException if accessing the repository fails.
   * @throws InvalidChangeOperationException if rebase is not possible or not
   *     allowed.
   */
  public void rebase(Repository git, RevWalk rw, Change change,
      PatchSet.Id patchSetId, IdentifiedUser uploader, String newBaseRev)
      throws NoSuchChangeException, EmailException, OrmException, IOException,
      ResourceConflictException, InvalidChangeOperationException {
    Change.Id changeId = patchSetId.getParentKey();
    ChangeControl changeControl =
        changeControlFactory.validateFor(change, uploader);
    if (!changeControl.canRebase()) {
      throw new InvalidChangeOperationException("Cannot rebase: New patch sets"
          + " are not allowed to be added to change: " + changeId);
    }
    try (ObjectInserter inserter = git.newObjectInserter()) {
      String baseRev = newBaseRev;
      if (baseRev == null) {
        baseRev = findBaseRevision(
            patchSetId, db.get(), change.getDest(), git, rw);
      }
      ObjectId baseObjectId = git.resolve(baseRev);
      if (baseObjectId == null) {
        throw new InvalidChangeOperationException(
          "Cannot rebase: Failed to resolve baseRev: " + baseRev);
      }
      RevCommit baseCommit = rw.parseCommit(baseObjectId);

      PersonIdent committerIdent =
          uploader.newCommitterIdent(TimeUtil.nowTs(), serverTimeZone);

      rebase(git, rw, inserter, change, patchSetId,
          uploader, baseCommit, mergeUtilFactory.create(
              changeControl.getProjectControl().getProjectState(), true),
          committerIdent, true, ValidatePolicy.GERRIT);
    } catch (MergeConflictException e) {
      throw new ResourceConflictException(e.getMessage());
    }
  }

  /**
   * Find the commit onto which a patch set should be rebased.
   * <p>
   * This is defined as the latest patch set of the change corresponding to
   * this commit's parent, or the destination branch tip in the case where the
   * parent's change is merged.
   *
   * @param patchSetId patch set ID for which the new base commit should be
   *     found.
   * @param db the ReviewDb.
   * @param destBranch the destination branch.
   * @param git the repository.
   * @param rw the RevWalk.
   * @return the commit onto which the patch set should be rebased.
   * @throws InvalidChangeOperationException if rebase is not possible or not
   *     allowed.
   * @throws IOException if accessing the repository fails.
   * @throws OrmException if accessing the database fails.
   */
  private static String findBaseRevision(PatchSet.Id patchSetId,
      ReviewDb db, Branch.NameKey destBranch, Repository git, RevWalk rw)
      throws InvalidChangeOperationException, IOException, OrmException {
    String baseRev = null;

    PatchSet patchSet = db.patchSets().get(patchSetId);
    if (patchSet == null) {
      throw new InvalidChangeOperationException(
          "Patch set " + patchSetId + " not found");
    }
    RevCommit commit = rw.parseCommit(
        ObjectId.fromString(patchSet.getRevision().get()));

    if (commit.getParentCount() > 1) {
      throw new InvalidChangeOperationException(
          "Cannot rebase a change with multiple parents.");
    } else if (commit.getParentCount() == 0) {
      throw new InvalidChangeOperationException(
          "Cannot rebase a change without any parents"
          + " (is this the initial commit?).");
    }

    RevId parentRev = new RevId(commit.getParent(0).name());

    for (PatchSet depPatchSet : db.patchSets().byRevision(parentRev)) {
      Change.Id depChangeId = depPatchSet.getId().getParentKey();
      Change depChange = db.changes().get(depChangeId);
      if (!depChange.getDest().equals(destBranch)) {
        continue;
      }

      if (depChange.getStatus() == Status.ABANDONED) {
        throw new InvalidChangeOperationException(
            "Cannot rebase a change with an abandoned parent: "
            + depChange.getKey());
      }

      if (depChange.getStatus().isOpen()) {
        if (depPatchSet.getId().equals(depChange.currentPatchSetId())) {
          throw new InvalidChangeOperationException(
              "Change is already based on the latest patch set of the"
              + " dependent change.");
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
      Ref destRef = git.getRefDatabase().exactRef(destBranch.get());
      if (destRef == null) {
        throw new InvalidChangeOperationException(
            "The destination branch does not exist: " + destBranch.get());
      }
      baseRev = destRef.getObjectId().getName();
      if (baseRev.equals(parentRev.get())) {
        throw new InvalidChangeOperationException(
            "Change is already up to date.");
      }
    }
    return baseRev;
  }

  /**
   * Rebase the change of the given patch set on the given base commit.
   * <p>
   * The rebased commit is added as new patch set to the change.
   * <p>
   * E-mail notification and triggering of hooks is only done for the creation
   * of the new patch set if {@code sendEmail} and {@code runHooks} are true,
   * respectively.
   *
   * @param git the repository.
   * @param inserter the object inserter.
   * @param change the change to rebase.
   * @param patchSetId the patch set ID to rebase.
   * @param uploader the user that creates the rebased patch set.
   * @param baseCommit the commit that should be the new base.
   * @param mergeUtil merge utilities for the destination project.
   * @param committerIdent the committer's identity.
   * @param runHooks if hooks should be run for the new patch set.
   * @param validate if commit validation should be run for the new patch set.
   * @param rw the RevWalk.
   * @return the new patch set, which is based on the given base commit.
   * @throws NoSuchChangeException if the change to which the patch set belongs
   *     does not exist or is not visible to the user.
   * @throws OrmException if accessing the database fails.
   * @throws IOException if rebase is not possible.
   * @throws InvalidChangeOperationException if rebase is not possible or not
   *     allowed.
   */
  public PatchSet rebase(Repository git, RevWalk rw,
      ObjectInserter inserter, Change change, PatchSet.Id patchSetId,
      IdentifiedUser uploader, RevCommit baseCommit, MergeUtil mergeUtil,
      PersonIdent committerIdent, boolean runHooks, ValidatePolicy validate)
      throws NoSuchChangeException, OrmException, IOException,
      InvalidChangeOperationException, MergeConflictException {
    if (!change.currentPatchSetId().equals(patchSetId)) {
      throw new InvalidChangeOperationException("patch set is not current");
    }
    PatchSet originalPatchSet = db.get().patchSets().get(patchSetId);

    RevCommit rebasedCommit;
    ObjectId oldId = ObjectId.fromString(originalPatchSet.getRevision().get());
    ObjectId newId = rebaseCommit(git, inserter, rw.parseCommit(oldId),
        baseCommit, mergeUtil, committerIdent);

    rebasedCommit = rw.parseCommit(newId);

    ChangeControl changeControl =
        changeControlFactory.validateFor(change, uploader);

    PatchSetInserter patchSetInserter = patchSetInserterFactory
        .create(git, rw, changeControl, rebasedCommit)
        .setValidatePolicy(validate)
        .setDraft(originalPatchSet.isDraft())
        .setUploader(uploader.getAccountId())
        .setSendMail(false)
        .setRunHooks(runHooks);

    PatchSet.Id newPatchSetId = patchSetInserter.getPatchSetId();
    ChangeMessage cmsg = new ChangeMessage(
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
      PersonIdent committerIdent) throws MergeConflictException, IOException,
      InvalidChangeOperationException {
    RevCommit parentCommit = original.getParent(0);

    if (base.equals(parentCommit)) {
      throw new InvalidChangeOperationException(
          "Change is already up to date.");
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

  public boolean canRebase(Project.NameKey project, PatchSet.Id patchSetId,
      Branch.NameKey branch) {
    Repository git;
    try {
      git = gitManager.openRepository(project);
    } catch (RepositoryNotFoundException err) {
      return false;
    } catch (IOException err) {
      return false;
    }
    try (RevWalk rw = new RevWalk(git)) {
      findBaseRevision(patchSetId, db.get(), branch, git, rw);
      return true;
    } catch (InvalidChangeOperationException e) {
      return false;
    } catch (OrmException | IOException e) {
      log.warn("Error checking if patch set " + patchSetId + " on " + branch
          + " can be rebased", e);
      return false;
    } finally {
      git.close();
    }
  }
}
