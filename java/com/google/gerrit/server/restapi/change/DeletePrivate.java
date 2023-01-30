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
import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.CHANGE_MODIFICATION;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.common.InputWithMessage;
import com.google.gerrit.extensions.conditions.BooleanCondition;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.SetPrivateOp;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.update.context.RefUpdateContext;
import com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class DeletePrivate implements RestModifyView<ChangeResource, InputWithMessage> {
  private final PermissionBackend permissionBackend;
  private final BatchUpdate.Factory updateFactory;
  private final SetPrivateOp.Factory setPrivateOpFactory;

  @Inject
  DeletePrivate(
      PermissionBackend permissionBackend,
      BatchUpdate.Factory updateFactory,
      SetPrivateOp.Factory setPrivateOpFactory) {
    this.permissionBackend = permissionBackend;
    this.updateFactory = updateFactory;
    this.setPrivateOpFactory = setPrivateOpFactory;
  }

  @Override
  public Response<String> apply(ChangeResource rsrc, @Nullable InputWithMessage input)
      throws RestApiException, UpdateException {
    if (!canDeletePrivate(rsrc).value()) {
      throw new AuthException("not allowed to unmark private");
    }

    if (!rsrc.getChange().isPrivate()) {
      throw new ResourceConflictException("change is not private");
    }

    SetPrivateOp op = setPrivateOpFactory.create(false, input);
    try(RefUpdateContext ctx = RefUpdateContext.open(CHANGE_MODIFICATION)) {
      try (BatchUpdate u = updateFactory.create(rsrc.getProject(), rsrc.getUser(),
          TimeUtil.now())) {
        u.addOp(rsrc.getId(), op).execute();
      }
    }

    return Response.none();
  }

  protected BooleanCondition canDeletePrivate(ChangeResource rsrc) {
    PermissionBackend.WithUser user = permissionBackend.user(rsrc.getUser());
    return or(rsrc.isUserOwner(), user.testCond(GlobalPermission.ADMINISTRATE_SERVER));
  }
}
