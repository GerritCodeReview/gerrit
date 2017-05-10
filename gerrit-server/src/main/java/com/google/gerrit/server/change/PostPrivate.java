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

import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryingRestModifyView;
import com.google.gerrit.server.update.UpdateException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@Singleton
public class PostPrivate
    extends RetryingRestModifyView<ChangeResource, SetPrivateOp.Input, Response<String>>
    implements UiAction<ChangeResource> {
  private final ChangeMessagesUtil cmUtil;
  private final Provider<ReviewDb> dbProvider;
  private final PermissionBackend permissionBackend;

  @Inject
  PostPrivate(
      Provider<ReviewDb> dbProvider,
      RetryHelper retryHelper,
      ChangeMessagesUtil cmUtil,
      PermissionBackend permissionBackend) {
    super(retryHelper);
    this.dbProvider = dbProvider;
    this.cmUtil = cmUtil;
    this.permissionBackend = permissionBackend;
  }

  @Override
  public Response<String> applyImpl(
      BatchUpdate.Factory updateFactory, ChangeResource rsrc, SetPrivateOp.Input input)
      throws RestApiException, UpdateException {
    if (!canSetPrivate(rsrc)) {
      throw new AuthException("not allowed to mark private");
    }

    if (rsrc.getChange().isPrivate()) {
      return Response.ok("");
    }

    ChangeControl control = rsrc.getControl();
    SetPrivateOp op = new SetPrivateOp(cmUtil, true, input);
    try (BatchUpdate u =
        updateFactory.create(
            dbProvider.get(),
            control.getProject().getNameKey(),
            control.getUser(),
            TimeUtil.nowTs())) {
      u.addOp(control.getId(), op).execute();
    }

    return Response.created("");
  }

  @Override
  public Description getDescription(ChangeResource rsrc) {
    Change change = rsrc.getChange();
    return new UiAction.Description()
        .setLabel("Mark private")
        .setTitle("Mark change as private")
        .setVisible(
            !change.isPrivate()
                && change.getStatus() != Change.Status.MERGED
                && canSetPrivate(rsrc));
  }

  private boolean canSetPrivate(ChangeResource rsrc) {
    PermissionBackend.WithUser user = permissionBackend.user(rsrc.getUser());
    return rsrc.isUserOwner() || user.testOrFalse(GlobalPermission.ADMINISTRATE_SERVER);
  }
}
