// Copyright (C) 2023 The Android Open Source Project
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

import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.CHANGE_MODIFICATION;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.extensions.api.changes.CustomKeyedValuesInput;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.SetCustomKeyedValuesOp;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.update.context.RefUpdateContext;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class PostCustomKeyedValues
    implements RestModifyView<ChangeResource, CustomKeyedValuesInput>, UiAction<ChangeResource> {
  private final BatchUpdate.Factory updateFactory;
  private final SetCustomKeyedValuesOp.Factory customKeyedValuesFactory;

  @Inject
  PostCustomKeyedValues(
      BatchUpdate.Factory updateFactory, SetCustomKeyedValuesOp.Factory customKeyedValuesFactory) {
    this.updateFactory = updateFactory;
    this.customKeyedValuesFactory = customKeyedValuesFactory;
  }

  @Override
  public Response<ImmutableMap<String, String>> apply(
      ChangeResource req, CustomKeyedValuesInput input)
      throws RestApiException, UpdateException, PermissionBackendException {
    req.permissions().check(ChangePermission.EDIT_CUSTOM_KEYED_VALUES);
    try (RefUpdateContext ctx = RefUpdateContext.open(CHANGE_MODIFICATION)) {
      try (BatchUpdate bu =
          updateFactory.create(req.getChange().getProject(), req.getUser(), TimeUtil.now())) {
        SetCustomKeyedValuesOp op = customKeyedValuesFactory.create(input);
        bu.addOp(req.getId(), op);
        bu.execute();
        return Response.ok(op.getUpdatedCustomKeyedValues());
      }
    }
  }

  @Override
  public UiAction.Description getDescription(ChangeResource rsrc) {
    return new UiAction.Description()
        .setLabel("Edit custom keyed values")
        .setVisible(rsrc.permissions().testCond(ChangePermission.EDIT_CUSTOM_KEYED_VALUES));
  }
}
