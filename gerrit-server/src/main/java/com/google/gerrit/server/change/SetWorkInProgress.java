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

package com.google.gerrit.server.change;

import static com.google.gerrit.extensions.conditions.BooleanCondition.and;
import static com.google.gerrit.extensions.conditions.BooleanCondition.or;

import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Change.Status;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.change.WorkInProgressOp.Input;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryingRestModifyView;
import com.google.gerrit.server.update.UpdateException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;

@Singleton
public class SetWorkInProgress extends RetryingRestModifyView<ChangeResource, Input, Response<?>>
    implements UiAction<ChangeResource> {
  private final WorkInProgressOp.Factory opFactory;
  private final Provider<ReviewDb> db;
  private final Provider<CurrentUser> self;
  private final PermissionBackend permissionBackend;
  private final ProjectControl.GenericFactory projectControlFactory;

  @Inject
  SetWorkInProgress(
      WorkInProgressOp.Factory opFactory,
      RetryHelper retryHelper,
      Provider<ReviewDb> db,
      Provider<CurrentUser> self,
      PermissionBackend permissionBackend,
      ProjectControl.GenericFactory projectControlFactory) {
    super(retryHelper);
    this.opFactory = opFactory;
    this.db = db;
    this.self = self;
    this.permissionBackend = permissionBackend;
    this.projectControlFactory = projectControlFactory;
  }

  @Override
  protected Response<?> applyImpl(
      BatchUpdate.Factory updateFactory, ChangeResource rsrc, Input input)
      throws RestApiException, UpdateException, PermissionBackendException, NoSuchProjectException,
          IOException {
    Change change = rsrc.getChange();
    if (!rsrc.isUserOwner()
        && !permissionBackend.user(rsrc.getUser()).test(GlobalPermission.ADMINISTRATE_SERVER)
        && !projectControlFactory.controlFor(rsrc.getProject(), rsrc.getUser()).isOwner()) {
      throw new AuthException("not allowed to set work in progress");
    }

    if (change.getStatus() != Status.NEW) {
      throw new ResourceConflictException("change is " + ChangeUtil.status(change));
    }

    if (change.isWorkInProgress()) {
      throw new ResourceConflictException("change is already work in progress");
    }

    try (BatchUpdate bu =
        updateFactory.create(db.get(), rsrc.getProject(), rsrc.getUser(), TimeUtil.nowTs())) {
      bu.addOp(rsrc.getChange().getId(), opFactory.create(true, input));
      bu.execute();
      return Response.ok("");
    }
  }

  @Override
  public Description getDescription(ChangeResource rsrc) {
    return new Description()
        .setLabel("WIP")
        .setTitle("Set Work In Progress")
        .setVisible(
            and(
                rsrc.getChange().getStatus() == Status.NEW && !rsrc.getChange().isWorkInProgress(),
                or(
                    rsrc.isUserOwner(),
                    permissionBackend.user(self).testCond(GlobalPermission.ADMINISTRATE_SERVER))));
  }
}
