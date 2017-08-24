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

package com.google.gerrit.server.query.project;

import com.google.gerrit.index.query.IsVisibleToPredicate;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.index.IndexUtils;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.account.AccountQueryBuilder;
import com.google.gwtorm.server.OrmException;

public class ProjectIsVisibleToPredicate extends IsVisibleToPredicate<ProjectState> {
  protected final PermissionBackend permissionBackend;
  protected final CurrentUser user;

  public ProjectIsVisibleToPredicate(PermissionBackend permissionBackend, CurrentUser user) {
    super(AccountQueryBuilder.FIELD_VISIBLETO, IndexUtils.describe(user));
    this.permissionBackend = permissionBackend;
    this.user = user;
  }

  @Override
  public boolean match(ProjectState projectState) throws OrmException {
    return permissionBackend
        .user(user)
        .project(projectState.getProject().getNameKey())
        .testOrFalse(ProjectPermission.READ);
  }

  @Override
  public int getCost() {
    return 1;
  }
}
