// Copyright (C) 2017 The Android Open Source Project
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

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.gerrit.extensions.conditions.BooleanCondition.and;
import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.CHANGE_MODIFICATION;

import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.NotifyResolver;
import com.google.gerrit.server.change.WorkInProgressOp;
import com.google.gerrit.server.change.WorkInProgressOp.Input;
import com.google.gerrit.server.git.CommitUtil;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.update.context.RefUpdateContext;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class SetReadyForReview
    implements RestModifyView<ChangeResource, Input>, UiAction<ChangeResource> {
  private final BatchUpdate.Factory updateFactory;
  private final WorkInProgressOp.Factory opFactory;
  private final CommitUtil commitUtil;
  private final IdentifiedUser.GenericFactory genericUserFactory;

  @Inject
  SetReadyForReview(
      BatchUpdate.Factory updateFactory,
      WorkInProgressOp.Factory opFactory,
      CommitUtil commitUtil,
      IdentifiedUser.GenericFactory genericUserFactory) {
    this.updateFactory = updateFactory;
    this.opFactory = opFactory;
    this.commitUtil = commitUtil;
    this.genericUserFactory = genericUserFactory;
  }

  @Override
  public Response<String> apply(ChangeResource rsrc, Input input)
      throws RestApiException, UpdateException, PermissionBackendException {
    rsrc.permissions().check(ChangePermission.TOGGLE_WORK_IN_PROGRESS_STATE);

    Change change = rsrc.getChange();
    if (!change.isNew()) {
      throw new ResourceConflictException("change is " + ChangeUtil.status(change));
    }

    if (!change.isWorkInProgress()) {
      throw new ResourceConflictException("change is not work in progress");
    }
    try (RefUpdateContext ctx = RefUpdateContext.open(CHANGE_MODIFICATION)) {
      try (BatchUpdate bu =
          updateFactory.create(rsrc.getProject(), rsrc.getUser(), TimeUtil.now())) {
        bu.setNotify(NotifyResolver.Result.create(firstNonNull(input.notify, NotifyHandling.ALL)));
        bu.addOp(rsrc.getChange().getId(), opFactory.create(false, input));

        // If a revert change was created as work-in-progress notifications about the revert got
        // suppressed. We send these notifications now when the change is marked as ready.
        // The notifications should be sent from the user that created the revert change. If this is
        // the same user as the user that is marking the change as ready, we can send the
        // notifications here in the same BatchUpdate, otherwise we need to do this in a separate
        // BatchUpdate below.
        if (change.getRevertOf() != null
            && change.getOwner().equals(rsrc.getUser().asIdentifiedUser().getAccountId())) {
          commitUtil.addChangeRevertedNotificationOps(
              bu, change.getRevertOf(), change.getId(), change.getKey().get().substring(1));
        }
        bu.execute();
      }

      // If a revert change that was created by another user is marked as ready, the revert
      // notifications should be sent from that user.
      // Since the user is different from the user that is marking the change as ready, we need to
      // create a new BatchUpdate.
      if (change.getRevertOf() != null
          && !change.getOwner().equals(rsrc.getUser().asIdentifiedUser().getAccountId())) {
        try (BatchUpdate bu =
            updateFactory.create(
                rsrc.getProject(), genericUserFactory.create(change.getOwner()), TimeUtil.now())) {
          bu.setNotify(
              NotifyResolver.Result.create(firstNonNull(input.notify, NotifyHandling.ALL)));
          commitUtil.addChangeRevertedNotificationOps(
              bu, change.getRevertOf(), change.getId(), change.getKey().get().substring(1));
          bu.execute();
        }
      }
      return Response.ok();
    }
  }

  @Override
  public Description getDescription(ChangeResource rsrc) {
    return new Description()
        .setLabel("Mark as Active")
        .setTitle("Switch change state from WIP to Active (ready for review)")
        .setVisible(
            and(
                rsrc.getChange().isNew() && rsrc.getChange().isWorkInProgress(),
                rsrc.permissions().testCond(ChangePermission.TOGGLE_WORK_IN_PROGRESS_STATE)));
  }
}
