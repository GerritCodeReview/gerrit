// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.client.admin;

import com.google.gerrit.client.data.AccountInfo;
import com.google.gerrit.client.data.AccountInfoCache;
import com.google.gerrit.client.data.AccountInfoCacheFactory;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.AccountGroup;
import com.google.gerrit.client.reviewdb.AccountGroupMember;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gwtorm.client.OrmException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class AccountGroupDetail {
  protected AccountInfoCache accounts;
  protected AccountGroup group;
  protected List<AccountGroupMember> members;
  protected AccountGroup ownerGroup;
  protected boolean autoGroup;

  public AccountGroupDetail() {
  }

  public void load(final ReviewDb db, final AccountInfoCacheFactory acc,
      final AccountGroup g, final boolean isAuto) throws OrmException {
    group = g;
    if (group.getId().equals(group.getOwnerGroupId())) {
      ownerGroup = group;
    } else {
      ownerGroup = db.accountGroups().get(group.getOwnerGroupId());
    }
    autoGroup = isAuto;

    if (!autoGroup) {
      loadMembers(db, acc);
    }
  }

  private void loadMembers(final ReviewDb db, final AccountInfoCacheFactory acc)
      throws OrmException {
    members = db.accountGroupMembers().byGroup(group.getId()).toList();
    for (final AccountGroupMember m : members) {
      acc.want(m.getAccountId());
    }
    accounts = acc.create();

    Collections.sort(members, new Comparator<AccountGroupMember>() {
      public int compare(final AccountGroupMember o1,
          final AccountGroupMember o2) {
        final AccountInfo a = accounts.get(o1.getAccountId());
        final AccountInfo b = accounts.get(o2.getAccountId());
        return n(a).compareTo(n(b));
      }

      private String n(final AccountInfo a) {
        String n = a.getFullName();
        if (n != null && n.length() > 0) {
          return n;
        }

        n = a.getPreferredEmail();
        if (n != null && n.length() > 0) {
          return n;
        }

        return a.getId().toString();
      }
    });
  }

  public void loadOneMember(final ReviewDb db, final Account a,
      final AccountGroupMember m) {
    final AccountInfoCacheFactory acc = new AccountInfoCacheFactory(db);
    acc.put(a);
    acc.want(m.getAccountId());
    members = new ArrayList<AccountGroupMember>(1);
    members.add(m);
    accounts = acc.create();
  }
}
