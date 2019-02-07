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

package com.google.gerrit.server.restapi.checker;

import static java.util.stream.Collectors.toList;

import com.google.gerrit.extensions.api.checkers.CheckerInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.server.checker.CheckerJson;
import com.google.gerrit.server.checker.Checkers;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;

@Singleton
public class ListCheckers implements RestReadView<TopLevelResource> {
  private final GlobalChecksConfig globalChecksConfig;
  private final PermissionBackend permissionBackend;
  private final Checkers checkers;
  private final CheckerJson checkerJson;

  @Inject
  public ListCheckers(
      GlobalChecksConfig globalChecksConfig,
      PermissionBackend permissionBackend,
      Checkers checkers,
      CheckerJson checkerJson) {
    this.globalChecksConfig = globalChecksConfig;
    this.permissionBackend = permissionBackend;
    this.checkers = checkers;
    this.checkerJson = checkerJson;
  }

  @Override
  public List<CheckerInfo> apply(TopLevelResource resource)
      throws RestApiException, PermissionBackendException, IOException {
    globalChecksConfig.checkThatApiIsEnabled();
    permissionBackend.currentUser().check(GlobalPermission.ADMINISTRATE_CHECKERS);

    return checkers.listCheckers().stream().map(checkerJson::format).collect(toList());
  }
}
