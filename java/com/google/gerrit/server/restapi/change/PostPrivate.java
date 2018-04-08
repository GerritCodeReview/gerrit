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

import static com.google.gerrit.extensions.conditions.BooleanCondition.and;
import static com.google.gerrit.extensions.conditions.BooleanCondition.or;

import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.config.GerritServerConfig;
import com.google.gerrit.extensions.conditions.BooleanCondition;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryingRestModifyView;
import com.google.gerrit.server.update.UpdateException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import org.eclipse.jgit.lib.Config;

@Singleton
public class PostPrivate
    extends RetryingRestModifyView<ChangeResource, SetPrivateOp.Input, Response<String>>
    implements UiAction<ChangeResource> {
  private final ChangeMessagesUtil cmUtil;
  private final Provider<ReviewDb> dbProvider;
  private final PermissionBackend permissionBackend;
  private final SetPrivateOp.Factory setPrivateOpFactory;
  private final boolean disablePrivateChanges;

  @Inject
  PostPrivate(
      Provider<ReviewDb> dbProvider,
      RetryHelper retryHelper,
      ChangeMessagesUtil cmUtil,
      PermissionBackend permissionBackend,
      SetPrivateOp.Factory setPrivateOpFactory,
      @GerritServerConfig Config config) {
    super(retryHelper);
    this.dbProvider = dbProvider;
    this.cmUtil = cmUtil;
    this.permissionBackend = permissionBackend;
    this.setPrivateOpFactory = setPrivateOpFactory;
    this.disablePrivateChanges = config.getBoolean("change", null, "disablePrivateChanges", false);
  }

  @Override
  public Response<String> applyImpl(
      BatchUpdate.Factory updateFactory, ChangeResource rsrc, SetPrivateOp.Input input)
      throws RestApiException, UpdateException {
    if (disablePrivateChanges) {
      throw new MethodNotAllowedException("private changes are disabled");
    }

    if (!canSetPrivate(rsrc).value()) {
      throw new AuthException("not allowed to mark private");
    }

    if (rsrc.getChange().isPrivate()) {
      return Response.ok("");
    }

    SetPrivateOp op = setPrivateOpFactory.create(cmUtil, true, input);
    try (BatchUpdate u =
        updateFactory.create(
            dbProvider.get(), rsrc.getProject(), rsrc.getUser(), TimeUtil.nowTs())) {
      u.addOp(rsrc.getId(), op).execute();
    }

    return Response.created("");
  }

  @Override
  public Description getDescription(ChangeResource rsrc) {
    Change change = rsrc.getChange();
    return new UiAction.Description()
        .setLabel("Mark private")
        .setTitle("Mark change as private")
        .setVisible(and(!disablePrivateChanges && !change.isPrivate(), canSetPrivate(rsrc)));
  }

  private BooleanCondition canSetPrivate(ChangeResource rsrc) {
    PermissionBackend.WithUser user = permissionBackend.user(rsrc.getUser());
    return or(
        rsrc.isUserOwner() && rsrc.getChange().getStatus() != Change.Status.MERGED,
        user.testCond(GlobalPermission.ADMINISTRATE_SERVER));
  }
}
