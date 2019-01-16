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

package com.google.gerrit.server.restapi.change;

import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.api.changes.FixInput;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;

@Singleton
public class Check
    implements RestReadView<ChangeResource>, RestModifyView<ChangeResource, FixInput> {
  private final PermissionBackend permissionBackend;
  private final ChangeJson.Factory jsonFactory;

  @Inject
  Check(PermissionBackend permissionBackend, ChangeJson.Factory json) {
    this.permissionBackend = permissionBackend;
    this.jsonFactory = json;
  }

  @Override
  public Response<ChangeInfo> apply(ChangeResource rsrc) throws RestApiException, StorageException {
    return Response.withMustRevalidate(newChangeJson().format(rsrc));
  }

  @Override
  public Response<ChangeInfo> apply(ChangeResource rsrc, FixInput input)
      throws RestApiException, StorageException, PermissionBackendException, NoSuchProjectException,
          IOException {
    PermissionBackend.WithUser perm = permissionBackend.currentUser();
    if (!rsrc.isUserOwner()) {
      try {
        perm.project(rsrc.getProject()).check(ProjectPermission.READ_CONFIG);
      } catch (AuthException e) {
        perm.check(GlobalPermission.MAINTAIN_SERVER);
      }
    }
    return Response.withMustRevalidate(newChangeJson().fix(input).format(rsrc));
  }

  private ChangeJson newChangeJson() {
    return jsonFactory.create(ListChangesOption.CHECK);
  }
}
