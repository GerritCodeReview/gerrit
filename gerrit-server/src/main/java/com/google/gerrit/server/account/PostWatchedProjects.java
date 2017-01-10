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

package com.google.gerrit.server.account;

import com.google.gerrit.extensions.client.ProjectWatchInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.WatchConfig.NotifyType;
import com.google.gerrit.server.account.WatchConfig.ProjectWatchKey;
import com.google.gerrit.server.project.ProjectsCollection;
import com.google.gwtorm.server.OrmException;
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
  private final GetWatchedProjects getWatchedProjects;
  private final ProjectsCollection projectsCollection;
  private final AccountCache accountCache;
  private final WatchConfig.Accessor watchConfig;

  @Inject
  public PostWatchedProjects(
      Provider<IdentifiedUser> self,
      GetWatchedProjects getWatchedProjects,
      ProjectsCollection projectsCollection,
      AccountCache accountCache,
      WatchConfig.Accessor watchConfig) {
    this.self = self;
    this.getWatchedProjects = getWatchedProjects;
    this.projectsCollection = projectsCollection;
    this.accountCache = accountCache;
    this.watchConfig = watchConfig;
  }

  @Override
  public List<ProjectWatchInfo> apply(AccountResource rsrc, List<ProjectWatchInfo> input)
      throws OrmException, RestApiException, IOException, ConfigInvalidException {
    if (self.get() != rsrc.getUser() && !self.get().getCapabilities().canAdministrateServer()) {
      throw new AuthException("not allowed to edit project watches");
    }
    Account.Id accountId = rsrc.getUser().getAccountId();
    watchConfig.upsertProjectWatches(accountId, asMap(input));
    accountCache.evict(accountId);
    return getWatchedProjects.apply(rsrc);
  }

  private Map<ProjectWatchKey, Set<NotifyType>> asMap(List<ProjectWatchInfo> input)
      throws BadRequestException, UnprocessableEntityException, IOException {
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
        + (filter != null && !WatchConfig.FILTER_ALL.equals(filter) ? " and filter " + filter : "");
  }
}
