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

import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.restapi.MergeConflictException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.change.RebaseUtil.Base;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.BatchUpdate.ChangeContext;
import com.google.gerrit.server.git.BatchUpdate.Context;
import com.google.gerrit.server.git.BatchUpdate.RepoContext;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.git.validators.CommitValidators;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectState;
import com.google.gwtorm.server.OrmException;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.io.IOException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.merge.ThreeWayMerger;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

public class RebaseChangeOp extends BatchUpdate.Op {
  public interface Factory {
    RebaseChangeOp create(
        ChangeControl ctl, PatchSet originalPatchSet, @Nullable String baseCommitish);
  }

  private final PatchSetInserter.Factory patchSetInserterFactory;
  private final MergeUtil.Factory mergeUtilFactory;
  private final RebaseUtil rebaseUtil;
  private final ChangeResource.Factory changeResourceFactory;

  private final ChangeControl ctl;
  private final PatchSet originalPatchSet;

  private String baseCommitish;
  private PersonIdent committerIdent;
  private boolean fireRevisionCreated = true;
  private CommitValidators.Policy validate;
  private boolean forceContentMerge;
  private boolean copyApprovals = true;
  private boolean postMessage = true;

  private RevCommit rebasedCommit;
  private PatchSet.Id rebasedPatchSetId;
  private PatchSetInserter patchSetInserter;
  private PatchSet rebasedPatchSet;

  @AssistedInject
  RebaseChangeOp(
      PatchSetInserter.Factory patchSetInserterFactory,
      MergeUtil.Factory mergeUtilFactory,
      RebaseUtil rebaseUtil,
      ChangeResource.Factory changeResourceFactory,
      @Assisted ChangeControl ctl,
      @Assisted PatchSet originalPatchSet,
      @Assisted @Nullable String baseCommitish) {
    this.patchSetInserterFactory = patchSetInserterFactory;
    this.mergeUtilFactory = mergeUtilFactory;
    this.rebaseUtil = rebaseUtil;
    this.changeResourceFactory = changeResourceFactory;
    this.ctl = ctl;
    this.originalPatchSet = originalPatchSet;
    this.baseCommitish = baseCommitish;
  }

  public RebaseChangeOp setCommitterIdent(PersonIdent committerIdent) {
    this.committerIdent = committerIdent;
    return this;
  }

  public RebaseChangeOp setValidatePolicy(CommitValidators.Policy validate) {
    this.validate = validate;
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
    RevCommit baseCommit;
    if (baseCommitish != null) {
      baseCommit = rw.parseCommit(ctx.getRepository().resolve(baseCommitish));
    } else {
      baseCommit =
          rw.parseCommit(
              rebaseUtil.findBaseRevision(
                  originalPatchSet,
                  ctl.getChange().getDest(),
                  ctx.getRepository(),
                  ctx.getRevWalk()));
    }

    rebasedCommit = rebaseCommit(ctx, original, baseCommit);

    RevId baseRevId =
        new RevId((baseCommitish != null) ? baseCommitish : ObjectId.toString(baseCommit.getId()));
    Base base =
        rebaseUtil.parseBase(
            new RevisionResource(changeResourceFactory.create(ctl), originalPatchSet),
            baseRevId.get());

    rebasedPatchSetId =
        ChangeUtil.nextPatchSetId(ctx.getRepository(), ctl.getChange().currentPatchSetId());
    patchSetInserter =
        patchSetInserterFactory
            .create(ctl, rebasedPatchSetId, rebasedCommit)
            .setDraft(originalPatchSet.isDraft())
            .setNotify(NotifyHandling.NONE)
            .setFireRevisionCreated(fireRevisionCreated)
            .setCopyApprovals(copyApprovals);
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
  private RevCommit rebaseCommit(RepoContext ctx, RevCommit original, ObjectId base)
      throws ResourceConflictException, MergeConflictException, IOException {
    RevCommit parentCommit = original.getParent(0);

    if (base.equals(parentCommit)) {
      throw new ResourceConflictException("Change is already up to date.");
    }

    ThreeWayMerger merger =
        newMergeUtil().newThreeWayMerger(ctx.getRepository(), ctx.getInserter());
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
