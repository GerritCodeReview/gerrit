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

package com.google.gerrit.server.project;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.permissions.FailedPermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;

@Singleton
class DefaultPermissionBackend extends PermissionBackend {
  private final ProjectCache projectCache;

  @Inject
  DefaultPermissionBackend(ProjectCache projectCache) {
    this.projectCache = projectCache;
  }

  @Override
  public WithUser user(CurrentUser user) {
    return new WithUserImpl(checkNotNull(user, "user"));
  }

  class WithUserImpl extends WithUser {
    private final CurrentUser user;

    WithUserImpl(CurrentUser user) {
      this.user = checkNotNull(user, "user");
    }

    @Override
    public ForProject project(Project.NameKey project) {
      try {
        ProjectState state = projectCache.checkedGet(project);
        if (state != null) {
          return state.controlFor(user).asForProject().database(db);
        }
        return FailedPermissionBackend.project("not found");
      } catch (IOException e) {
        return FailedPermissionBackend.project("unavailable", e);
      }
    }
  }
}
