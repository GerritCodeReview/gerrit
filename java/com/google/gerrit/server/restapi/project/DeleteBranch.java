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

package com.google.gerrit.server.restapi.project;

import static com.google.gerrit.reviewdb.client.RefNames.isConfigRef;
import static org.eclipse.jgit.lib.Constants.R_HEADS;

import com.google.gerrit.extensions.common.Input;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.project.BranchResource;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;

@Singleton
public class DeleteBranch implements RestModifyView<BranchResource, Input> {

  private final Provider<InternalChangeQuery> queryProvider;
  private final DeleteRef.Factory deleteRefFactory;
  private final PermissionBackend permissionBackend;

  @Inject
  DeleteBranch(
      Provider<InternalChangeQuery> queryProvider,
      DeleteRef.Factory deleteRefFactory,
      PermissionBackend permissionBackend) {
    this.queryProvider = queryProvider;
    this.deleteRefFactory = deleteRefFactory;
    this.permissionBackend = permissionBackend;
  }

  @Override
  public Response<?> apply(BranchResource rsrc, Input input)
      throws RestApiException, OrmException, IOException, PermissionBackendException {
    if (isConfigRef(rsrc.getBranchKey().get())) {
      // Never allow to delete the meta config branch.
      throw new MethodNotAllowedException(
          "not allowed to delete branch " + rsrc.getBranchKey().get());
    }

    permissionBackend.currentUser().ref(rsrc.getBranchKey()).check(RefPermission.DELETE);
    rsrc.getProjectState().checkStatePermitsWrite();

    if (!queryProvider.get().setLimit(1).byBranchOpen(rsrc.getBranchKey()).isEmpty()) {
      throw new ResourceConflictException("branch " + rsrc.getBranchKey() + " has open changes");
    }

    deleteRefFactory.create(rsrc).ref(rsrc.getRef()).prefix(R_HEADS).delete();
    return Response.none();
  }
}
