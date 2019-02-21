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

import static java.util.stream.Collectors.toList;

import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.plugins.checks.AdministrateCheckersPermission;
import com.google.gerrit.plugins.checks.CheckerJson;
import com.google.gerrit.plugins.checks.Checkers;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;

@Singleton
public class ListCheckers implements RestReadView<TopLevelResource> {
  private final PermissionBackend permissionBackend;
  private final Checkers checkers;
  private final CheckerJson checkerJson;
  private final AdministrateCheckersPermission permission;

  @Inject
  public ListCheckers(
      PermissionBackend permissionBackend,
      Checkers checkers,
      CheckerJson checkerJson,
      AdministrateCheckersPermission permission) {
    this.permissionBackend = permissionBackend;
    this.checkers = checkers;
    this.checkerJson = checkerJson;
    this.permission = permission;
  }

  @Override
  public List<CheckerInfo> apply(TopLevelResource resource)
      throws RestApiException, PermissionBackendException, IOException {
    permissionBackend.currentUser().check(permission);

    return checkers.listCheckers().stream().map(checkerJson::format).collect(toList());
  }
}
