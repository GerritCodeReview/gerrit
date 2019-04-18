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

import com.google.gerrit.extensions.restapi.MergeConflictException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.change.RebaseUtil.Base;
import com.google.gerrit.server.git.GroupCollector;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.Context;
import com.google.gerrit.server.update.RepoContext;
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
    RebaseChangeOp create(ChangeNotes notes, PatchSet originalPatchSet, ObjectId baseCommitId);
  }

  private final PatchSetInserter.Factory patchSetInserterFactory;
  private final MergeUtil.Factory mergeUtilFactory;
  private final RebaseUtil rebaseUtil;
  private final ChangeResource.Factory changeResourceFactory;

  private final ChangeNotes notes;
  private final PatchSet originalPatchSet;
  private final IdentifiedUser.GenericFactory identifiedUserFactory;
  private final ProjectCache projectCache;

  private ObjectId baseCommitId;
  private PersonIdent committerIdent;
  private boolean fireRevisionCreated = true;
  private boolean validate = true;
  private boolean checkAddPatchSetPermission = true;
  private boolean forceContentMerge;
  private boolean detailedCommitMessage;
  private boolean postMessage = true;
  private boolean sendEmail = true;
  private boolean matchAuthorToCommitterDate = false;

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
      IdentifiedUser.GenericFactory identifiedUserFactory,
      ProjectCache projectCache,
      @Assisted ChangeNotes notes,
      @Assisted PatchSet originalPatchSet,
      @Assisted ObjectId baseCommitId) {
    this.patchSetInserterFactory = patchSetInserterFactory;
    this.mergeUtilFactory = mergeUtilFactory;
    this.rebaseUtil = rebaseUtil;
    this.changeResourceFactory = changeResourceFactory;
    this.identifiedUserFactory = identifiedUserFactory;
    this.projectCache = projectCache;
    this.notes = notes;
    this.originalPatchSet = originalPatchSet;
    this.baseCommitId = baseCommitId;
  }

  public RebaseChangeOp setCommitterIdent(PersonIdent committerIdent) {
    this.committerIdent = committerIdent;
    return this;
  }

  public RebaseChangeOp setValidate(boolean validate) {
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

  public RebaseChangeOp setDetailedCommitMessage(boolean detailedCommitMessage) {
    this.detailedCommitMessage = detailedCommitMessage;
    return this;
  }

  public RebaseChangeOp setPostMessage(boolean postMessage) {
    this.postMessage = postMessage;
    return this;
  }

  public RebaseChangeOp setSendEmail(boolean sendEmail) {
    this.sendEmail = sendEmail;
    return this;
  }

  public RebaseChangeOp setMatchAuthorToCommitterDate(boolean matchAuthorToCommitterDate) {
    this.matchAuthorToCommitterDate = matchAuthorToCommitterDate;
    return this;
  }

  @Override
  public void updateRepo(RepoContext ctx)
      throws MergeConflictException, InvalidChangeOperationException, RestApiException, IOException,
          NoSuchChangeException, PermissionBackendException {
    // Ok that originalPatchSet was not read in a transaction, since we just
    // need its revision.
    RevId oldRev = originalPatchSet.getRevision();

    RevWalk rw = ctx.getRevWalk();
    RevCommit original = rw.parseCommit(ObjectId.fromString(oldRev.get()));
    rw.parseBody(original);
    RevCommit baseCommit = rw.parseCommit(baseCommitId);
    CurrentUser changeOwner = identifiedUserFactory.create(notes.getChange().getOwner());

    String newCommitMessage;
    if (detailedCommitMessage) {
      rw.parseBody(baseCommit);
      newCommitMessage =
          newMergeUtil()
              .createCommitMessageOnSubmit(original, baseCommit, notes, originalPatchSet.getId());
    } else {
      newCommitMessage = original.getFullMessage();
    }

    rebasedCommit = rebaseCommit(ctx, original, baseCommit, newCommitMessage);
    Base base =
        rebaseUtil.parseBase(
            new RevisionResource(
                changeResourceFactory.create(notes, changeOwner), originalPatchSet),
            baseCommitId.name());

    rebasedPatchSetId =
        ChangeUtil.nextPatchSetIdFromChangeRefs(
            ctx.getRepoView()
                .getRefs(originalPatchSet.getId().getParentKey().toRefPrefix())
                .keySet(),
            notes.getChange().currentPatchSetId());
    patchSetInserter =
        patchSetInserterFactory
            .create(notes, rebasedPatchSetId, rebasedCommit)
            .setDescription("Rebase")
            .setFireRevisionCreated(fireRevisionCreated)
            .setCheckAddPatchSetPermission(checkAddPatchSetPermission)
            .setValidate(validate)
            .setSendEmail(sendEmail);
    if (postMessage) {
      patchSetInserter.setMessage(
          "Patch Set "
              + rebasedPatchSetId.get()
              + ": Patch Set "
              + originalPatchSet.getId().get()
              + " was rebased");
    }

    if (base != null && !base.notes().getChange().isMerged()) {
      if (!base.notes().getChange().isMerged()) {
        // Add to end of relation chain for open base change.
        patchSetInserter.setGroups(base.patchSet().getGroups());
      } else {
        // If the base is merged, start a new relation chain.
        patchSetInserter.setGroups(GroupCollector.getDefaultGroups(rebasedCommit));
      }
    }
    patchSetInserter.updateRepo(ctx);
  }

  @Override
  public boolean updateChange(ChangeContext ctx) throws ResourceConflictException, IOException {
    boolean ret = patchSetInserter.updateChange(ctx);
    rebasedPatchSet = patchSetInserter.getPatchSet();
    return ret;
  }

  @Override
  public void postUpdate(Context ctx) {
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

  private MergeUtil newMergeUtil() throws IOException {
    ProjectState project = projectCache.checkedGet(notes.getProjectName());
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
        newMergeUtil().newThreeWayMerger(ctx.getInserter(), ctx.getRepoView().getConfig());
    merger.setBase(parentCommit);
    boolean success = merger.merge(original, base);

    if (!success || merger.getResultTreeId() == null) {
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
    if (matchAuthorToCommitterDate) {
      cb.setAuthor(
          new PersonIdent(
              cb.getAuthor(), cb.getCommitter().getWhen(), cb.getCommitter().getTimeZone()));
    }
    ObjectId objectId = ctx.getInserter().insert(cb);
    ctx.getInserter().flush();
    return ctx.getRevWalk().parseCommit(objectId);
  }
}
