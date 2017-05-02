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
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryingRestModifyView;
import com.google.gerrit.server.update.UpdateException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@Singleton
public class DeletePrivate
    extends RetryingRestModifyView<ChangeResource, DeletePrivate.Input, Response<String>>
    implements UiAction<ChangeResource> {
  public static class Input {}

  private final ChangeMessagesUtil cmUtil;
  private final Provider<ReviewDb> dbProvider;

  @Inject
  DeletePrivate(Provider<ReviewDb> dbProvider, RetryHelper retryHelper, ChangeMessagesUtil cmUtil) {
    super(retryHelper);
    this.dbProvider = dbProvider;
    this.cmUtil = cmUtil;
  }

  @Override
  protected Response<String> applyImpl(
      BatchUpdate.Factory updateFactory, ChangeResource rsrc, DeletePrivate.Input input)
      throws RestApiException, UpdateException {
    if (!rsrc.isUserOwner()) {
      throw new AuthException("not allowed to unmark private");
    }

    if (!rsrc.getChange().isPrivate()) {
      throw new ResourceConflictException("change is not private");
    }

    ChangeControl control = rsrc.getControl();
    SetPrivateOp op = new SetPrivateOp(cmUtil, false);
    try (BatchUpdate u =
        updateFactory.create(
            dbProvider.get(),
            control.getProject().getNameKey(),
            control.getUser(),
            TimeUtil.nowTs())) {
      u.addOp(control.getId(), op).execute();
    }

    return Response.none();
  }

  @Override
  public Description getDescription(ChangeResource rsrc) {
    return new UiAction.Description()
        .setLabel("Unmark private")
        .setTitle("Unmark change as private")
        .setVisible(rsrc.getChange().isPrivate() && rsrc.isUserOwner());
  }
}
