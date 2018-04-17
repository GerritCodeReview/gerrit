// Copyright (C) 2018 The Android Open Source Project
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

import com.google.gerrit.extensions.api.config.AccessCheckInfo;
import com.google.gerrit.extensions.api.config.AccessCheckInput;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gwtorm.server.OrmException;
import java.io.IOException;
import javax.inject.Inject;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.kohsuke.args4j.Option;

public class CheckAccessReadView implements RestReadView<ProjectResource> {
  String refName;
  String account;
  String permission;

  @Inject CheckAccess checkAccess;

  @Option(name = "--ref", usage = "ref name to check permission for")
  void addOption(String refName) {
    this.refName = refName;
  }

  @Option(name = "--account", usage = "account to check acccess for")
  void setAccount(String account) {
    this.account = account;
  }

  @Option(name = "--perm", usage = "permission to check; default: read of any ref.")
  void setPermission(String perm) {
    this.permission = perm;
  }

  @Override
  public AccessCheckInfo apply(ProjectResource rsrc)
      throws OrmException, PermissionBackendException, RestApiException, IOException,
          ConfigInvalidException {

    AccessCheckInput input = new AccessCheckInput();
    input.ref = refName;
    input.account = account;
    input.permission = permission;

    return checkAccess.apply(rsrc, input);
  }
}
