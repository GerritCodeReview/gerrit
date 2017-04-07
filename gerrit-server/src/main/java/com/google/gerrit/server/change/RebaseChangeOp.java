// Copyright (C) 2015 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkState;

import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.restapi.MergeConflictException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.change.RebaseUtil.Base;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.git.validators.CommitValidators;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.Context;
import com.google.gerrit.server.update.RepoContext;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.merge.ThreeWayMerger;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

public class RebaseChangeOp implements BatchUpdateOp {
  public interface Factory {
    RebaseChangeOp create(ChangeControl ctl, PatchSet originalPatchSet, ObjectId baseCommitId);
  }

  private final PatchSetInserter.Factory patchSetInserterFactory;
  private final MergeUtil.Factory mergeUtilFactory;
  private final RebaseUtil rebaseUtil;
  private final ChangeResource.Factory changeResourceFactory;

  private final ChangeControl ctl;
  private final PatchSet originalPatchSet;

  private ObjectId baseCommitId;
  private PersonIdent committerIdent;
  private boolean fireRevisionCreated = true;
  private CommitValidators.Policy validate;
  private boolean checkAddPatchSetPermission = true;
  private boolean forceContentMerge;
  private boolean copyApprovals = true;
  private boolean detailedCommitMessage;
  private boolean postMessage = true;

  private RevCommit rebasedCommit;
  private PatchSet.Id rebasedPatchSetId;
  private PatchSetInserter patchSetInserter;
  private PatchSet rebasedPatchSet;

  @Inject
  RebaseChangeOp(
      PatchSetInserter.Factory patchSetInserterFactory,
      MergeUtil.Factory mergeUtilFactory,
      RebaseUtil rebaseUtil,
      ChangeResource.Factory changeResourceFactory,
      @Assisted ChangeControl ctl,
      @Assisted PatchSet originalPatchSet,
      @Assisted ObjectId baseCommitId) {
    this.patchSetInserterFactory = patchSetInserterFactory;
    this.mergeUtilFactory = mergeUtilFactory;
    this.rebaseUtil = rebaseUtil;
    this.changeResourceFactory = changeResourceFactory;
    this.ctl = ctl;
    this.originalPatchSet = originalPatchSet;
    this.baseCommitId = baseCommitId;
  }

  public RebaseChangeOp setCommitterIdent(PersonIdent committerIdent) {
    this.committerIdent = committerIdent;
    return this;
  }

  public RebaseChangeOp setValidatePolicy(CommitValidators.Policy validate) {
    this.validate = validate;
    return this;
  }

  public RebaseChangeOp setCheckAddPatchSetPermission(boolean checkAddPatchSetPermission) {
    this.checkAddPatchSetPermission = checkAddPatchSetPermission;
    return this;
  }

  public RebaseChangeOp setFireRevisionCreated(boolean fireRevisionCreated) {
    this.fireRevisionCreated = fireRevisionCreated;
    return this;
  }

  public RebaseChangeOp setForceContentMerge(boolean forceContentMerge) {
    this.forceContentMerge = forceContentMerge;
    return this;
  }

  public RebaseChangeOp setCopyApprovals(boolean copyApprovals) {
    this.copyApprovals = copyApprovals;
    return this;
  }

  public RebaseChangeOp setDetailedCommitMessage(boolean detailedCommitMessage) {
    this.detailedCommitMessage = detailedCommitMessage;
    return this;
  }

  public RebaseChangeOp setPostMessage(boolean postMessage) {
    this.postMessage = postMessage;
    return this;
  }

  @Override
  public void updateRepo(RepoContext ctx)
      throws MergeConflictException, InvalidChangeOperationException, RestApiException, IOException,
          OrmException, NoSuchChangeException {
    // Ok that originalPatchSet was not read in a transaction, since we just
    // need its revision.
    RevId oldRev = originalPatchSet.getRevision();

    RevWalk rw = ctx.getRevWalk();
    RevCommit original = rw.parseCommit(ObjectId.fromString(oldRev.get()));
    rw.parseBody(original);
    RevCommit baseCommit = rw.parseCommit(baseCommitId);

    String newCommitMessage;
    if (detailedCommitMessage) {
      rw.parseBody(baseCommit);
      newCommitMessage =
          newMergeUtil()
              .createCommitMessageOnSubmit(original, baseCommit, ctl, originalPatchSet.getId());
    } else {
      newCommitMessage = original.getFullMessage();
    }

    rebasedCommit = rebaseCommit(ctx, original, baseCommit, newCommitMessage);
    Base base =
        rebaseUtil.parseBase(
            new RevisionResource(changeResourceFactory.create(ctl), originalPatchSet),
            baseCommitId.name());

    rebasedPatchSetId =
        ChangeUtil.nextPatchSetId(ctx.getRepository(), ctl.getChange().currentPatchSetId());
    patchSetInserter =
        patchSetInserterFactory
            .create(ctl, rebasedPatchSetId, rebasedCommit)
            .setDescription("Rebase")
            .setDraft(originalPatchSet.isDraft())
            .setNotify(NotifyHandling.NONE)
            .setFireRevisionCreated(fireRevisionCreated)
            .setCopyApprovals(copyApprovals)
            .setCheckAddPatchSetPermission(checkAddPatchSetPermission);
    if (postMessage) {
      patchSetInserter.setMessage(
          "Patch Set "
              + rebasedPatchSetId.get()
              + ": Patch Set "
              + originalPatchSet.getId().get()
              + " was rebased");
    }

    if (base != null) {
      patchSetInserter.setGroups(base.patchSet().getGroups());
    }
    if (validate != null) {
      patchSetInserter.setValidatePolicy(validate);
    }
    patchSetInserter.updateRepo(ctx);
  }

  @Override
  public boolean updateChange(ChangeContext ctx)
      throws ResourceConflictException, OrmException, IOException {
    boolean ret = patchSetInserter.updateChange(ctx);
    rebasedPatchSet = patchSetInserter.getPatchSet();
    return ret;
  }

  @Override
  public void postUpdate(Context ctx) throws OrmException {
    patchSetInserter.postUpdate(ctx);
  }

  public RevCommit getRebasedCommit() {
    checkState(rebasedCommit != null, "getRebasedCommit() only valid after updateRepo");
    return rebasedCommit;
  }

  public PatchSet.Id getPatchSetId() {
    checkState(rebasedPatchSetId != null, "getPatchSetId() only valid after updateRepo");
    return rebasedPatchSetId;
  }

  public PatchSet getPatchSet() {
    checkState(rebasedPatchSet != null, "getPatchSet() only valid after executing update");
    return rebasedPatchSet;
  }

  private MergeUtil newMergeUtil() {
    ProjectState project = ctl.getProjectControl().getProjectState();
    return forceContentMerge
        ? mergeUtilFactory.create(project, true)
        : mergeUtilFactory.create(project);
  }

  /**
   * Rebase a commit.
   *
   * @param ctx repo context.
   * @param original the commit to rebase.
   * @param base base to rebase against.
   * @return the rebased commit.
   * @throws MergeConflictException the rebase failed due to a merge conflict.
   * @throws IOException the merge failed for another reason.
   */
  private RevCommit rebaseCommit(
      RepoContext ctx, RevCommit original, ObjectId base, String commitMessage)
      throws ResourceConflictException, IOException {
    RevCommit parentCommit = original.getParent(0);

    if (base.equals(parentCommit)) {
      throw new ResourceConflictException("Change is already up to date.");
    }

    ThreeWayMerger merger =
        newMergeUtil().newThreeWayMerger(ctx.getInserter(), ctx.getRepository().getConfig());
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
    cb.setMessage(commitMessage);
    if (committerIdent != null) {
      cb.setCommitter(committerIdent);
    } else {
      cb.setCommitter(ctx.getIdentifiedUser().newCommitterIdent(ctx.getWhen(), ctx.getTimeZone()));
    }
    ObjectId objectId = ctx.getInserter().insert(cb);
    ctx.getInserter().flush();
    return ctx.getRevWalk().parseCommit(objectId);
  }
}
