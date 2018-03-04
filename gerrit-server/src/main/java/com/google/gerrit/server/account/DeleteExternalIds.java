// Copyright (C) 2017 The Android Open Source Project
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

import static com.google.gerrit.server.account.ExternalId.SCHEME_USERNAME;

import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.eclipse.jgit.errors.ConfigInvalidException;

public class DeleteExternalIds implements RestModifyView<AccountResource, List<String>> {
  private final AccountByEmailCache accountByEmailCache;
  private final AccountCache accountCache;
  private final ExternalIdsUpdate.User externalIdsUpdateFactory;
  private final Provider<CurrentUser> self;
  private final Provider<ReviewDb> dbProvider;

  @Inject
  DeleteExternalIds(
      AccountByEmailCache accountByEmailCache,
      AccountCache accountCache,
      ExternalIdsUpdate.User externalIdsUpdateFactory,
      Provider<CurrentUser> self,
      Provider<ReviewDb> dbProvider) {
    this.accountByEmailCache = accountByEmailCache;
    this.accountCache = accountCache;
    this.externalIdsUpdateFactory = externalIdsUpdateFactory;
    this.self = self;
    this.dbProvider = dbProvider;
  }

  @Override
  public Response<?> apply(AccountResource resource, List<String> externalIds)
      throws RestApiException, IOException, OrmException, ConfigInvalidException {
    if (self.get() != resource.getUser()) {
      throw new AuthException("not allowed to delete external IDs");
    }

    if (externalIds == null || externalIds.size() == 0) {
      throw new BadRequestException("external IDs are required");
    }

    Account.Id accountId = resource.getUser().getAccountId();
    Map<ExternalId.Key, ExternalId> externalIdMap =
        dbProvider
            .get()
            .accountExternalIds()
            .byAccount(resource.getUser().getAccountId())
            .toList()
            .stream()
            .map(ExternalId::from)
            .collect(Collectors.toMap(i -> i.key(), i -> i));

    List<ExternalId> toDelete = new ArrayList<>();
    ExternalId.Key last = resource.getUser().getLastLoginExternalIdKey();
    for (String externalIdStr : externalIds) {
      ExternalId id = externalIdMap.get(ExternalId.Key.parse(externalIdStr));

      if (id == null) {
        throw new UnprocessableEntityException(
            String.format("External id %s does not exist", externalIdStr));
      }

      if ((!id.isScheme(SCHEME_USERNAME))
          && ((last == null) || (!last.get().equals(id.key().get())))) {
        toDelete.add(id);
      } else {
        throw new ResourceConflictException(
            String.format("External id %s cannot be deleted", externalIdStr));
      }
    }

    if (!toDelete.isEmpty()) {
      externalIdsUpdateFactory.create().delete(dbProvider.get(), toDelete);
      accountCache.evict(accountId);
      for (ExternalId e : toDelete) {
        accountByEmailCache.evict(e.email());
      }
    }

    return Response.none();
  }
}
