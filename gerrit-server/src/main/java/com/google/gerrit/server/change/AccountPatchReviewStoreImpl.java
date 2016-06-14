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

package com.google.gerrit.server.change;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountPatchReview;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.Collection;
import java.util.Collections;

import javax.inject.Singleton;

@Singleton
public class AccountPatchReviewStoreImpl implements AccountPatchReviewStore {
  private final Provider<ReviewDb> dbProvider;

  public static class Module extends AbstractModule {
    @Override
    protected void configure() {
      DynamicItem.itemOf(binder(), AccountPatchReviewStore.class);
      DynamicItem.bind(binder(), AccountPatchReviewStore.class)
          .to(AccountPatchReviewStoreImpl.class);
    }
  }

  @Inject
  AccountPatchReviewStoreImpl(Provider<ReviewDb> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public boolean markReviewed(PatchSet.Id psId, Account.Id accountId,
      String path) throws OrmException {
    ReviewDb db = dbProvider.get();
    AccountPatchReview apr = getExisting(db, psId, path, accountId);
    if (apr != null) {
      return false;
    }

    try {
      db.accountPatchReviews().insert(Collections.singleton(
          new AccountPatchReview(new Patch.Key(psId, path), accountId)));
      return true;
    } catch (OrmDuplicateKeyException e) {
      // Ignored
      return false;
    }
  }

  @Override
  public void markReviewed(final PatchSet.Id psId, final Account.Id accountId,
      final Collection<String> paths) throws OrmException {
    if (paths == null || paths.isEmpty()) {
      return;
    } else if (paths.size() == 1) {
      markReviewed(psId, accountId, Iterables.getOnlyElement(paths));
      return;
    }

    paths.removeAll(findReviewed(psId, accountId));
    if (paths.isEmpty()) {
      return;
    }
    dbProvider.get().accountPatchReviews().insert(Collections2.transform(paths,
        new Function<String, AccountPatchReview>() {
          @Override
          public AccountPatchReview apply(String path) {
            return new AccountPatchReview(new Patch.Key(psId, path), accountId);
          }
        }));
  }

  @Override
  public void clearReviewed(PatchSet.Id psId, Account.Id accountId, String path)
      throws OrmException {
    ReviewDb db = dbProvider.get();
    AccountPatchReview apr = getExisting(db, psId, path, accountId);
    if (apr != null) {
      db.accountPatchReviews().delete(Collections.singleton(apr));
    }
  }

  @Override
  public void clearReviewed(PatchSet.Id psId) throws OrmException {
    dbProvider.get().accountPatchReviews()
        .delete(dbProvider.get().accountPatchReviews().byPatchSet(psId));
  }

  @Override
  public Collection<String> findReviewed(PatchSet.Id psId, Account.Id accountId)
      throws OrmException {
    return Collections2.transform(dbProvider.get().accountPatchReviews()
        .byReviewer(accountId, psId).toList(),
        new Function<AccountPatchReview, String>() {
          @Override
          public String apply(AccountPatchReview apr) {
            return apr.getKey().getPatchKey().getFileName();
          }
        });
  }

  private static AccountPatchReview getExisting(ReviewDb db, PatchSet.Id psId,
      String path, Account.Id accountId) throws OrmException {
    AccountPatchReview.Key key =
        new AccountPatchReview.Key(new Patch.Key(psId, path), accountId);
    return db.accountPatchReviews().get(key);
  }
}
