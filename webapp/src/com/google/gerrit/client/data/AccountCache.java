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

package com.google.gerrit.client.data;

import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gwtorm.client.OrmException;

import java.util.HashMap;

public class AccountCache {
  private final ReviewDb db;
  private final HashMap<Account.Id, Account> cache;

  public AccountCache(final ReviewDb schema) {
    db = schema;
    cache = new HashMap<Account.Id, Account>();
  }

  public Account get(final Account.Id id) throws OrmException {
    Account a = cache.get(id);
    if (a == null) {
      a = db.accounts().byId(id);
      cache.put(id, a);
    }
    return a;
  }
}
