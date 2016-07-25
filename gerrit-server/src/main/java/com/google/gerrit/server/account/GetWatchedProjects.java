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
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.AccountProjectWatch;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import java.util.LinkedList;
import java.util.List;

@Singleton
public class GetWatchedProjects implements RestReadView<AccountResource> {

  private final Provider<ReviewDb> dbProvider;
  private final Provider<IdentifiedUser> self;

  @Inject
  public GetWatchedProjects(Provider<ReviewDb> dbProvider,
      Provider<IdentifiedUser> self) {
    this.dbProvider = dbProvider;
    this.self = self;
  }

  @Override
  public List<ProjectWatchInfo> apply(AccountResource rsrc)
      throws OrmException, AuthException {
    if (self.get() != rsrc.getUser()
      && !self.get().getCapabilities().canAdministrateServer()) {
      throw new AuthException("It is not allowed to list project watches "
          + "of other users");
    }
    List<ProjectWatchInfo> projectWatchInfos = new LinkedList<>();
    Iterable<AccountProjectWatch> projectWatches =
        dbProvider.get().accountProjectWatches()
            .byAccount(rsrc.getUser().getAccountId());
    for (AccountProjectWatch a : projectWatches) {
      ProjectWatchInfo pwi = new ProjectWatchInfo();
      pwi.filter = a.getFilter();
      pwi.project = a.getProjectNameKey().get();
      pwi.notifyAbandonedChanges =
          toBoolean(
              a.isNotify(AccountProjectWatch.NotifyType.ABANDONED_CHANGES));
      pwi.notifyNewChanges =
          toBoolean(a.isNotify(AccountProjectWatch.NotifyType.NEW_CHANGES));
      pwi.notifyNewPatchSets =
          toBoolean(a.isNotify(AccountProjectWatch.NotifyType.NEW_PATCHSETS));
      pwi.notifySubmittedChanges =
          toBoolean(
              a.isNotify(AccountProjectWatch.NotifyType.SUBMITTED_CHANGES));
      pwi.notifyAllComments =
          toBoolean(a.isNotify(AccountProjectWatch.NotifyType.ALL_COMMENTS));
      projectWatchInfos.add(pwi);
    }
    return projectWatchInfos;
  }

  private static Boolean toBoolean(boolean value) {
    return value ? true : null;
  }
}
