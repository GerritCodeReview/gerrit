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

package com.google.gerrit.server.restapi.group;

import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.common.Input;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.AllUsersNameProvider;
import com.google.gerrit.server.group.DeleteGroupOp;
import com.google.gerrit.server.group.GroupResource;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@Singleton
public class DeleteGroup implements RestModifyView<GroupResource, Input>, UiAction<GroupResource> {
  private final Provider<IdentifiedUser> user;
  private final PermissionBackend permissionBackend;
  private final Provider<ListGroups> listProvider;
  private final AllUsersNameProvider allUsersNameProvider;
  private final DeleteGroupOp.Factory deleteGroupOp;
  private final BatchUpdate.Factory updateFactory;

  @Inject
  DeleteGroup(
      Provider<IdentifiedUser> user,
      PermissionBackend permissionBackend,
      Provider<ListGroups> listProvider,
      AllUsersNameProvider allUsersNameProvider,
      DeleteGroupOp.Factory deleteGroupOp,
      BatchUpdate.Factory updateFactory) {

    this.user = user;
    this.permissionBackend = permissionBackend;
    this.listProvider = listProvider;
    this.allUsersNameProvider = allUsersNameProvider;
    this.deleteGroupOp = deleteGroupOp;
    this.updateFactory = updateFactory;
  }

  @Override
  public Response<Object> apply(GroupResource resource, Input input)
      throws AuthException, BadRequestException, ResourceConflictException, Exception {
    try {
      permissionBackend.user(user.get()).check(GlobalPermission.ADMINISTRATE_SERVER);
    } catch (AuthException e) {
      throw new AuthException("cannot delete group", e);
    } catch (PermissionBackendException e) {
      throw new RestApiException("unable to check permission", e);
    }

    AccountGroup.UUID uuid = resource.getGroup().getGroupUUID();
    if (SystemGroupBackend.isSystemGroup(uuid)) {
      throw new ResourceConflictException("cannot delete system group");
    }

    if (!resource.isInternalGroup()) {
      throw new ResourceConflictException("cannot delete external group");
    }

    ListGroups listOwner = listProvider.get();
    ListGroups listOwned = listProvider.get();
    listOwner.setOwnedBy(uuid.get());
    listOwned.setOwned(true);
    boolean isOwner, isOwned;
    try {
      isOwner = !listOwner.get().isEmpty();
      isOwned = !listOwned.get().isEmpty();
    } catch (Exception e) {
      throw new RestApiException("unable to check group ownership", e);
    }
    if (isOwner) {
      throw new ResourceConflictException("cannot delete group that is owner of other groups");
    }
    /*
        if (isOwned) {
          throw new ResourceConflictException("cannot delete group that is owned by other groups");
        }
    */

    // TODO check if the group is used in ref permissions

    // TODO actually delete the group...

    try (BatchUpdate bu =
        updateFactory.create(
            Project.nameKey(allUsersNameProvider.get().get()),
            resource.getControl().getUser(),
            TimeUtil.nowTs())) {
      bu.addRepoOnlyOp(deleteGroupOp.create(resource.getGroup()));
      bu.execute();
    }

    return Response.none();
  }

  @Override
  public UiAction.Description getDescription(GroupResource rsrc) {
    return new UiAction.Description()
        .setLabel("Delete group")
        .setTitle("Delete group " + rsrc.getName())
        .setVisible(true);
    // TODO         .setVisible(and(isChangeDeletable(rsrc),
    // perm.testCond(ChangePermission.DELETE)));
  }
}
