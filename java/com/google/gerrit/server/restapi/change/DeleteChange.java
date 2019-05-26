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

package com.google.gerrit.server.restapi.change;

import static com.google.gerrit.extensions.conditions.BooleanCondition.and;

import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.common.Input;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.DeleteChangeOp;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryingRestModifyView;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class DeleteChange extends RetryingRestModifyView<ChangeResource, Input, Object>
    implements UiAction<ChangeResource> {

  private final DeleteChangeOp.Factory opFactory;

  @Inject
  public DeleteChange(RetryHelper retryHelper, DeleteChangeOp.Factory opFactory) {
    super(retryHelper);
    this.opFactory = opFactory;
  }

  @Override
  protected Response<Object> applyImpl(
      BatchUpdate.Factory updateFactory, ChangeResource rsrc, Input input)
      throws RestApiException, UpdateException, PermissionBackendException {
    if (!isChangeDeletable(rsrc)) {
      throw new MethodNotAllowedException("delete not permitted");
    }
    rsrc.permissions().check(ChangePermission.DELETE);

    try (BatchUpdate bu =
        updateFactory.create(rsrc.getProject(), rsrc.getUser(), TimeUtil.nowTs())) {
      Change.Id id = rsrc.getChange().getId();
      bu.addOp(id, opFactory.create(id));
      bu.execute();
    }
    return Response.none();
  }

  @Override
  public UiAction.Description getDescription(ChangeResource rsrc) {
    PermissionBackend.ForChange perm = rsrc.permissions();
    return new UiAction.Description()
        .setLabel("Delete")
        .setTitle("Delete change " + rsrc.getId())
        .setVisible(and(isChangeDeletable(rsrc), perm.testCond(ChangePermission.DELETE)));
  }

  private static boolean isChangeDeletable(ChangeResource rsrc) {
    Change change = rsrc.getChange();
    if (change.isMerged()) {
      // Merged changes should never be deleted.
      return false;
    }
    // New or abandoned changes can be deleted with the right permissions.
    return true;
  }
}
