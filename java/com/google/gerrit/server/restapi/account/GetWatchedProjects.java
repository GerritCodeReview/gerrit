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

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.extensions.client.ProjectWatchInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.Accounts;
import com.google.gerrit.server.account.ProjectWatches.NotifyType;
import com.google.gerrit.server.account.ProjectWatches.ProjectWatchKey;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class GetWatchedProjects implements RestReadView<AccountResource> {
  private final PermissionBackend permissionBackend;
  private final Provider<IdentifiedUser> self;
  private final Accounts accounts;

  @Inject
  public GetWatchedProjects(
      PermissionBackend permissionBackend, Provider<IdentifiedUser> self, Accounts accounts) {
    this.permissionBackend = permissionBackend;
    this.self = self;
    this.accounts = accounts;
  }

  @Override
  public List<ProjectWatchInfo> apply(AccountResource rsrc)
      throws AuthException, IOException, ConfigInvalidException, PermissionBackendException,
          ResourceNotFoundException {
    if (!self.get().hasSameAccountId(rsrc.getUser())) {
      permissionBackend.currentUser().check(GlobalPermission.ADMINISTRATE_SERVER);
    }

    Account.Id accountId = rsrc.getUser().getAccountId();
    AccountState account = accounts.get(accountId).orElseThrow(ResourceNotFoundException::new);
    return account.getProjectWatches().entrySet().stream()
        .map(e -> toProjectWatchInfo(e.getKey(), e.getValue()))
        .sorted(
            comparing((ProjectWatchInfo pwi) -> pwi.project)
                .thenComparing(pwi -> Strings.nullToEmpty(pwi.filter)))
        .collect(toList());
  }

  private static ProjectWatchInfo toProjectWatchInfo(
      ProjectWatchKey key, ImmutableSet<NotifyType> watchTypes) {
    ProjectWatchInfo pwi = new ProjectWatchInfo();
    pwi.filter = key.filter();
    pwi.project = key.project().get();
    pwi.notifyAbandonedChanges = toBoolean(watchTypes.contains(NotifyType.ABANDONED_CHANGES));
    pwi.notifyNewChanges = toBoolean(watchTypes.contains(NotifyType.NEW_CHANGES));
    pwi.notifyNewPatchSets = toBoolean(watchTypes.contains(NotifyType.NEW_PATCHSETS));
    pwi.notifySubmittedChanges = toBoolean(watchTypes.contains(NotifyType.SUBMITTED_CHANGES));
    pwi.notifyAllComments = toBoolean(watchTypes.contains(NotifyType.ALL_COMMENTS));
    return pwi;
  }

  private static Boolean toBoolean(boolean value) {
    return value ? true : null;
  }
}
