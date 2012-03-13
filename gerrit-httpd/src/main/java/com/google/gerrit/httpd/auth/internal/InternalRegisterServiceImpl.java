// Copyright (C) 2011 The Android Open Source Project
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
// limitations under the License

package com.google.gerrit.httpd.auth.internal;

import com.google.gerrit.common.auth.internal.InternalRegisterService;
import com.google.gerrit.common.auth.internal.RegistrationResult;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.auth.internal.InternalPasswordHasher;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;

import java.util.Collections;

class InternalRegisterServiceImpl implements InternalRegisterService {

  private final AccountCache accountCache;

  private final SchemaFactory<ReviewDb> schema;

  @Inject
  InternalRegisterServiceImpl(AccountCache accountCache,
      SchemaFactory<ReviewDb> schema) {
    this.schema = schema;
    this.accountCache = accountCache;
  }

  @Override
  public void register(String login, String password,
      AsyncCallback<RegistrationResult> callback) {
    RegistrationResult result = new RegistrationResult();
    if (alreadyInUse(login)) {
      result.alreadyExist = true;
      callback.onSuccess(result);
      return;
    }
    try {
      ReviewDb db = schema.open();
      try {
        AccountExternalId.Key account =
            new AccountExternalId.Key(AccountExternalId.SCHEME_INTERNAL, login);
        AccountExternalId existingAccount = db.accountExternalIds().get(account);
        if (existingAccount != null) {
          result.alreadyExist = true;
          callback.onSuccess(result);
          return;
        }

        Account.Id id = new Account.Id(db.nextAccountId());
        AccountExternalId newAccount = new AccountExternalId(id, account);
        String passwordHash =
            InternalPasswordHasher.hashPassword(newAccount, password);
        newAccount.setPassword(passwordHash);
        db.accountExternalIds().insert(Collections.singleton(newAccount));
        result.success = true;
        callback.onSuccess(result);
      } finally {
        db.close();
      }
    } catch (OrmException e) {
      callback.onFailure(e);
    }

  }

  private boolean alreadyInUse(String login) {
    return accountCache.getByUsername(login) != null;
  }

}
