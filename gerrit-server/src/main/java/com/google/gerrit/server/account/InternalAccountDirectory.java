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
import com.google.common.collect.Multimap;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.AvatarInfo;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.avatar.AvatarProvider;
import com.google.gwtorm.server.OrmException;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

@Singleton
public class InternalAccountDirectory extends AccountDirectory {
  static final Set<FillOptions> ID_ONLY =
      Collections.unmodifiableSet(EnumSet.of(FillOptions.ID));

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
    if (options.equals(ID_ONLY)) {
      return;
    }
    Multimap<Account.Id, AccountInfo> missing = ArrayListMultimap.create();
    for (AccountInfo info : in) {
      Account.Id id = new Account.Id(info._accountId);
      AccountState state = accountCache.getIfPresent(id);
      if (state != null) {
        fill(info, state.getAccount(), options);
      } else {
        missing.put(id, info);
      }
    }
    if (!missing.isEmpty()) {
      try {
        for (Account account : db.get().accounts().get(missing.keySet())) {
          if (options.contains(FillOptions.USERNAME)) {
            account.setUserName(AccountState.getUserName(
                db.get().accountExternalIds().byAccount(account.getId()).toList()));
          }
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
    if (options.contains(FillOptions.ID)) {
      info._accountId = account.getId().get();
    } else {
      // Was previously set to look up account for filling.
      info._accountId = null;
    }
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
      AvatarProvider ap = avatar.get();
      if (ap != null) {
        info.avatars = new ArrayList<>(2);
        IdentifiedUser user = userFactory.create(account.getId());

        // GWT UI uses the DEFAULT_SIZE (26).
        addAvatar(ap, info, user, AvatarInfo.DEFAULT_SIZE);

        // PolyGerrit UI prefers 32 and 100.
        if (!info.avatars.isEmpty() && 32 != AvatarInfo.DEFAULT_SIZE) {
          addAvatar(ap, info, user, 32);
        }
        if (!info.avatars.isEmpty() && 100 != AvatarInfo.DEFAULT_SIZE) {
          addAvatar(ap, info, user, 100);
        }
      }
    }
  }

  private static void addAvatar(
      AvatarProvider provider,
      AccountInfo info,
      IdentifiedUser user,
      int size) {
    String url = provider.getUrl(user, size);
    if (url != null) {
      AvatarInfo a = new AvatarInfo();
      a.url = url;
      a.height = size;
      info.avatars.add(a);
    }
  }
}
