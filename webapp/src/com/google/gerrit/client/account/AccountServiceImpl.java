// Copyright 2008 Google Inc.
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

package com.google.gerrit.client.account;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.rpc.NotSignedInException;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtjsonrpc.client.CookieAccess;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;

public class AccountServiceImpl implements AccountService {
  private final SchemaFactory<ReviewDb> schema;

  public AccountServiceImpl(final SchemaFactory<ReviewDb> rdf) {
    schema = rdf;
  }

  public void myAccount(final AsyncCallback<Account> callback) {
    final int id = idFromCookie(callback);
    if (id <= 0) {
      callback.onFailure(new NotSignedInException());
      return;
    }

    try {
      final ReviewDb db = schema.open();
      try {
        callback.onSuccess(db.accounts().byId(new Account.Id(id)));
      } finally {
        db.close();
      }
    } catch (OrmException e) {
      callback.onFailure(e);
    }
  }

  private static int idFromCookie(final AsyncCallback<Account> callback) {
    final String myid = CookieAccess.getTokenText(Gerrit.ACCOUNT_COOKIE);
    if (myid != null && myid.length() > 0) {
      try {
        return Integer.parseInt(myid);
      } catch (NumberFormatException e) {
      }
    }
    return 0;
  }
}
