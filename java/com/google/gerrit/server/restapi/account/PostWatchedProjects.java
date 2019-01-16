// Copyright (C) 2016 The Android Open Source Project
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

import com.google.gerrit.extensions.client.ProjectWatchInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.UserInitiated;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.account.ProjectWatches;
import com.google.gerrit.server.account.ProjectWatches.NotifyType;
import com.google.gerrit.server.account.ProjectWatches.ProjectWatchKey;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.restapi.project.ProjectsCollection;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class PostWatchedProjects
    implements RestModifyView<AccountResource, List<ProjectWatchInfo>> {
  private final Provider<IdentifiedUser> self;
  private final PermissionBackend permissionBackend;
  private final GetWatchedProjects getWatchedProjects;
  private final ProjectsCollection projectsCollection;
  private final Provider<AccountsUpdate> accountsUpdateProvider;

  @Inject
  public PostWatchedProjects(
      Provider<IdentifiedUser> self,
      PermissionBackend permissionBackend,
      GetWatchedProjects getWatchedProjects,
      ProjectsCollection projectsCollection,
      @UserInitiated Provider<AccountsUpdate> accountsUpdateProvider) {
    this.self = self;
    this.permissionBackend = permissionBackend;
    this.getWatchedProjects = getWatchedProjects;
    this.projectsCollection = projectsCollection;
    this.accountsUpdateProvider = accountsUpdateProvider;
  }

  @Override
  public List<ProjectWatchInfo> apply(AccountResource rsrc, List<ProjectWatchInfo> input)
      throws RestApiException, IOException, ConfigInvalidException, PermissionBackendException {
    if (!self.get().hasSameAccountId(rsrc.getUser())) {
      permissionBackend.currentUser().check(GlobalPermission.ADMINISTRATE_SERVER);
    }

    Map<ProjectWatchKey, Set<NotifyType>> projectWatches = asMap(input);
    accountsUpdateProvider
        .get()
        .update(
            "Update Project Watches via API",
            rsrc.getUser().getAccountId(),
            u -> u.updateProjectWatches(projectWatches));
    return getWatchedProjects.apply(rsrc);
  }

  private Map<ProjectWatchKey, Set<NotifyType>> asMap(List<ProjectWatchInfo> input)
      throws RestApiException, IOException, PermissionBackendException {
    Map<ProjectWatchKey, Set<NotifyType>> m = new HashMap<>();
    for (ProjectWatchInfo info : input) {
      if (info.project == null) {
        throw new BadRequestException("project name must be specified");
      }

      ProjectWatchKey key =
          ProjectWatchKey.create(projectsCollection.parse(info.project).getNameKey(), info.filter);
      if (m.containsKey(key)) {
        throw new BadRequestException(
            "duplicate entry for project " + format(info.project, info.filter));
      }

      Set<NotifyType> notifyValues = EnumSet.noneOf(NotifyType.class);
      if (toBoolean(info.notifyAbandonedChanges)) {
        notifyValues.add(NotifyType.ABANDONED_CHANGES);
      }
      if (toBoolean(info.notifyAllComments)) {
        notifyValues.add(NotifyType.ALL_COMMENTS);
      }
      if (toBoolean(info.notifyNewChanges)) {
        notifyValues.add(NotifyType.NEW_CHANGES);
      }
      if (toBoolean(info.notifyNewPatchSets)) {
        notifyValues.add(NotifyType.NEW_PATCHSETS);
      }
      if (toBoolean(info.notifySubmittedChanges)) {
        notifyValues.add(NotifyType.SUBMITTED_CHANGES);
      }

      m.put(key, notifyValues);
    }
    return m;
  }

  private boolean toBoolean(Boolean b) {
    return b == null ? false : b;
  }

  private static String format(String project, String filter) {
    return project
        + (filter != null && !ProjectWatches.FILTER_ALL.equals(filter)
            ? " and filter " + filter
            : "");
  }
}
