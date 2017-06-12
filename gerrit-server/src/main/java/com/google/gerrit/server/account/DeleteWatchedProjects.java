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

import static java.util.stream.Collectors.toList;

import com.google.gerrit.extensions.client.ProjectWatchInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountProjectWatch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.WatchConfig.ProjectWatchKey;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class DeleteWatchedProjects
    implements RestModifyView<AccountResource, List<ProjectWatchInfo>> {
  private final Provider<ReviewDb> dbProvider;
  private final Provider<IdentifiedUser> self;
  private final AccountCache accountCache;
  private final WatchConfig.Accessor watchConfig;

  @Inject
  DeleteWatchedProjects(
      Provider<ReviewDb> dbProvider,
      Provider<IdentifiedUser> self,
      AccountCache accountCache,
      WatchConfig.Accessor watchConfig) {
    this.dbProvider = dbProvider;
    this.self = self;
    this.accountCache = accountCache;
    this.watchConfig = watchConfig;
  }

  @Override
  public Response<?> apply(AccountResource rsrc, List<ProjectWatchInfo> input)
      throws AuthException, UnprocessableEntityException, OrmException, IOException,
          ConfigInvalidException {
    if (self.get() != rsrc.getUser() && !self.get().getCapabilities().canAdministrateServer()) {
      throw new AuthException("It is not allowed to edit project watches " + "of other users");
    }
    if (input == null) {
      return Response.none();
    }

    Account.Id accountId = rsrc.getUser().getAccountId();
    deleteFromDb(accountId, input);
    deleteFromGit(accountId, input);
    accountCache.evict(accountId);
    return Response.none();
  }

  private void deleteFromDb(Account.Id accountId, List<ProjectWatchInfo> input)
      throws OrmException, IOException {
    ResultSet<AccountProjectWatch> watchedProjects =
        dbProvider.get().accountProjectWatches().byAccount(accountId);
    HashMap<AccountProjectWatch.Key, AccountProjectWatch> watchedProjectsMap = new HashMap<>();
    for (AccountProjectWatch watchedProject : watchedProjects) {
      watchedProjectsMap.put(watchedProject.getKey(), watchedProject);
    }

    List<AccountProjectWatch> watchesToDelete = new LinkedList<>();
    for (ProjectWatchInfo projectInfo : input) {
      AccountProjectWatch.Key key =
          new AccountProjectWatch.Key(
              accountId, new Project.NameKey(projectInfo.project), projectInfo.filter);
      if (watchedProjectsMap.containsKey(key)) {
        watchesToDelete.add(watchedProjectsMap.get(key));
      }
    }
    if (!watchesToDelete.isEmpty()) {
      dbProvider.get().accountProjectWatches().delete(watchesToDelete);
      accountCache.evict(accountId);
    }
  }

  private void deleteFromGit(Account.Id accountId, List<ProjectWatchInfo> input)
      throws IOException, ConfigInvalidException {
    watchConfig.deleteProjectWatches(
        accountId,
        input
            .stream()
            .map(w -> ProjectWatchKey.create(new Project.NameKey(w.project), w.filter))
            .collect(toList()));
  }
}
