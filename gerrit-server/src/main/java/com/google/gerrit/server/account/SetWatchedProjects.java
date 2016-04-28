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
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountProjectWatch;
import com.google.gerrit.reviewdb.client.AccountProjectWatch.NotifyType;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import autovalue.shaded.com.google.common.common.collect.Sets;

@Singleton
public class SetWatchedProjects
    implements RestModifyView<AccountResource, List<ProjectWatchInfo>> {
  private GetWatchedProjects getWatchedProjects;
  private Provider<ReviewDb> dbProvider;
  private ProjectCache projectCache;

  @Inject
  public SetWatchedProjects(GetWatchedProjects getWatchedProjects,
      Provider<ReviewDb> dbProvider,
      ProjectCache projectCache) {
    this.getWatchedProjects = getWatchedProjects;
    this.dbProvider = dbProvider;
    this.projectCache = projectCache;
  }

  @Override
  public List<ProjectWatchInfo> apply(AccountResource rsrc,
      List<ProjectWatchInfo> input)
      throws OrmException, ResourceNotFoundException, AuthException {
    List<AccountProjectWatch> accountProjectWatchList =
        getAccountProjectWatchList(input, rsrc.getUser().getAccountId());
    dbProvider.get().accountProjectWatches().upsert(accountProjectWatchList);
    return getWatchedProjects.apply(rsrc);
  }

  private List<AccountProjectWatch> getAccountProjectWatchList(
      List<ProjectWatchInfo> input, Account.Id accountId)
      throws ResourceNotFoundException {
    List<AccountProjectWatch> watchedProjects = new LinkedList<>();
    Set<Project.NameKey> projectKeys = Sets.newHashSet(projectCache.all());
    for (ProjectWatchInfo a : input) {
      if (a.project == null) {
        throw new ResourceNotFoundException("Project name must be specified");
      }
      Project.NameKey projectKey = new Project.NameKey(a.project);
      if (!projectKeys.contains(projectKey)) {
        throw new ResourceNotFoundException("Project not found");
      }
      AccountProjectWatch.Key key =
          new AccountProjectWatch.Key(accountId, projectKey, a.filter);
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
