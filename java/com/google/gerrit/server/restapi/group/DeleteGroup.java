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

import com.google.gerrit.extensions.common.Input;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.NotImplementedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.group.GroupResource;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class DeleteGroup implements RestModifyView<GroupResource, Input> {
  private final Provider<IdentifiedUser> user;
  private final PermissionBackend permissionBackend;
  private final Provider<ListGroups> listProvider;

  @Inject
  DeleteGroup(
      Provider<IdentifiedUser> user,
      PermissionBackend permissionBackend,
      Provider<ListGroups> listProvider) {
    this.user = user;
    this.permissionBackend = permissionBackend;
    this.listProvider = listProvider;
  }

  @Override
  public Response<?> apply(GroupResource resource, Input input) throws RestApiException {
    try {
      permissionBackend.user(user).check(GlobalPermission.ADMINISTRATE_SERVER);
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

    ListGroups list = listProvider.get();
    list.setOwnedBy(uuid.get());
    try {
      if (!list.get().isEmpty()) {
        throw new ResourceConflictException("cannot delete group that is owner of other groups");
      }
    } catch (OrmException | IOException | ConfigInvalidException e) {
      throw new RestApiException("unable to check group ownership", e);
    }

    // TODO should we check if the group is used in ref permissions?

    // TODO actually delete the group...
    throw new NotImplementedException();
  }
}
