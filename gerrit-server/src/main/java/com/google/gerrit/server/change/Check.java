// Copyright (C) 2014 The Android Open Source Project
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

import com.google.gerrit.extensions.api.changes.FixInput;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

public class Check
    implements RestReadView<ChangeResource>, RestModifyView<ChangeResource, FixInput> {
  private final PermissionBackend permissionBackend;
  private final Provider<CurrentUser> user;
  private final ChangeJson.Factory jsonFactory;

  @Inject
  Check(PermissionBackend permissionBackend, Provider<CurrentUser> user, ChangeJson.Factory json) {
    this.permissionBackend = permissionBackend;
    this.user = user;
    this.jsonFactory = json;
  }

  @Override
  public Response<ChangeInfo> apply(ChangeResource rsrc) throws RestApiException, OrmException {
    return Response.withMustRevalidate(newChangeJson().format(rsrc));
  }

  @Override
  public Response<ChangeInfo> apply(ChangeResource rsrc, FixInput input)
      throws RestApiException, OrmException, PermissionBackendException {
    if (!rsrc.isUserOwner() && !rsrc.getControl().getProjectControl().isOwner()) {
      permissionBackend.user(user).check(GlobalPermission.MAINTAIN_SERVER);
    }
    return Response.withMustRevalidate(newChangeJson().fix(input).format(rsrc));
  }

  private ChangeJson newChangeJson() {
    return jsonFactory.create(ListChangesOption.CHECK);
  }
}
