// Copyright (C) 2010 The Android Open Source Project
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
// limitations under the License

package com.google.gerrit.httpd.rpc.project;

import com.google.gerrit.common.CollectionsUtil;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.ProjectCreatorGroups;
import com.google.inject.Inject;

import java.util.Set;

public class CanCreateProject extends Handler<Boolean> {
  interface Factory {
    CanCreateProject create();
  }

  private final CurrentUser user;

  @Inject
  @ProjectCreatorGroups
  private Set<AccountGroup.Id> projectCreatorGroups;

  @Inject
  CanCreateProject(final CurrentUser user) {
    this.user = user;
  }

  @Override
  public Boolean call() throws Exception {
    final boolean canCreateProject = CollectionsUtil.isAnyIncludedIn(
        user.getEffectiveGroups(), projectCreatorGroups);
    return canCreateProject;
  }

}
