// Copyright (C) 2013 The Android Open Source Project
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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Account.Id;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Provider;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class AccountInfo {
  public static class Cache {
    private final Provider<ReviewDb> db;
    private final boolean nameOnly;
    private final Map<Id, Account> accounts;
    private final List<AccountInfo> pending;

    Cache(Provider<ReviewDb> db, boolean nameOnly) {
      this.db = db;
      this.nameOnly = nameOnly;
      accounts = Maps.newHashMap();
      pending = Lists.newArrayList();
    }

    public AccountInfo get(Id id) {
      if (id == null) {
        return null;
      }
      AccountInfo info = new AccountInfo(id);
      pending.add(info);
      return info;
    }

    public void put(AccountInfo info) {
      pending.add(info);
    }

    public void fill() throws OrmException {
      Set<Id> missing = Sets.newHashSetWithExpectedSize(pending.size());
      for (AccountInfo info : pending) {
        if (info._id != null) {
          if (!accounts.containsKey(info._id)) {
            missing.add(info._id);
          }
        }
      }
      if (!missing.isEmpty()) {
        for (Account account : db.get().accounts().get(missing)) {
          accounts.put(account.getId(), account);
        }
      }
      for (AccountInfo info : pending) {
        if (info._id != null) {
          Account account = accounts.get(info._id);
          if (account != null) {
            info._id = null;
            info.name = account.getFullName();
            if (!nameOnly) {
              info.email = account.getPreferredEmail();
            }
          }
        }
      }
      pending.clear();
    }
  }

  private transient Id _id;

  protected AccountInfo(Id id) {
    _id = id;
  }

  public String name;
  public String email;
}
