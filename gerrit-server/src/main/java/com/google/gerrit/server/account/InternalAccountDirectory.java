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
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountInfo.AvatarInfo;
import com.google.gerrit.server.avatar.AvatarProvider;
import com.google.gwtorm.server.OrmException;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import java.util.Set;

@Singleton
public class InternalAccountDirectory extends AccountDirectory {
  public static class Module extends AbstractModule {
    @Override
    protected void configure() {
      bind(AccountDirectory.class).to(InternalAccountDirectory.class);
    }
  }

  private final Provider<ReviewDb> db;
  private final AccountCache accountCache;
  private final DynamicItem<AvatarProvider> avatar;
  private final IdentifiedUser.GenericFactory userFactory;

  @Inject
  InternalAccountDirectory(Provider<ReviewDb> db,
      AccountCache accountCache,
      DynamicItem<AvatarProvider> avatar,
      IdentifiedUser.GenericFactory userFactory) {
    this.db = db;
    this.accountCache = accountCache;
    this.avatar = avatar;
    this.userFactory = userFactory;
  }

  @Override
  public void fillAccountInfo(
      Iterable<? extends AccountInfo> in,
      Set<FillOptions> options)
      throws DirectoryException {
    Multimap<Account.Id, AccountInfo> missing = ArrayListMultimap.create();
    for (AccountInfo info : in) {
      AccountState state = accountCache.getIfPresent(info._id);
      if (state != null) {
        fill(info, state.getAccount(), options);
      } else {
        missing.put(info._id, info);
      }
    }
    if (!missing.isEmpty()) {
      try {
        for (Account account : db.get().accounts().get(missing.keySet())) {
          for (AccountInfo info : missing.get(account.getId())) {
            fill(info, account, options);
          }
        }
      } catch (OrmException e) {
        throw new DirectoryException(e);
      }
    }
  }

  private void fill(AccountInfo info,
      Account account,
      Set<FillOptions> options) {
    if (options.contains(FillOptions.NAME)) {
      info.name = Strings.emptyToNull(account.getFullName());
      if (info.name == null) {
        info.name = account.getUserName();
      }
    }
    if (options.contains(FillOptions.EMAIL)) {
      info.email = account.getPreferredEmail();
    }
    if (options.contains(FillOptions.USERNAME)) {
      info.username = account.getUserName();
    }
    if (options.contains(FillOptions.AVATARS)) {
      info.avatars = Lists.newArrayListWithCapacity(1);
      AvatarProvider ap = avatar.get();
      if (ap != null) {
        String u = ap.getUrl(
            userFactory.create(account.getId()),
            AvatarInfo.DEFAULT_SIZE);
        if (u != null) {
          AvatarInfo a = new AvatarInfo();
          a.url = u;
          a.height = AvatarInfo.DEFAULT_SIZE;
          info.avatars.add(a);
        }
      }
    }
  }
}
