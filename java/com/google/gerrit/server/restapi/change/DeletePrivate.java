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

import static com.google.gerrit.extensions.conditions.BooleanCondition.or;

import com.google.gerrit.extensions.conditions.BooleanCondition;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryingRestModifyView;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class DeletePrivate
    extends RetryingRestModifyView<ChangeResource, SetPrivateOp.Input, Response<String>> {
  private final ChangeMessagesUtil cmUtil;
  private final PermissionBackend permissionBackend;
  private final SetPrivateOp.Factory setPrivateOpFactory;

  @Inject
  DeletePrivate(
      RetryHelper retryHelper,
      ChangeMessagesUtil cmUtil,
      PermissionBackend permissionBackend,
      SetPrivateOp.Factory setPrivateOpFactory) {
    super(retryHelper);
    this.cmUtil = cmUtil;
    this.permissionBackend = permissionBackend;
    this.setPrivateOpFactory = setPrivateOpFactory;
  }

  @Override
  protected Response<String> applyImpl(
      BatchUpdate.Factory updateFactory, ChangeResource rsrc, SetPrivateOp.Input input)
      throws RestApiException, UpdateException {
    if (!canDeletePrivate(rsrc).value()) {
      throw new AuthException("not allowed to unmark private");
    }

    if (!rsrc.getChange().isPrivate()) {
      throw new ResourceConflictException("change is not private");
    }

    SetPrivateOp op = setPrivateOpFactory.create(cmUtil, false, input);
    try (BatchUpdate u =
        updateFactory.create(rsrc.getProject(), rsrc.getUser(), TimeUtil.nowTs())) {
      u.addOp(rsrc.getId(), op).execute();
    }

    return Response.none();
  }

  protected BooleanCondition canDeletePrivate(ChangeResource rsrc) {
    PermissionBackend.WithUser user = permissionBackend.user(rsrc.getUser());
    return or(rsrc.isUserOwner(), user.testCond(GlobalPermission.ADMINISTRATE_SERVER));
  }
}
