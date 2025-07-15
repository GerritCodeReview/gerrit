// Copyright (C) 2025 The Android Open Source Project
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

import static com.google.gerrit.extensions.api.projects.DeleteChangesResult.FAILURE;
import static com.google.gerrit.extensions.api.projects.DeleteChangesResult.NOT_UNIQUE;
import static com.google.gerrit.extensions.api.projects.DeleteChangesResult.SUCCESS;
import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.CHANGE_MODIFICATION;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.api.projects.DeleteChangesInput;
import com.google.gerrit.extensions.api.projects.DeleteChangesResult;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.change.ChangeFinder;
import com.google.gerrit.server.change.DeleteChangeOp;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.update.context.RefUpdateContext;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class DeleteChanges implements RestModifyView<ProjectResource, DeleteChangesInput> {

  private final PermissionBackend permissionBackend;
  private final ChangeFinder changeFinder;
  private final BatchUpdate.Factory batchUpdateFactory;
  private final DeleteChangeOp.Factory opFactory;

  @Inject
  public DeleteChanges(
      PermissionBackend permissionBackend,
      ChangeFinder changeFinder,
      BatchUpdate.Factory batchUpdateFactory,
      DeleteChangeOp.Factory opFactory) {
    this.permissionBackend = permissionBackend;
    this.changeFinder = changeFinder;
    this.batchUpdateFactory = batchUpdateFactory;
    this.opFactory = opFactory;
  }

  @Override
  public Response<Map<DeleteChangesResult, Collection<String>>> apply(
      ProjectResource resource, DeleteChangesInput input)
      throws RestApiException, UpdateException, PermissionBackendException {
    if (input == null || input.changes == null || input.changes.isEmpty()) {
      throw new BadRequestException("Change Ids must be specified");
    }
    ListMultimap<DeleteChangesResult, String> responseBody = ArrayListMultimap.create();
    List<String> deletableChanges = new ArrayList<>();
    try (RefUpdateContext ctx = RefUpdateContext.open(CHANGE_MODIFICATION)) {
      try (BatchUpdate bu =
          batchUpdateFactory.create(resource.getNameKey(), resource.getUser(), TimeUtil.now())) {
        for (String change : input.changes) {
          List<ChangeNotes> cn = changeFinder.find(change);
          if (cn.isEmpty()) {
            responseBody.put(FAILURE, change);
          } else if (cn.size() > 1) {
            responseBody.put(NOT_UNIQUE, change);
          } else {
            if (isChangeDeletable(cn.getFirst())) {
              checkPermissions(cn);
              Change.Id changeId = cn.getFirst().getChange().getId();
              bu.addOp(changeId, opFactory.create(changeId));
              deletableChanges.add(change);
            } else {
              responseBody.put(FAILURE, change);
            }
          }
        }
        bu.execute();
        responseBody.putAll(SUCCESS, deletableChanges);
      }
    }
    return Response.ok(responseBody.asMap());
  }

  public void checkPermissions(List<ChangeNotes> cn)
      throws PermissionBackendException, AuthException {
    if (cn.getFirst() != null) {
      permissionBackend.currentUser().change(cn.getFirst()).check(ChangePermission.DELETE);
    }
  }

  private static boolean isChangeDeletable(ChangeNotes cn) {
    // Merged changes must not be deleted.
    // New or abandoned changes can be deleted with the right permissions.
    return !cn.getChange().isMerged();
  }
}
