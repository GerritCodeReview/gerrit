// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.plugins.checks.api;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestCollection;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.plugins.checks.AdministrateCheckersPermission;
import com.google.gerrit.plugins.checks.Checker;
import com.google.gerrit.plugins.checks.Checkers;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class CheckersCollection implements RestCollection<TopLevelResource, CheckerResource> {
  private final Provider<CurrentUser> self;
  private final PermissionBackend permissionBackend;
  private final ListCheckers listCheckers;
  private final Checkers checkers;
  private final DynamicMap<RestView<CheckerResource>> views;
  private final AdministrateCheckersPermission permission;

  @Inject
  public CheckersCollection(
      Provider<CurrentUser> self,
      PermissionBackend permissionBackend,
      ListCheckers listCheckers,
      Checkers checkers,
      DynamicMap<RestView<CheckerResource>> views,
      AdministrateCheckersPermission permission) {
    this.self = self;
    this.permissionBackend = permissionBackend;
    this.listCheckers = listCheckers;
    this.checkers = checkers;
    this.views = views;
    this.permission = permission;
  }

  @Override
  public RestView<TopLevelResource> list() throws RestApiException {
    return listCheckers;
  }

  @Override
  public CheckerResource parse(TopLevelResource parent, IdString id)
      throws AuthException, ResourceNotFoundException, PermissionBackendException, IOException,
          ConfigInvalidException {
    CurrentUser user = self.get();
    if (user instanceof AnonymousUser) {
      throw new AuthException("Authentication required");
    } else if (!(user.isIdentifiedUser())) {
      throw new ResourceNotFoundException(id);
    }

    permissionBackend.currentUser().check(permission);

    Checker checker =
        checkers.getChecker(id.get()).orElseThrow(() -> new ResourceNotFoundException(id));
    return new CheckerResource(checker);
  }

  @Override
  public DynamicMap<RestView<CheckerResource>> views() {
    return views;
  }
}
