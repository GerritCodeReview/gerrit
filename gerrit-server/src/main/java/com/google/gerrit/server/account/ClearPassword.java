// Copyright (C) 2010 The Android Open Source Project
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

import com.google.common.collect.Lists;
import com.google.gerrit.common.errors.NoSuchEntityException;
import com.google.gerrit.reviewdb.AccountExternalId;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.util.FutureUtil;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/** Operation to clear a password for an account. */
public class ClearPassword implements Callable<AccountExternalId> {
  public interface Factory {
    ClearPassword create(AccountExternalId.Key forUser);
  }

  private final AccountCache accountCache;
  private final ReviewDb db;
  private final IdentifiedUser user;

  private final AccountExternalId.Key forUser;

  @Inject
  ClearPassword(final AccountCache accountCache, final ReviewDb db,
      final IdentifiedUser user,

      @Assisted AccountExternalId.Key forUser) {
    this.accountCache = accountCache;
    this.db = db;
    this.user = user;

    this.forUser = forUser;
  }

  public AccountExternalId call() throws OrmException, NoSuchEntityException {
    AccountExternalId id = db.accountExternalIds().get(forUser);
    if (id == null || !user.getAccountId().equals(id.getAccountId())) {
      throw new NoSuchEntityException();
    }

    id.setPassword(null);
    db.accountExternalIds().update(Collections.singleton(id));

    List<Future<Void>> evictions = Lists.newArrayList();
    evictions.add(accountCache.evictAsync(id.getKey()));
    evictions.add(accountCache.evictAsync(user.getAccountId()));
    FutureUtil.waitFor(evictions);
    return id;
  }
}
