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

import com.google.common.base.Strings;
import com.google.common.collect.ComparisonChain;
import com.google.gerrit.extensions.client.ProjectWatchInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.WatchConfig.NotifyType;
import com.google.gerrit.server.account.WatchConfig.ProjectWatchKey;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.ConfigInvalidException;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Singleton
public class GetWatchedProjects implements RestReadView<AccountResource> {

  private final Provider<IdentifiedUser> self;
  private final WatchConfig.Accessor watchConfig;

  @Inject
  public GetWatchedProjects(Provider<IdentifiedUser> self,
      WatchConfig.Accessor watchConfig) {
    this.self = self;
    this.watchConfig = watchConfig;
  }

  @Override
  public List<ProjectWatchInfo> apply(AccountResource rsrc)
      throws OrmException, AuthException, IOException, ConfigInvalidException {
    if (self.get() != rsrc.getUser()
        && !self.get().getCapabilities().canAdministrateServer()) {
      throw new AuthException("It is not allowed to list project watches "
          + "of other users");
    }
    Account.Id accountId = rsrc.getUser().getAccountId();
    List<ProjectWatchInfo> projectWatchInfos = new LinkedList<>();
    for (Map.Entry<ProjectWatchKey, Set<NotifyType>> e : watchConfig
        .getProjectWatches(accountId).entrySet()) {
      ProjectWatchInfo pwi = new ProjectWatchInfo();
      pwi.filter = e.getKey().filter();
      pwi.project = e.getKey().project().get();
      pwi.notifyAbandonedChanges =
          toBoolean(e.getValue().contains(NotifyType.ABANDONED_CHANGES));
      pwi.notifyNewChanges =
          toBoolean(e.getValue().contains(NotifyType.NEW_CHANGES));
      pwi.notifyNewPatchSets =
          toBoolean(e.getValue().contains(NotifyType.NEW_PATCHSETS));
      pwi.notifySubmittedChanges =
          toBoolean(e.getValue().contains(NotifyType.SUBMITTED_CHANGES));
      pwi.notifyAllComments =
          toBoolean(e.getValue().contains(NotifyType.ALL_COMMENTS));
      projectWatchInfos.add(pwi);
    }
    Collections.sort(projectWatchInfos, new Comparator<ProjectWatchInfo>() {
      @Override
      public int compare(ProjectWatchInfo pwi1, ProjectWatchInfo pwi2) {
        return ComparisonChain.start()
            .compare(pwi1.project, pwi2.project)
            .compare(Strings.nullToEmpty(pwi1.filter),
                Strings.nullToEmpty(pwi2.filter))
            .result();
      }
    });
    return projectWatchInfos;
  }

  private static Boolean toBoolean(boolean value) {
    return value ? true : null;
  }
}
