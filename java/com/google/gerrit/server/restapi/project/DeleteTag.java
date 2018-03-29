// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.restapi.project;

import static com.google.gerrit.reviewdb.client.RefNames.isConfigRef;

import com.google.gerrit.extensions.common.Input;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.project.RefUtil;
import com.google.gerrit.server.project.TagResource;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;

@Singleton
public class DeleteTag implements RestModifyView<TagResource, Input> {

  private final PermissionBackend permissionBackend;
  private final DeleteRef.Factory deleteRefFactory;

  @Inject
  DeleteTag(PermissionBackend permissionBackend, DeleteRef.Factory deleteRefFactory) {
    this.permissionBackend = permissionBackend;
    this.deleteRefFactory = deleteRefFactory;
  }

  @Override
  public Response<?> apply(TagResource resource, Input input)
      throws OrmException, RestApiException, IOException, PermissionBackendException {
    String tag = RefUtil.normalizeTagRef(resource.getTagInfo().ref);

    if (isConfigRef(tag)) {
      // Never allow to delete the meta config branch.
      throw new MethodNotAllowedException("not allowed to delete " + tag);
    }

    permissionBackend
        .currentUser()
        .project(resource.getNameKey())
        .ref(tag)
        .check(RefPermission.DELETE);
    resource.getProjectState().checkStatePermitsWrite();
    deleteRefFactory.create(resource).ref(tag).delete();
    return Response.none();
  }
}
