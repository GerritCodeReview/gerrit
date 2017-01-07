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

import com.google.gerrit.extensions.api.accounts.DeleteAccountExternalIdsInput;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.reviewdb.server.ReviewDbUtil;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DeleteAccountExternalIds implements
    RestModifyView<AccountResource, DeleteAccountExternalIdsInput> {
  private final Provider<ReviewDb> dbProvider;
  private final AccountByEmailCache byEmailCache;
  private final AccountCache accountCache;

  @Inject
  DeleteAccountExternalIds(final Provider<ReviewDb> dbProvider,
      final AccountByEmailCache byEmailCache,
      final AccountCache accountCache) {
    this.dbProvider = dbProvider;
    this.byEmailCache = byEmailCache;
    this.accountCache = accountCache;
  }

  @Override
  public Response<?> apply(AccountResource resource,
      DeleteAccountExternalIdsInput input)
      throws BadRequestException, IOException, OrmException {
    if (input == null
        || input.externalIdList == null
        || input.externalIdList.size() == 0) {
      throw new BadRequestException("external ids are required");
    }

    Account.Id accountId = resource.getUser().getAccountId();
    final Map<AccountExternalId.Key, AccountExternalId> externalIdMap =
        getAccountExternalIds(accountId);

    List<AccountExternalId> toDelete = new ArrayList<>();
    for (String externalIdStr : input.externalIdList) {
      AccountExternalId.Key key = new AccountExternalId.Key(externalIdStr);
      final AccountExternalId id = externalIdMap.get(key);
      if (id != null && id.canDelete()) {
        toDelete.add(id);
      }
    }

    if (!toDelete.isEmpty()) {
      ReviewDbUtil.unwrapDb(dbProvider.get())
          .accountExternalIds().delete(toDelete);
      accountCache.evict(accountId);
      for (AccountExternalId e : toDelete) {
        byEmailCache.evict(e.getEmailAddress());
      }
    }

    return Response.none();
  }

  private Map<AccountExternalId.Key, AccountExternalId> getAccountExternalIds(
      Account.Id accountId) throws OrmException {
    Map<AccountExternalId.Key, AccountExternalId> idMap = new HashMap<>();
    List<AccountExternalId> idList =
        dbProvider.get().accountExternalIds().byAccount(accountId).toList();

    // to do, set 'canDelete' field

    for (AccountExternalId i : idList) {
      idMap.put(i.getKey(), i);
    }
    return idMap;
  }
}
