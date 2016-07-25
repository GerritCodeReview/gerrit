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
import com.google.gerrit.reviewdb.client.AccountProjectWatch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.project.ProjectsCollection;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

@Singleton
public class PostWatchedProjects
    implements RestModifyView<AccountResource, List<ProjectWatchInfo>> {
  private final Provider<ReviewDb> dbProvider;
  private final Provider<IdentifiedUser> self;
  private final GetWatchedProjects getWatchedProjects;
  private final ProjectsCollection projectsCollection;
  private final AccountCache accountCache;

  @Inject
  public PostWatchedProjects(Provider<ReviewDb> dbProvider,
      Provider<IdentifiedUser> self,
      GetWatchedProjects getWatchedProjects,
      ProjectsCollection projectsCollection,
      AccountCache accountCache) {
    this.dbProvider = dbProvider;
    this.self = self;
    this.getWatchedProjects = getWatchedProjects;
    this.projectsCollection = projectsCollection;
    this.accountCache = accountCache;
  }

  @Override
  public List<ProjectWatchInfo> apply(AccountResource rsrc,
      List<ProjectWatchInfo> input)
      throws OrmException, RestApiException, IOException {
    if (self.get() != rsrc.getUser()
      && !self.get().getCapabilities().canAdministrateServer()) {
      throw new AuthException("not allowed to edit project watches");
    }
    Account.Id accountId = rsrc.getUser().getAccountId();
    List<AccountProjectWatch> accountProjectWatchList =
        getAccountProjectWatchList(input, accountId);
    dbProvider.get().accountProjectWatches().upsert(accountProjectWatchList);
    accountCache.evict(accountId);
    return getWatchedProjects.apply(rsrc);
  }

  private List<AccountProjectWatch> getAccountProjectWatchList(
      List<ProjectWatchInfo> input, Account.Id accountId)
      throws UnprocessableEntityException, BadRequestException, IOException {
    Set<AccountProjectWatch.Key> keys = new HashSet<>();
    List<AccountProjectWatch> watchedProjects = new LinkedList<>();
    for (ProjectWatchInfo a : input) {
      if (a.project == null) {
        throw new BadRequestException("project name must be specified");
      }

      Project.NameKey projectKey =
          projectsCollection.parse(a.project).getNameKey();

      AccountProjectWatch.Key key =
          new AccountProjectWatch.Key(accountId, projectKey, a.filter);
      if (!keys.add(key)) {
        throw new BadRequestException(
            "duplicate entry for project " + key.getProjectName().get()
                + (!AccountProjectWatch.FILTER_ALL.equals(key.getFilter().get())
                    ? " and filter " + key.getFilter().get()
                    : ""));
      }
      AccountProjectWatch apw = new AccountProjectWatch(key);
      apw.setNotify(AccountProjectWatch.NotifyType.ABANDONED_CHANGES,
          toBoolean(a.notifyAbandonedChanges));
      apw.setNotify(AccountProjectWatch.NotifyType.ALL_COMMENTS,
          toBoolean(a.notifyAllComments));
      apw.setNotify(AccountProjectWatch.NotifyType.NEW_CHANGES,
          toBoolean(a.notifyNewChanges));
      apw.setNotify(AccountProjectWatch.NotifyType.NEW_PATCHSETS,
          toBoolean(a.notifyNewPatchSets));
      apw.setNotify(AccountProjectWatch.NotifyType.SUBMITTED_CHANGES,
          toBoolean(a.notifySubmittedChanges));
      watchedProjects.add(apw);
    }
    return watchedProjects;
  }

  private boolean toBoolean(Boolean b) {
    return b == null ? false : b;
  }
}
