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
import com.google.gerrit.extensions.restapi.MergeConflictException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.BatchUpdate.ChangeContext;
import com.google.gerrit.server.git.BatchUpdate.Context;
import com.google.gerrit.server.git.BatchUpdate.RepoContext;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.git.validators.CommitValidators;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.ProjectState;
import com.google.gwtorm.server.OrmException;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.merge.ThreeWayMerger;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;

public class RebaseChangeOp extends BatchUpdate.Op {
  public interface Factory {
    RebaseChangeOp create(ChangeControl ctl, PatchSet originalPatchSet,
        @Nullable String baseCommitish);
  }

  private final PatchSetInserter.Factory patchSetInserterFactory;
  private final MergeUtil.Factory mergeUtilFactory;

  private final ChangeControl ctl;
  private final PatchSet originalPatchSet;

  private String baseCommitish;
  private PersonIdent committerIdent;
  private boolean runHooks = true;
  private CommitValidators.Policy validate;
  private boolean forceContentMerge;

  private RevCommit rebasedCommit;
  private PatchSet.Id rebasedPatchSetId;
  private PatchSetInserter patchSetInserter;
  private PatchSet rebasedPatchSet;

  @AssistedInject
  RebaseChangeOp(
      PatchSetInserter.Factory patchSetInserterFactory,
      MergeUtil.Factory mergeUtilFactory,
      @Assisted ChangeControl ctl,
      @Assisted PatchSet originalPatchSet,
      @Assisted @Nullable String baseCommitish) {
    this.patchSetInserterFactory = patchSetInserterFactory;
    this.mergeUtilFactory = mergeUtilFactory;
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

  public RebaseChangeOp setRunHooks(boolean runHooks) {
    this.runHooks = runHooks;
    return this;
  }

  public RebaseChangeOp setForceContentMerge(boolean forceContentMerge) {
    this.forceContentMerge = forceContentMerge;
    return this;
  }

  @Override
  public void updateRepo(RepoContext ctx) throws MergeConflictException,
       InvalidChangeOperationException, RestApiException, IOException,
       OrmException {
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
       baseCommit = rw.parseCommit(RebaseUtil.findBaseRevision(
           originalPatchSet, ctl.getChange().getDest(),
           ctx.getRepository(), ctx.getRevWalk(), ctx.getDb()));
    }

    ObjectId newId = rebaseCommit(ctx, original, baseCommit);
    rebasedCommit = rw.parseCommit(newId);

    rebasedPatchSetId = ChangeUtil.nextPatchSetId(
        ctx.getRepository(), ctl.getChange().currentPatchSetId());
    patchSetInserter = patchSetInserterFactory
        .create(ctl.getRefControl(), rebasedPatchSetId, rebasedCommit)
        .setDraft(originalPatchSet.isDraft())
        .setUploader(ctx.getUser().getAccountId())
        .setSendMail(false)
        .setRunHooks(runHooks)
        .setMessage(
          "Patch Set " + rebasedPatchSetId.get()
          + ": Patch Set " + originalPatchSet.getId().get() + " was rebased");
    if (validate != null) {
      patchSetInserter.setValidatePolicy(validate);
    }
    patchSetInserter.updateRepo(ctx);
  }

  @Override
  public void updateChange(ChangeContext ctx)
      throws OrmException, InvalidChangeOperationException, IOException {
    patchSetInserter.updateChange(ctx);
    rebasedPatchSet = patchSetInserter.getPatchSet();
  }

  @Override
  public void postUpdate(Context ctx) throws OrmException {
    patchSetInserter.postUpdate(ctx);
  }

  public PatchSet getPatchSet() {
    checkState(rebasedPatchSet != null,
        "getPatchSet() only valid after executing update");
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
  private RevCommit rebaseCommit(RepoContext ctx, RevCommit original,
      ObjectId base) throws ResourceConflictException, MergeConflictException,
      IOException {
    RevCommit parentCommit = original.getParent(0);

    if (base.equals(parentCommit)) {
      throw new ResourceConflictException("Change is already up to date.");
    }

    ThreeWayMerger merger = newMergeUtil().newThreeWayMerger(
        ctx.getRepository(), ctx.getInserter());
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
      cb.setCommitter(ctx.getUser().asIdentifiedUser()
          .newCommitterIdent(ctx.getWhen(), ctx.getTimeZone()));
    }
    ObjectId objectId = ctx.getInserter().insert(cb);
    ctx.getInserter().flush();
    return ctx.getRevWalk().parseCommit(objectId);
  }
}
