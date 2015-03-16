// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.api.accounts;

import com.google.gerrit.extensions.api.accounts.AccountApi;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.account.StarredChanges;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.ChangesCollection;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

public class AccountApiImpl implements AccountApi {
  interface Factory {
    AccountApiImpl create(AccountResource account);
  }

  private final AccountResource account;
  private final ChangesCollection changes;
  private final AccountLoader.Factory accountLoaderFactory;
  private final StarredChanges.Create starredChangesCreate;
  private final StarredChanges.Delete starredChangesDelete;

  @Inject
  AccountApiImpl(AccountLoader.Factory ailf,
      ChangesCollection changes,
      StarredChanges.Create starredChangesCreate,
      StarredChanges.Delete starredChangesDelete,
      @Assisted AccountResource account) {
    this.account = account;
    this.accountLoaderFactory = ailf;
    this.changes = changes;
    this.starredChangesCreate = starredChangesCreate;
    this.starredChangesDelete = starredChangesDelete;
  }

  @Override
  public com.google.gerrit.extensions.common.AccountInfo get()
      throws RestApiException {
    AccountLoader accountLoader = accountLoaderFactory.create(true);
    try {
      AccountInfo ai = accountLoader.get(account.getUser().getAccountId());
      accountLoader.fill();
      return ai;
    } catch (OrmException e) {
      throw new RestApiException("Cannot parse change", e);
    }
  }

  @Override
  public void starChange(String id) throws RestApiException {
    try {
      ChangeResource rsrc = changes.parse(
        TopLevelResource.INSTANCE,
        IdString.fromUrl(id));
      starredChangesCreate.setChange(rsrc);
      starredChangesCreate.apply(account, new StarredChanges.EmptyInput());
    } catch (OrmException e) {
      throw new RestApiException("Cannot star change", e);
    }
  }

  @Override
  public void unstarChange(String id) throws RestApiException {
    try {
      ChangeResource rsrc =
          changes.parse(TopLevelResource.INSTANCE, IdString.fromUrl(id));
      AccountResource.StarredChange starredChange =
          new AccountResource.StarredChange(account.getUser(), rsrc);
      starredChangesDelete.apply(starredChange,
          new StarredChanges.EmptyInput());
    } catch (OrmException e) {
      throw new RestApiException("Cannot unstar change", e);
    }
  }
}
