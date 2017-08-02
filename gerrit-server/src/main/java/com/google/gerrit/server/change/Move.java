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

import static com.google.gerrit.server.permissions.RefPermission.CREATE_CHANGE;
import static com.google.gerrit.server.query.change.ChangeData.asChanges;

import com.google.common.base.Strings;
import com.google.gerrit.common.TimeUtil;
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
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.RefPermission;
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
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

@Singleton
public class Move extends RetryingRestModifyView<ChangeResource, MoveInput, ChangeInfo>
    implements UiAction<ChangeResource> {
  private final PermissionBackend permissionBackend;
  private final Provider<ReviewDb> dbProvider;
  private final ChangeJson.Factory json;
  private final GitRepositoryManager repoManager;
  private final Provider<InternalChangeQuery> queryProvider;
  private final ChangeMessagesUtil cmUtil;
  private final PatchSetUtil psUtil;

  @Inject
  Move(
      PermissionBackend permissionBackend,
      Provider<ReviewDb> dbProvider,
      ChangeJson.Factory json,
      GitRepositoryManager repoManager,
      Provider<InternalChangeQuery> queryProvider,
      ChangeMessagesUtil cmUtil,
      RetryHelper retryHelper,
      PatchSetUtil psUtil) {
    super(retryHelper);
    this.permissionBackend = permissionBackend;
    this.dbProvider = dbProvider;
    this.json = json;
    this.repoManager = repoManager;
    this.queryProvider = queryProvider;
    this.cmUtil = cmUtil;
    this.psUtil = psUtil;
  }

  @Override
  protected ChangeInfo applyImpl(
      BatchUpdate.Factory updateFactory, ChangeResource rsrc, MoveInput input)
      throws RestApiException, OrmException, UpdateException, PermissionBackendException {
    Change change = rsrc.getChange();
    Project.NameKey project = rsrc.getProject();
    IdentifiedUser caller = rsrc.getUser();
    input.destinationBranch = RefNames.fullName(input.destinationBranch);

    if (change.getStatus().isClosed()) {
      throw new ResourceConflictException("Change is " + ChangeUtil.status(change));
    }

    Branch.NameKey newDest = new Branch.NameKey(project, input.destinationBranch);
    if (change.getDest().equals(newDest)) {
      throw new ResourceConflictException("Change is already destined for the specified branch");
    }

    // Move requires abandoning this change, and creating a new change.
    try {
      rsrc.permissions().database(dbProvider).check(ChangePermission.ABANDON);
      permissionBackend
          .user(caller)
          .database(dbProvider)
          .ref(newDest)
          .check(RefPermission.CREATE_CHANGE);
    } catch (AuthException denied) {
      throw new AuthException("move not permitted", denied);
    }

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
        throws OrmException, ResourceConflictException, RepositoryNotFoundException, IOException {
      change = ctx.getChange();
      if (change.getStatus() != Status.NEW && change.getStatus() != Status.DRAFT) {
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

      ChangeUpdate update = ctx.getUpdate(change.currentPatchSetId());
      update.setBranch(newDestKey.get());
      change.setDest(newDestKey);

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
  }

  @Override
  public UiAction.Description getDescription(ChangeResource rsrc) {
    Change change = rsrc.getChange();
    return new UiAction.Description()
        .setLabel("Move Change")
        .setTitle("Move change to a different branch")
        .setVisible(
            change.getStatus().isOpen()
                && permissionBackend
                    .user(rsrc.getUser())
                    .ref(change.getDest())
                    .testOrFalse(CREATE_CHANGE)
                && rsrc.permissions().database(dbProvider).testOrFalse(ChangePermission.ABANDON));
  }
}
