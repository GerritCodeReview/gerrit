// Copyright (C) 2012 The Android Open Source Project
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

import com.google.gerrit.common.auth.internal.InternalPasswordChangeService;
import com.google.gerrit.common.auth.internal.PasswordChangeResult;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.auth.internal.InternalPasswordHasher;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.Arrays;

class InternalPasswordChangeServiceImpl implements
    InternalPasswordChangeService {

  private final Provider<IdentifiedUser> user;

  private final SchemaFactory<ReviewDb> schema;

  @Inject
  InternalPasswordChangeServiceImpl(
      Provider<IdentifiedUser> user,
      SchemaFactory<ReviewDb> schema) {
    this.user = user;
    this.schema = schema;
  }

  @Override
  public void changePassword(String oldPassword, String newPassword,
      AsyncCallback<PasswordChangeResult> callback) {
    ReviewDb db = null;
    String userName = user.get().getUserName();
    try {
      db = schema.open();
      try {
        AccountExternalId.Key accountKey =
            new AccountExternalId.Key(AccountExternalId.SCHEME_INTERNAL, userName);
        AccountExternalId account = db.accountExternalIds().get(accountKey);
        String oldHash = InternalPasswordHasher.hashPassword(account, oldPassword);
        PasswordChangeResult result = new PasswordChangeResult();
        if (account.getPassword().equals(oldHash)) {
          String newPasswordHash = InternalPasswordHasher.hashPassword(account, newPassword);
          account.setPassword(newPasswordHash);
          db.accountExternalIds().update(Arrays.asList(account));
          result.success = true;
        } else {
          result.success = false;
        }
        callback.onSuccess(result);
      } finally {
        db.close();
      }
    } catch (OrmException e) {
      callback.onFailure(e);
    }
  }

}
