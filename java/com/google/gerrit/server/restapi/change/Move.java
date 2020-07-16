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
import static com.google.gerrit.server.permissions.ChangePermission.ABANDON;
import static com.google.gerrit.server.permissions.RefPermission.CREATE_CHANGE;
import static com.google.gerrit.server.project.ProjectCache.illegalState;
import static com.google.gerrit.server.query.change.ChangeData.asChanges;

import com.google.common.base.Strings;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.ChangeMessage;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.api.changes.MoveInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.config.GerritServerConfig;
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
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

@Singleton
public class Move implements RestModifyView<ChangeResource, MoveInput>, UiAction<ChangeResource> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final PermissionBackend permissionBackend;
  private final BatchUpdate.Factory updateFactory;
  private final ChangeJson.Factory json;
  private final GitRepositoryManager repoManager;
  private final Provider<InternalChangeQuery> queryProvider;
  private final ChangeMessagesUtil cmUtil;
  private final PatchSetUtil psUtil;
  private final ApprovalsUtil approvalsUtil;
  private final ProjectCache projectCache;
  private final boolean moveEnabled;

  @Inject
  Move(
      PermissionBackend permissionBackend,
      BatchUpdate.Factory updateFactory,
      ChangeJson.Factory json,
      GitRepositoryManager repoManager,
      Provider<InternalChangeQuery> queryProvider,
      ChangeMessagesUtil cmUtil,
      PatchSetUtil psUtil,
      ApprovalsUtil approvalsUtil,
      ProjectCache projectCache,
      @GerritServerConfig Config gerritConfig) {
    this.permissionBackend = permissionBackend;
    this.updateFactory = updateFactory;
    this.json = json;
    this.repoManager = repoManager;
    this.queryProvider = queryProvider;
    this.cmUtil = cmUtil;
    this.psUtil = psUtil;
    this.approvalsUtil = approvalsUtil;
    this.projectCache = projectCache;
    this.moveEnabled = gerritConfig.getBoolean("change", null, "move", true);
  }

  @Override
  public Response<ChangeInfo> apply(ChangeResource rsrc, MoveInput input)
      throws RestApiException, UpdateException, PermissionBackendException, IOException {
    if (!moveEnabled) {
      // This will be removed with the above config once we reach consensus for the move change
      // behavior. See: https://bugs.chromium.org/p/gerrit/issues/detail?id=9877
      throw new MethodNotAllowedException("move changes endpoint is disabled");
    }

    Change change = rsrc.getChange();
    Project.NameKey project = rsrc.getProject();
    IdentifiedUser caller = rsrc.getUser().asIdentifiedUser();
    if (input.destinationBranch == null) {
      throw new BadRequestException("destination branch is required");
    }
    input.destinationBranch = RefNames.fullName(input.destinationBranch);

    if (!change.isNew()) {
      throw new ResourceConflictException("Change is " + ChangeUtil.status(change));
    }

    BranchNameKey newDest = BranchNameKey.create(project, input.destinationBranch);
    if (change.getDest().equals(newDest)) {
      throw new ResourceConflictException("Change is already destined for the specified branch");
    }

    // Not allowed to move if the current patch set is locked.
    psUtil.checkPatchSetNotLocked(rsrc.getNotes());

    // Move requires abandoning this change, and creating a new change.
    try {
      rsrc.permissions().check(ABANDON);
      permissionBackend.user(caller).ref(newDest).check(CREATE_CHANGE);
    } catch (AuthException denied) {
      throw new AuthException("move not permitted", denied);
    }
    projectCache.get(project).orElseThrow(illegalState(project)).checkStatePermitsWrite();

    Op op = new Op(input);
    try (BatchUpdate u = updateFactory.create(project, caller, TimeUtil.nowTs())) {
      u.addOp(change.getId(), op);
      u.execute();
    }
    return Response.ok(json.noOptions().format(op.getChange()));
  }

  private class Op implements BatchUpdateOp {
    private final MoveInput input;

    private Change change;
    private BranchNameKey newDestKey;

    Op(MoveInput input) {
      this.input = input;
    }

    @Nullable
    public Change getChange() {
      return change;
    }

    @Override
    public boolean updateChange(ChangeContext ctx) throws ResourceConflictException, IOException {
      change = ctx.getChange();
      if (!change.isNew()) {
        throw new ResourceConflictException("Change is " + ChangeUtil.status(change));
      }

      Project.NameKey projectKey = change.getProject();
      newDestKey = BranchNameKey.create(projectKey, input.destinationBranch);
      BranchNameKey changePrevDest = change.getDest();
      if (changePrevDest.equals(newDestKey)) {
        throw new ResourceConflictException("Change is already destined for the specified branch");
      }

      final PatchSet.Id patchSetId = change.currentPatchSetId();
      try (Repository repo = repoManager.openRepository(projectKey);
          RevWalk revWalk = new RevWalk(repo)) {
        RevCommit currPatchsetRevCommit =
            revWalk.parseCommit(psUtil.current(ctx.getNotes()).commitId());
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
                + newDestKey.shortName()
                + " has a different change with same change key "
                + changeKey);
      }

      if (!change.currentPatchSetId().equals(patchSetId)) {
        throw new ResourceConflictException("Patch set is not current");
      }

      PatchSet.Id psId = change.currentPatchSetId();
      ChangeUpdate update = ctx.getUpdate(psId);
      update.setBranch(newDestKey.branch());
      change.setDest(newDestKey);

      updateApprovals(ctx, update, psId, projectKey);

      StringBuilder msgBuf = new StringBuilder();
      msgBuf.append("Change destination moved from ");
      msgBuf.append(changePrevDest.shortName());
      msgBuf.append(" to ");
      msgBuf.append(newDestKey.shortName());
      if (!Strings.isNullOrEmpty(input.message)) {
        msgBuf.append("\n\n");
        msgBuf.append(input.message);
      }
      ChangeMessage cmsg =
          ChangeMessagesUtil.newMessage(ctx, msgBuf.toString(), ChangeMessagesUtil.TAG_MOVE);
      cmUtil.addChangeMessage(update, cmsg);

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
        throws IOException {
      for (PatchSetApproval psa :
          approvalsUtil.byPatchSet(
              ctx.getNotes(), psId, ctx.getRevWalk(), ctx.getRepoView().getConfig())) {
        ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
        LabelType type = projectState.getLabelTypes(ctx.getNotes()).byLabel(psa.labelId());
        // Only keep veto votes, defined as votes where:
        // 1- the label function allows minimum values to block submission.
        // 2- the vote holds the minimum value.
        if (type == null || (type.isMaxNegative(psa) && type.getFunction().isBlock())) {
          continue;
        }

        // Remove votes from NoteDb.
        update.removeApprovalFor(psa.accountId(), psa.label());
      }
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
    if (!change.isNew()) {
      return description;
    }

    try {
      if (!projectCache
          .get(rsrc.getProject())
          .orElseThrow(illegalState(rsrc.getProject()))
          .statePermitsWrite()) {
        return description;
      }
    } catch (StorageException e) {
      logger.atSevere().withCause(e).log(
          "Failed to check if project state permits write: %s", rsrc.getProject());
      return description;
    }

    try {
      if (psUtil.isPatchSetLocked(rsrc.getNotes())) {
        return description;
      }
    } catch (StorageException e) {
      logger.atSevere().withCause(e).log(
          "Failed to check if the current patch set of change %s is locked", change.getId());
      return description;
    }

    return description.setVisible(
        and(
            permissionBackend.user(rsrc.getUser()).ref(change.getDest()).testCond(CREATE_CHANGE),
            rsrc.permissions().testCond(ABANDON)));
  }
}
