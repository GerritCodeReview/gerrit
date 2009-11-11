// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.httpd.rpc.account;

import com.google.gerrit.common.data.GroupDetail;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.AccountGroupMember;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.account.AccountInfoCacheFactory;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.account.NoSuchGroupException;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

class GroupDetailFactory extends Handler<GroupDetail> {
  interface Factory {
    GroupDetailFactory create(AccountGroup.Id groupId);
  }

  private final ReviewDb db;
  private final GroupControl.Factory groupControl;
  private final GroupCache groupCache;
  private final AccountInfoCacheFactory aic;

  private final AccountGroup.Id groupId;
  private GroupControl control;

  @Inject
  GroupDetailFactory(final ReviewDb db,
      final GroupControl.Factory groupControl, final GroupCache groupCache,
      final AccountInfoCacheFactory.Factory accountInfoCacheFactory,
      @Assisted final AccountGroup.Id groupId) {
    this.db = db;
    this.groupControl = groupControl;
    this.groupCache = groupCache;
    this.aic = accountInfoCacheFactory.create();

    this.groupId = groupId;
  }

  @Override
  public GroupDetail call() throws OrmException, NoSuchGroupException {
    control = groupControl.validateFor(groupId);
    final AccountGroup group = control.getAccountGroup();
    final GroupDetail detail = new GroupDetail();
    detail.setGroup(group);
    detail.setOwnerGroup(groupCache.get(group.getOwnerGroupId()));
    switch (group.getType()) {
      case INTERNAL:
        detail.setMembers(loadMembers());
        break;
    }
    detail.setAccounts(aic.create());
    return detail;
  }

  private List<AccountGroupMember> loadMembers() throws OrmException {
    List<AccountGroupMember> members = new ArrayList<AccountGroupMember>();
    for (final AccountGroupMember m : db.accountGroupMembers().byGroup(groupId)) {
      if (control.canSee(m.getAccountId())) {
        aic.want(m.getAccountId());
        members.add(m);
      }
    }

    Collections.sort(members, new Comparator<AccountGroupMember>() {
      public int compare(final AccountGroupMember o1,
          final AccountGroupMember o2) {
        final Account a = aic.get(o1.getAccountId());
        final Account b = aic.get(o2.getAccountId());
        return n(a).compareTo(n(b));
      }

      private String n(final Account a) {
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
    return members;
  }
}
