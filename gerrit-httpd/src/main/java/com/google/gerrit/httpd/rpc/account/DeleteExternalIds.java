// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.httpd.rpc.account;

import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountByEmailCache;
import com.google.gerrit.server.account.AccountCache;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class DeleteExternalIds extends Handler<Set<AccountExternalId.Key>> {
  interface Factory {
    DeleteExternalIds create(Set<AccountExternalId.Key> keys);
  }

  private final ReviewDb db;
  private final IdentifiedUser user;
  private final ExternalIdDetailFactory detailFactory;
  private final AccountByEmailCache byEmailCache;
  private final AccountCache accountCache;

  private final Set<AccountExternalId.Key> keys;

  @Inject
  DeleteExternalIds(
      final ReviewDb db,
      final IdentifiedUser user,
      final ExternalIdDetailFactory detailFactory,
      final AccountByEmailCache byEmailCache,
      final AccountCache accountCache,
      @Assisted final Set<AccountExternalId.Key> keys) {
    this.db = db;
    this.user = user;
    this.detailFactory = detailFactory;
    this.byEmailCache = byEmailCache;
    this.accountCache = accountCache;

    this.keys = keys;
  }

  @Override
  public Set<AccountExternalId.Key> call() throws OrmException, IOException {
    final Map<AccountExternalId.Key, AccountExternalId> have = have();

    List<AccountExternalId> toDelete = new ArrayList<>();
    for (AccountExternalId.Key k : keys) {
      final AccountExternalId id = have.get(k);
      if (id != null && id.canDelete()) {
        toDelete.add(id);
      }
    }

    if (!toDelete.isEmpty()) {
      db.accountExternalIds().delete(toDelete);
      accountCache.evict(user.getAccountId());
      for (AccountExternalId e : toDelete) {
        byEmailCache.evict(e.getEmailAddress());
      }
    }

    return toKeySet(toDelete);
  }

  private Map<AccountExternalId.Key, AccountExternalId> have() throws OrmException {
    Map<AccountExternalId.Key, AccountExternalId> r;

    r = new HashMap<>();
    for (AccountExternalId i : detailFactory.call()) {
      r.put(i.getKey(), i);
    }
    return r;
  }

  private Set<AccountExternalId.Key> toKeySet(List<AccountExternalId> toDelete) {
    Set<AccountExternalId.Key> r = new HashSet<>();
    for (AccountExternalId i : toDelete) {
      r.add(i.getKey());
    }
    return r;
  }
}
