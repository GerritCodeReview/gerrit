// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.restapi.account;

import static com.google.gerrit.server.permissions.DefaultPermissionMappings.globalOrPluginPermissionName;

import com.google.gerrit.extensions.api.access.GlobalOrPluginPermission;
import com.google.gerrit.extensions.api.access.PluginPermission;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.account.AccountResource.Capability;
import com.google.gerrit.server.permissions.DefaultPermissionMappings;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.Optional;

@Singleton
class Capabilities implements ChildCollection<AccountResource, AccountResource.Capability> {
  private final Provider<CurrentUser> self;
  private final PermissionBackend permissionBackend;
  private final DynamicMap<RestView<AccountResource.Capability>> views;
  private final Provider<GetCapabilities> get;

  @Inject
  Capabilities(
      Provider<CurrentUser> self,
      PermissionBackend permissionBackend,
      DynamicMap<RestView<AccountResource.Capability>> views,
      Provider<GetCapabilities> get) {
    this.self = self;
    this.permissionBackend = permissionBackend;
    this.views = views;
    this.get = get;
  }

  @Override
  public GetCapabilities list() throws ResourceNotFoundException {
    return get.get();
  }

  @Override
  public Capability parse(AccountResource parent, IdString id)
      throws ResourceNotFoundException, AuthException, PermissionBackendException {
    permissionBackend.checkUsesDefaultCapabilities();
    IdentifiedUser target = parent.getUser();
    if (self.get() != target) {
      permissionBackend.currentUser().check(GlobalPermission.ADMINISTRATE_SERVER);
    }

    GlobalOrPluginPermission perm = parse(id);
    if (permissionBackend.user(target).test(perm)) {
      return new AccountResource.Capability(target, globalOrPluginPermissionName(perm));
    }
    throw new ResourceNotFoundException(id);
  }

  private GlobalOrPluginPermission parse(IdString id) throws ResourceNotFoundException {
    String name = id.get();
    Optional<GlobalPermission> perm = DefaultPermissionMappings.globalPermission(name);
    if (perm.isPresent()) {
      return perm.get();
    }

    int dash = name.lastIndexOf('-');
    if (dash < 0) {
      throw new ResourceNotFoundException(id);
    }

    String pluginName = name.substring(0, dash);
    String capability = name.substring(dash + 1);
    if (pluginName.isEmpty() || capability.isEmpty()) {
      throw new ResourceNotFoundException(id);
    }
    return new PluginPermission(pluginName, capability);
  }

  @Override
  public DynamicMap<RestView<Capability>> views() {
    return views;
  }
}
