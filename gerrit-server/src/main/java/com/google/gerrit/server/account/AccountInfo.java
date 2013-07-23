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

package com.google.gerrit.server.account;

import com.google.common.base.Strings;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.avatar.AvatarProvider;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class AccountInfo {
  public static class Loader {
    public interface Factory {
      Loader create(boolean detailed);
    }

    private final Provider<ReviewDb> db;
    private final AccountCache accountCache;
    private final DynamicItem<AvatarProvider> avatar;
    private final IdentifiedUser.GenericFactory userFactory;
    private final boolean detailed;
    private final Map<Account.Id, AccountInfo> created;
    private final List<AccountInfo> provided;

    @Inject
    Loader(Provider<ReviewDb> db,
        AccountCache accountCache,
        DynamicItem<AvatarProvider> avatar,
        IdentifiedUser.GenericFactory userFactory,
        @Assisted boolean detailed) {
      this.db = db;
      this.accountCache = accountCache;
      this.avatar = avatar;
      this.userFactory = userFactory;
      this.detailed = detailed;
      created = Maps.newHashMap();
      provided = Lists.newArrayList();
    }

    public AccountInfo get(Account.Id id) {
      if (id == null) {
        return null;
      }
      AccountInfo info = created.get(id);
      if (info == null) {
        info = new AccountInfo(id);
        created.put(id, info);
      }
      return info;
    }

    public void put(AccountInfo info) {
      provided.add(info);
    }

    public void fill() throws OrmException {
      Multimap<Account.Id, AccountInfo> missing = ArrayListMultimap.create();
      for (AccountInfo info : Iterables.concat(created.values(), provided)) {
        AccountState state = accountCache.getIfPresent(info._id);
        if (state != null) {
          fill(info, state.getAccount());
        } else {
          missing.put(info._id, info);
        }
      }
      if (!missing.isEmpty()) {
        for (Account account : db.get().accounts().get(missing.keySet())) {
          for (AccountInfo info : missing.get(account.getId())) {
            fill(info, account);
          }
        }
      }
    }

    public void fill(Collection<? extends AccountInfo> infos)
        throws OrmException {
      for (AccountInfo info : infos) {
        put(info);
      }
      fill();
    }

    private void fill(AccountInfo info, Account account) {
      info.name = Strings.emptyToNull(account.getFullName());
      if (info.name == null) {
        info.name = account.getUserName();
      }

      if (detailed) {
        info._account_id = account.getId().get();
        info.email = account.getPreferredEmail();
        info.username = account.getUserName();
      }

      info.avatars = Lists.newArrayListWithCapacity(1);
      AvatarProvider ap = avatar.get();
      if (ap != null) {
        String u = ap.getUrl(userFactory.create(account.getId()), 26);
        if (u != null) {
          info.avatars.add(new AvatarInfo(u, 26));
        }
      }
    }
  }

  public transient Account.Id _id;

  public AccountInfo(Account.Id id) {
    _id = id;
  }

  public Integer _account_id;
  public String name;
  public String email;
  public String username;
  public List<AvatarInfo> avatars;

  public static class AvatarInfo {
    String url;
    int height;

    AvatarInfo(String url, int height) {
      this.url = url;
      this.height = height;
    }
  }
}
