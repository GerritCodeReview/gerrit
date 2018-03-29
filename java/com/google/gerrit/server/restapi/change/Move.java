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

package com.google.gerrit.server.restapi.change;

import static com.google.gerrit.extensions.conditions.BooleanCondition.and;
import static com.google.gerrit.server.PatchSetUtil.isPatchSetLocked;
import static com.google.gerrit.server.permissions.ChangePermission.ABANDON;
import static com.google.gerrit.server.permissions.RefPermission.CREATE_CHANGE;
import static com.google.gerrit.server.query.change.ChangeData.asChanges;

import com.google.common.base.Strings;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.extensions.api.changes.MoveInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Change.Status;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.LabelId;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryingRestModifyView;
import com.google.gerrit.server.update.UpdateException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class Move extends RetryingRestModifyView<ChangeResource, MoveInput, ChangeInfo>
    implements UiAction<ChangeResource> {
  private static final Logger log = LoggerFactory.getLogger(Move.class);

  private final PermissionBackend permissionBackend;
  private final Provider<ReviewDb> dbProvider;
  private final ChangeJson.Factory json;
  private final GitRepositoryManager repoManager;
  private final Provider<InternalChangeQuery> queryProvider;
  private final ChangeMessagesUtil cmUtil;
  private final PatchSetUtil psUtil;
  private final ApprovalsUtil approvalsUtil;
  private final ProjectCache projectCache;
  private final Provider<CurrentUser> userProvider;

  @Inject
  Move(
      PermissionBackend permissionBackend,
      Provider<ReviewDb> dbProvider,
      ChangeJson.Factory json,
      GitRepositoryManager repoManager,
      Provider<InternalChangeQuery> queryProvider,
      ChangeMessagesUtil cmUtil,
      RetryHelper retryHelper,
      PatchSetUtil psUtil,
      ApprovalsUtil approvalsUtil,
      ProjectCache projectCache,
      Provider<CurrentUser> userProvider) {
    super(retryHelper);
    this.permissionBackend = permissionBackend;
    this.dbProvider = dbProvider;
    this.json = json;
    this.repoManager = repoManager;
    this.queryProvider = queryProvider;
    this.cmUtil = cmUtil;
    this.psUtil = psUtil;
    this.approvalsUtil = approvalsUtil;
    this.projectCache = projectCache;
    this.userProvider = userProvider;
  }

  @Override
  protected ChangeInfo applyImpl(
      BatchUpdate.Factory updateFactory, ChangeResource rsrc, MoveInput input)
      throws RestApiException, OrmException, UpdateException, PermissionBackendException,
          IOException {
    Change change = rsrc.getChange();
    Project.NameKey project = rsrc.getProject();
    IdentifiedUser caller = rsrc.getUser().asIdentifiedUser();
    input.destinationBranch = RefNames.fullName(input.destinationBranch);

    if (change.getStatus().isClosed()) {
      throw new ResourceConflictException("Change is " + ChangeUtil.status(change));
    }

    Branch.NameKey newDest = new Branch.NameKey(project, input.destinationBranch);
    if (change.getDest().equals(newDest)) {
      throw new ResourceConflictException("Change is already destined for the specified branch");
    }

    // Not allowed to move if the current patch set is locked.
    if (isPatchSetLocked(
        approvalsUtil, projectCache, dbProvider.get(), rsrc.getNotes(), rsrc.getUser())) {
      throw new ResourceConflictException(
          String.format("The current patch set of change %s is locked", rsrc.getId()));
    }

    // Move requires abandoning this change, and creating a new change.
    try {
      rsrc.permissions().database(dbProvider).check(ABANDON);
      permissionBackend.user(caller).database(dbProvider).ref(newDest).check(CREATE_CHANGE);
    } catch (AuthException denied) {
      throw new AuthException("move not permitted", denied);
    }
    projectCache.checkedGet(project).checkStatePermitsWrite();

    try (BatchUpdate u =
        updateFactory.create(dbProvider.get(), project, caller, TimeUtil.nowTs())) {
      u.addOp(change.getId(), new Op(input));
      u.execute();
    }
    return json.noOptions().format(project, rsrc.getId());
  }

  private class Op implements BatchUpdateOp {
    private final MoveInput input;

    private Change change;
    private Branch.NameKey newDestKey;

    Op(MoveInput input) {
      this.input = input;
    }

    @Override
    public boolean updateChange(ChangeContext ctx)
        throws OrmException, ResourceConflictException, IOException {
      change = ctx.getChange();
      if (change.getStatus() != Status.NEW) {
        throw new ResourceConflictException("Change is " + ChangeUtil.status(change));
      }

      Project.NameKey projectKey = change.getProject();
      newDestKey = new Branch.NameKey(projectKey, input.destinationBranch);
      Branch.NameKey changePrevDest = change.getDest();
      if (changePrevDest.equals(newDestKey)) {
        throw new ResourceConflictException("Change is already destined for the specified branch");
      }

      final PatchSet.Id patchSetId = change.currentPatchSetId();
      try (Repository repo = repoManager.openRepository(projectKey);
          RevWalk revWalk = new RevWalk(repo)) {
        RevCommit currPatchsetRevCommit =
            revWalk.parseCommit(
                ObjectId.fromString(
                    psUtil.current(ctx.getDb(), ctx.getNotes()).getRevision().get()));
        if (currPatchsetRevCommit.getParentCount() > 1) {
          throw new ResourceConflictException("Merge commit cannot be moved");
        }

        ObjectId refId = repo.resolve(input.destinationBranch);
        // Check if destination ref exists in project repo
        if (refId == null) {
          throw new ResourceConflictException(
              "Destination " + input.destinationBranch + " not found in the project");
        }
        RevCommit refCommit = revWalk.parseCommit(refId);
        if (revWalk.isMergedInto(currPatchsetRevCommit, refCommit)) {
          throw new ResourceConflictException(
              "Current patchset revision is reachable from tip of " + input.destinationBranch);
        }
      }

      Change.Key changeKey = change.getKey();
      if (!asChanges(queryProvider.get().byBranchKey(newDestKey, changeKey)).isEmpty()) {
        throw new ResourceConflictException(
            "Destination "
                + newDestKey.getShortName()
                + " has a different change with same change key "
                + changeKey);
      }

      if (!change.currentPatchSetId().equals(patchSetId)) {
        throw new ResourceConflictException("Patch set is not current");
      }

      PatchSet.Id psId = change.currentPatchSetId();
      ChangeUpdate update = ctx.getUpdate(psId);
      update.setBranch(newDestKey.get());
      change.setDest(newDestKey);

      updateApprovals(ctx, update, psId, projectKey);

      StringBuilder msgBuf = new StringBuilder();
      msgBuf.append("Change destination moved from ");
      msgBuf.append(changePrevDest.getShortName());
      msgBuf.append(" to ");
      msgBuf.append(newDestKey.getShortName());
      if (!Strings.isNullOrEmpty(input.message)) {
        msgBuf.append("\n\n");
        msgBuf.append(input.message);
      }
      ChangeMessage cmsg =
          ChangeMessagesUtil.newMessage(ctx, msgBuf.toString(), ChangeMessagesUtil.TAG_MOVE);
      cmUtil.addChangeMessage(ctx.getDb(), update, cmsg);

      return true;
    }

    /**
     * We have a long discussion about how to deal with its votes after moving a change from one
     * branch to another. In the end, we think only keeping the veto votes is the best way since
     * it's simple for us and less confusing for our users. See the discussion in the following
     * proposal: https://gerrit-review.googlesource.com/c/gerrit/+/129171
     */
    private void updateApprovals(
        ChangeContext ctx, ChangeUpdate update, PatchSet.Id psId, Project.NameKey project)
        throws IOException, OrmException {
      List<PatchSetApproval> approvals = new ArrayList<>();
      for (PatchSetApproval psa :
          approvalsUtil.byPatchSet(
              ctx.getDb(),
              ctx.getNotes(),
              userProvider.get(),
              psId,
              ctx.getRevWalk(),
              ctx.getRepoView().getConfig())) {
        ProjectState projectState = projectCache.checkedGet(project);
        LabelType type =
            projectState.getLabelTypes(ctx.getNotes(), ctx.getUser()).byLabel(psa.getLabelId());
        // Only keep veto votes, defined as votes where:
        // 1- the label function allows minimum values to block submission.
        // 2- the vote holds the minimum value.
        if (type.isMaxNegative(psa) && type.getFunction().isBlock()) {
          continue;
        }

        // Remove votes from NoteDb.
        update.removeApprovalFor(psa.getAccountId(), psa.getLabel());
        approvals.add(
            new PatchSetApproval(
                new PatchSetApproval.Key(psId, psa.getAccountId(), new LabelId(psa.getLabel())),
                (short) 0,
                ctx.getWhen()));
      }
      // Remove votes from ReviewDb.
      ctx.getDb().patchSetApprovals().upsert(approvals);
    }
  }

  @Override
  public UiAction.Description getDescription(ChangeResource rsrc) {
    UiAction.Description description =
        new UiAction.Description()
            .setLabel("Move Change")
            .setTitle("Move change to a different branch")
            .setVisible(false);

    Change change = rsrc.getChange();
    if (!change.getStatus().isOpen()) {
      return description;
    }

    try {
      if (!projectCache.checkedGet(rsrc.getProject()).statePermitsWrite()) {
        return description;
      }
    } catch (IOException e) {
      log.error("Failed to check if project state permits write: " + rsrc.getProject(), e);
      return description;
    }

    try {
      if (isPatchSetLocked(
          approvalsUtil, projectCache, dbProvider.get(), rsrc.getNotes(), rsrc.getUser())) {
        return description;
      }
    } catch (OrmException | IOException e) {
      log.error(
          String.format(
              "Failed to check if the current patch set of change %s is locked", change.getId()),
          e);
      return description;
    }

    return description.setVisible(
        and(
            permissionBackend.user(rsrc.getUser()).ref(change.getDest()).testCond(CREATE_CHANGE),
            rsrc.permissions().database(dbProvider).testCond(ABANDON)));
  }
}
