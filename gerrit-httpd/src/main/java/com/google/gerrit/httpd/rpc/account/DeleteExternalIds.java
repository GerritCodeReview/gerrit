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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.AccountExternalId;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.util.FutureUtil;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.internal.Lists;

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
  private final AccountCache accountCache;

  private final Set<AccountExternalId.Key> keys;

  @Inject
  DeleteExternalIds(final ReviewDb db, final IdentifiedUser user,
      final ExternalIdDetailFactory detailFactory,
      final AccountCache accountCache,

      @Assisted final Set<AccountExternalId.Key> keys) {
    this.db = db;
    this.user = user;
    this.detailFactory = detailFactory;
    this.accountCache = accountCache;

    this.keys = keys;
  }

  @Override
  public Set<AccountExternalId.Key> call() throws OrmException {
    final Map<AccountExternalId.Key, AccountExternalId> have = have();

    List<AccountExternalId> toDelete = new ArrayList<AccountExternalId>();
    for (AccountExternalId.Key k : keys) {
      final AccountExternalId id = have.get(k);
      if (id != null && id.canDelete()) {
        toDelete.add(id);
      }
    }

    List<ListenableFuture<Void>> evictions = Lists.newArrayList();
    if (!toDelete.isEmpty()) {
      db.accountExternalIds().delete(toDelete);
      evictions.add(accountCache.evictAsync(user.getAccountId()));
      for (AccountExternalId e : toDelete) {
        evictions.add(accountCache.evictEmailAsync(e.getEmailAddress()));
        evictions.add(accountCache.evictAsync(e.getKey()));
      }
    }
    FutureUtil.waitFor(evictions);
    return toKeySet(toDelete);
  }

  private Map<AccountExternalId.Key, AccountExternalId> have() {
    Map<AccountExternalId.Key, AccountExternalId> r;

    r = new HashMap<AccountExternalId.Key, AccountExternalId>();
    for (AccountExternalId i : detailFactory.call()) {
      r.put(i.getKey(), i);
    }
    return r;
  }

  private Set<AccountExternalId.Key> toKeySet(List<AccountExternalId> toDelete) {
    Set<AccountExternalId.Key> r = new HashSet<AccountExternalId.Key>();
    for (AccountExternalId i : toDelete) {
      r.add(i.getKey());
    }
    return r;
  }
}
