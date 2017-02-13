// Copyright (C) 2013 The Android Open Source Project
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
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.change.DeleteChange.Input;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.Order;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryingRestModifyView;
import com.google.gerrit.server.update.UpdateException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@Singleton
public class DeleteChange extends RetryingRestModifyView<ChangeResource, Input, Response<?>>
    implements UiAction<ChangeResource> {
  public static class Input {}

  private final Provider<ReviewDb> db;
  private final Provider<DeleteChangeOp> opProvider;

  @Inject
  public DeleteChange(
      Provider<ReviewDb> db, RetryHelper retryHelper, Provider<DeleteChangeOp> opProvider) {
    super(retryHelper);
    this.db = db;
    this.opProvider = opProvider;
  }

  @Override
  protected Response<?> applyImpl(
      BatchUpdate.Factory updateFactory, ChangeResource rsrc, Input input)
      throws RestApiException, UpdateException, PermissionBackendException {
    if (rsrc.getChange().getStatus() == Change.Status.MERGED) {
      throw new MethodNotAllowedException("delete not permitted");
    }
    rsrc.permissions().database(db).check(ChangePermission.DELETE);

    try (BatchUpdate bu =
        updateFactory.create(db.get(), rsrc.getProject(), rsrc.getUser(), TimeUtil.nowTs())) {
      Change.Id id = rsrc.getChange().getId();
      bu.setOrder(Order.DB_BEFORE_REPO);
      bu.addOp(id, opProvider.get());
      bu.execute();
    }
    return Response.none();
  }

  @Override
  public UiAction.Description getDescription(ChangeResource rsrc) {
    Change.Status status = rsrc.getChange().getStatus();
    PermissionBackend.ForChange perm = rsrc.permissions().database(db);
    return new UiAction.Description()
        .setLabel("Delete")
        .setTitle("Delete change " + rsrc.getId())
        .setVisible(couldDeleteWhenIn(status) && perm.testOrFalse(ChangePermission.DELETE));
  }

  private boolean couldDeleteWhenIn(Change.Status status) {
    switch (status) {
      case NEW:
      case ABANDONED:
        // New or abandoned changes can be deleted with the right permissions.
        return true;

      case MERGED:
        // Merged changes should never be deleted.
        return false;
    }
    return false;
  }
}
