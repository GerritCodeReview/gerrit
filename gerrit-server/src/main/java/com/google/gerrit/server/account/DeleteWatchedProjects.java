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
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.WatchConfig.ProjectWatchKey;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class DeleteWatchedProjects
    implements RestModifyView<AccountResource, List<ProjectWatchInfo>> {
  private final Provider<IdentifiedUser> self;
  private final PermissionBackend permissionBackend;
  private final AccountCache accountCache;
  private final WatchConfig.Accessor watchConfig;

  @Inject
  DeleteWatchedProjects(
      Provider<IdentifiedUser> self,
      PermissionBackend permissionBackend,
      AccountCache accountCache,
      WatchConfig.Accessor watchConfig) {
    this.self = self;
    this.permissionBackend = permissionBackend;
    this.accountCache = accountCache;
    this.watchConfig = watchConfig;
  }

  @Override
  public Response<?> apply(AccountResource rsrc, List<ProjectWatchInfo> input)
      throws AuthException, UnprocessableEntityException, OrmException, IOException,
          ConfigInvalidException, PermissionBackendException {
    if (self.get() != rsrc.getUser()) {
      permissionBackend.user(self).check(GlobalPermission.ADMINISTRATE_SERVER);
    }
    if (input == null) {
      return Response.none();
    }

    Account.Id accountId = rsrc.getUser().getAccountId();
    watchConfig.deleteProjectWatches(
        accountId,
        input
            .stream()
            .map(w -> ProjectWatchKey.create(new Project.NameKey(w.project), w.filter))
            .collect(toList()));
    accountCache.evict(accountId);
    return Response.none();
  }
}
