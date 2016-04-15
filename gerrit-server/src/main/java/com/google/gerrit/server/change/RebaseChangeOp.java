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

import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.gerrit.common.Nullable;
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

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.merge.ThreeWayMerger;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class RebaseChangeOp extends BatchUpdate.Op {
  public interface Factory {
    RebaseChangeOp create(ChangeControl ctl, PatchSet originalPatchSet,
        @Nullable String baseCommitish);
    RebaseChangeOp create(ChangeControl ctl, PatchSet originalPatchSet,
        @Nullable String baseCommitish, @Nullable ObjectId[] parents);
  }

  private final PatchSetInserter.Factory patchSetInserterFactory;
  private final MergeUtil.Factory mergeUtilFactory;
  private final RebaseUtil rebaseUtil;

  private final ChangeControl ctl;
  private final PatchSet originalPatchSet;

  private String baseCommitish;
  private ObjectId[] parents;
  private PersonIdent committerIdent;
  private boolean runHooks = true;
  private CommitValidators.Policy validate;
  private boolean forceContentMerge;
  private boolean copyApprovals = true;

  private RevCommit rebasedCommit;
  private PatchSet.Id rebasedPatchSetId;
  private PatchSetInserter patchSetInserter;
  private PatchSet rebasedPatchSet;

  @AssistedInject
  RebaseChangeOp(
      PatchSetInserter.Factory patchSetInserterFactory,
      MergeUtil.Factory mergeUtilFactory,
      RebaseUtil rebaseUtil,
      @Assisted ChangeControl ctl,
      @Assisted PatchSet originalPatchSet,
      @Assisted @Nullable String baseCommitish) {
    this(patchSetInserterFactory, mergeUtilFactory, rebaseUtil, ctl,
        originalPatchSet, baseCommitish, null);
  }

  @AssistedInject
  RebaseChangeOp(
      PatchSetInserter.Factory patchSetInserterFactory,
      MergeUtil.Factory mergeUtilFactory,
      RebaseUtil rebaseUtil,
      @Assisted ChangeControl ctl,
      @Assisted PatchSet originalPatchSet,
      @Assisted @Nullable String baseCommitish,
      @Assisted @Nullable ObjectId[] parents) {
    this.patchSetInserterFactory = patchSetInserterFactory;
    this.mergeUtilFactory = mergeUtilFactory;
    this.rebaseUtil = rebaseUtil;
    this.ctl = ctl;
    this.originalPatchSet = originalPatchSet;
    this.baseCommitish = baseCommitish;
    this.parents = parents;
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

  public RebaseChangeOp setCopyApprovals(boolean copyApprovals) {
    this.copyApprovals = copyApprovals;
    return this;
  }

  @Override
  public void updateRepo(RepoContext ctx) throws MergeConflictException,
       InvalidChangeOperationException, RestApiException, IOException,
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
       baseCommit = rw.parseCommit(rebaseUtil.findBaseRevision(
           originalPatchSet, ctl.getChange().getDest(),
           ctx.getRepository(), ctx.getRevWalk()));
    }

    rebasedCommit = rebaseCommit(ctx, original, baseCommit);

    RevId baseRevId = new RevId((baseCommitish != null) ? baseCommitish
        : ObjectId.toString(baseCommit.getId()));
    Base base = rebaseUtil.parseBase(
        new RevisionResource(new ChangeResource(ctl), originalPatchSet),
        baseRevId.get());

    rebasedPatchSetId = ChangeUtil.nextPatchSetId(
        ctx.getRepository(), ctl.getChange().currentPatchSetId());
    patchSetInserter = patchSetInserterFactory
        .create(ctl.getRefControl(), rebasedPatchSetId, rebasedCommit)
        .setDraft(originalPatchSet.isDraft())
        .setSendMail(false)
        .setRunHooks(runHooks)
        .setCopyApprovals(copyApprovals)
        .setMessage(
          "Patch Set " + rebasedPatchSetId.get()
          + ": Patch Set " + originalPatchSet.getId().get() + " was rebased");

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
    checkState(rebasedCommit != null,
        "getRebasedCommit() only valid after updateRepo");
    return rebasedCommit;
  }

  public PatchSet.Id getPatchSetId() {
    checkState(rebasedPatchSetId != null,
        "getPatchSetId() only valid after updateRepo");
    return rebasedPatchSetId;
  }

  public PatchSet getPatchSet() {
    checkState(rebasedPatchSet != null,
        "getPatchSet() only valid after executing update");
    return rebasedPatchSet;
  }

  public static Optional<RevCommit[]> getRebasedParents(RevWalk rw, ObjectId base,
      RevCommit[] originalParents) {
    try {
      RevCommit baseCommit = rw.parseCommit(base);
      List<RevCommit> parents = new ArrayList<>(originalParents.length);
      Iterator<RevCommit> parentsIt = Iterators.forArray(originalParents);
      while (parentsIt.hasNext()) {
        RevCommit parent = parentsIt.next();
        if (rw.isMergedInto(parent, baseCommit)) {
          return Optional.of(FluentIterable.from(parents).append(baseCommit)
              .append(Lists.newArrayList(parentsIt)).toArray(RevCommit.class));
        }
        parents.add(parent);
      }
    } catch (IOException e) {
      //Optional.absent is sufficient and it will be propagated by caller
    }

    return Optional.absent();
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
    ThreeWayMerger merger = null;
    if (original.getParentCount() == 1) {
      parents = new ObjectId[] {base};
      merger = rebaseWithOneParent(ctx, original, base);
    } else {
      // Re-create merge commit on the top of current target if any of commit parents is
      // parent of target tip. That looks actually cleaner in history than merge of merge commit.
      // It is kind of a 'rebase' over one of merge parents commit.
      if (parents == null) {
        Optional<RevCommit[]> hasRebasableParents =
            getRebasedParents(ctx.getRevWalk(), base, original.getParents());
        if (!hasRebasableParents.isPresent()) {
          throw new ResourceConflictException(
              "Rebase of merge commit was not possible. Base is not derived from any parent");
        }
        parents = hasRebasableParents.get();
      }
      merger = rebaseWithMoreParents(ctx, parents);
    }

    if (merger.getResultTreeId() == null) {
      throw new MergeConflictException(
          "The change could not be rebased due to a conflict during merge.");
    }

    CommitBuilder cb = new CommitBuilder();
    cb.setTreeId(merger.getResultTreeId());
    cb.setParentIds(parents);
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

  private ThreeWayMerger rebaseWithMoreParents(RepoContext ctx, ObjectId[] parents)
        throws IOException, MissingObjectException, IncorrectObjectTypeException {
    ThreeWayMerger merger = newMergeUtil().newThreeWayMerger(
        ctx.getRepository(), ctx.getInserter());
    merger.merge(parents);
    return merger;
  }

  private ThreeWayMerger rebaseWithOneParent(RepoContext ctx, RevCommit original, ObjectId base)
        throws ResourceConflictException, IOException, MissingObjectException,
        IncorrectObjectTypeException {
    RevCommit parentCommit = original.getParent(0);

    if (base.equals(parentCommit)) {
      throw new ResourceConflictException("Change is already up to date.");
    }

    ThreeWayMerger merger = newMergeUtil().newThreeWayMerger(
        ctx.getRepository(), ctx.getInserter());
    merger.setBase(parentCommit);
    merger.merge(original, base);
    return merger;
  }
}
