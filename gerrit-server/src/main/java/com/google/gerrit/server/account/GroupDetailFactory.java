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

package com.google.gerrit.server.account;

import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.common.data.GroupDetail;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupById;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.group.GroupInfoCache;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;

public class GroupDetailFactory implements Callable<GroupDetail> {
  public interface Factory {
    GroupDetailFactory create(AccountGroup.Id groupId);
  }

  private final ReviewDb db;
  private final GroupControl.Factory groupControl;
  private final GroupCache groupCache;
  private final GroupBackend groupBackend;
  private final AccountInfoCacheFactory aic;
  private final GroupInfoCache gic;

  private final AccountGroup.Id groupId;
  private GroupControl control;

  @Inject
  GroupDetailFactory(
      ReviewDb db,
      GroupControl.Factory groupControl,
      GroupCache groupCache,
      GroupBackend groupBackend,
      AccountInfoCacheFactory.Factory accountInfoCacheFactory,
      GroupInfoCache.Factory groupInfoCacheFactory,
      @Assisted AccountGroup.Id groupId) {
    this.db = db;
    this.groupControl = groupControl;
    this.groupCache = groupCache;
    this.groupBackend = groupBackend;
    this.aic = accountInfoCacheFactory.create();
    this.gic = groupInfoCacheFactory.create();

    this.groupId = groupId;
  }

  @Override
  public GroupDetail call() throws OrmException, NoSuchGroupException {
    control = groupControl.validateFor(groupId);
    AccountGroup group = groupCache.get(groupId);
    GroupDetail detail = new GroupDetail();
    detail.setGroup(group);
    GroupDescription.Basic ownerGroup = groupBackend.get(group.getOwnerGroupUUID());
    if (ownerGroup != null) {
      detail.setOwnerGroup(GroupReference.forGroup(ownerGroup));
    }
    detail.setMembers(loadMembers());
    detail.setIncludes(loadIncludes());
    detail.setAccounts(aic.create());
    detail.setCanModify(control.isOwner());
    return detail;
  }

  private List<AccountGroupMember> loadMembers() throws OrmException {
    List<AccountGroupMember> members = new ArrayList<>();
    for (AccountGroupMember m : db.accountGroupMembers().byGroup(groupId)) {
      if (control.canSeeMember(m.getAccountId())) {
        aic.want(m.getAccountId());
        members.add(m);
      }
    }

    Collections.sort(
        members,
        new Comparator<AccountGroupMember>() {
          @Override
          public int compare(AccountGroupMember o1, AccountGroupMember o2) {
            Account a = aic.get(o1.getAccountId());
            Account b = aic.get(o2.getAccountId());
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

  private List<AccountGroupById> loadIncludes() throws OrmException {
    List<AccountGroupById> groups = new ArrayList<>();

    for (AccountGroupById m : db.accountGroupById().byGroup(groupId)) {
      if (control.canSeeGroup()) {
        gic.want(m.getIncludeUUID());
        groups.add(m);
      }
    }

    Collections.sort(
        groups,
        new Comparator<AccountGroupById>() {
          @Override
          public int compare(AccountGroupById o1, AccountGroupById o2) {
            GroupDescription.Basic a = gic.get(o1.getIncludeUUID());
            GroupDescription.Basic b = gic.get(o2.getIncludeUUID());
            return n(a).compareTo(n(b));
          }

          private String n(GroupDescription.Basic a) {
            if (a == null) {
              return "";
            }

            String n = a.getName();
            if (n != null && n.length() > 0) {
              return n;
            }
            return a.getGroupUUID().get();
          }
        });

    return groups;
  }
}
