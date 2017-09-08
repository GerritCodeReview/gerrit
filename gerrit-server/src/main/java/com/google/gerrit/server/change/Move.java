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

import static com.google.gerrit.extensions.conditions.BooleanCondition.and;
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
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.permissions.LabelPermission;
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
import java.util.Collections;
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
  private final IdentifiedUser.GenericFactory userFactory;
  private final ApprovalsUtil approvalsUtil;
  private final ProjectCache projectCache;

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
      IdentifiedUser.GenericFactory userFactory,
      ApprovalsUtil approvalsUtil,
      ProjectCache projectCache) {
    super(retryHelper);
    this.permissionBackend = permissionBackend;
    this.dbProvider = dbProvider;
    this.json = json;
    this.repoManager = repoManager;
    this.queryProvider = queryProvider;
    this.cmUtil = cmUtil;
    this.psUtil = psUtil;
    this.userFactory = userFactory;
    this.approvalsUtil = approvalsUtil;
    this.projectCache = projectCache;
  }

  @Override
  protected ChangeInfo applyImpl(
      BatchUpdate.Factory updateFactory, ChangeResource rsrc, MoveInput input)
      throws RestApiException, OrmException, UpdateException, PermissionBackendException {
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

    // Move requires abandoning this change, and creating a new change.
    try {
      rsrc.permissions().database(dbProvider).check(ABANDON);
      permissionBackend.user(caller).database(dbProvider).ref(newDest).check(CREATE_CHANGE);
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
        throws OrmException, ResourceConflictException, IOException, PermissionBackendException {
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

      ChangeUpdate update = ctx.getUpdate(change.currentPatchSetId());
      update.setBranch(newDestKey.get());
      change.setDest(newDestKey);

      updateApprovals(ctx, update, patchSetId);

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

    private void updateApprovals(ChangeContext ctx, ChangeUpdate update, PatchSet.Id psId)
        throws OrmException, IOException, PermissionBackendException {
      for (PatchSetApproval psa :
          approvalsUtil.byPatchSet(
              ctx.getDb(),
              ctx.getNotes(),
              ctx.getUser(),
              psId,
              ctx.getRevWalk(),
              ctx.getRepoView().getConfig())) {
        ProjectState projectState = projectCache.checkedGet(ctx.getProject());
        LabelType lt =
            projectState.getLabelTypes(ctx.getNotes(), ctx.getUser()).byLabel(psa.getLabelId());
        if (lt == null) {
          continue; // Label no longer exists; leave it alone.
        }

        // Squash on the new branch based on the 'originalValue'.
        if (applyRightFloor(ctx.getNotes(), lt, psa)) {
          ctx.getDb().patchSetApprovals().upsert(Collections.singleton(psa));
          update.squashApprovalFor(
              psa.getAccountId(), psa.getLabel(), psa.getValue(), psa.getOriginalValue());
        }
      }
    }

    private boolean applyRightFloor(ChangeNotes notes, LabelType lt, PatchSetApproval a)
        throws PermissionBackendException {
      PermissionBackend.ForChange forChange =
          permissionBackend
              .user(userFactory.create(a.getAccountId()))
              .database(dbProvider.get())
              .change(notes);
      // Check if the user is allowed to vote on the label at all
      try {
        forChange.check(new LabelPermission(lt.getName()));
      } catch (AuthException e) {
        a.setValue((short) 0);
        return true;
      }
      // Squash vote to nearest allowed value
      try {
        // Check if the reviewer holds permission for the originalValue on the new branch.
        forChange.check(new LabelPermission.WithValue(lt.getName(), a.getOriginalValue()));
        // Restore the originalValue.
        if (a.getValue() != a.getOriginalValue()) {
          a.setValue(a.getOriginalValue());
          return true;
        }
        return false;
      } catch (AuthException e) {
        // Squash *original* value, so that moving a change from A->B->A returns all votes to their
        // original values.
        a.setValue(nearest(forChange.testLabels(Collections.singleton(lt)), a.getOriginalValue()));
        return true;
      }
    }

    private short nearest(Iterable<LabelPermission.WithValue> possible, short wanted) {
      short s = 0;
      for (LabelPermission.WithValue v : possible) {
        if ((wanted < 0 && v.value() < 0 && wanted <= v.value() && v.value() < s)
            || (wanted > 0 && v.value() > 0 && wanted >= v.value() && v.value() > s)) {
          s = v.value();
        }
      }
      return s;
    }
  }

  @Override
  public UiAction.Description getDescription(ChangeResource rsrc) {
    Change change = rsrc.getChange();
    return new UiAction.Description()
        .setLabel("Move Change")
        .setTitle("Move change to a different branch")
        .setVisible(
            and(
                change.getStatus().isOpen(),
                and(
                    permissionBackend
                        .user(rsrc.getUser())
                        .ref(change.getDest())
                        .testCond(CREATE_CHANGE),
                    rsrc.permissions().database(dbProvider).testCond(ABANDON))));
  }
}
