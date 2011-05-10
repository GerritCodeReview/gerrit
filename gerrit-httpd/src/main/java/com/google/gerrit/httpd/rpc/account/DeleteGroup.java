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

import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.AccountGroupMember;
import com.google.gerrit.reviewdb.AccountGroupName;
import com.google.gerrit.reviewdb.RefRight;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.AccountGroup.Id;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.GroupControl;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.ResultSet;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class DeleteGroup extends Handler<AccountGroup.Id> {
  interface Factory {
    DeleteGroup create(AccountGroup.Id groupId);
  }

  private final ReviewDb db;
  private final GroupCache groupCache;
  private final GroupControl.Factory groupControlFactory;
  private final Id groupid;

  @Inject
  DeleteGroup(final ReviewDb db, final GroupCache groupCache,
      final GroupControl.Factory groupControlFactory,
      @Assisted final AccountGroup.Id groupid) {

    this.db = db;
    this.groupCache = groupCache;
    this.groupid = groupid;
    this.groupControlFactory = groupControlFactory;
  }

  @Override
  public AccountGroup.Id call() throws OrmException, NoSuchGroupException {

    final GroupControl ctl = groupControlFactory.validateFor(groupid);
    final AccountGroup group = db.accountGroups().get(groupid);
    if (group == null || !ctl.isOwner()) {
      throw new NoSuchGroupException(groupid);
    }

    deleteFromAccountGroup(group);
    deleteFromAccountGroupNames(group);
    deleteFromAccountGroupMembers();
    deleteFromAccountGroupMembers();
    deleteFromRefRights();
    // note: the audit is NOT cleaned on purpose

    groupCache.evict(group);

    return groupid;
  }

  private void deleteFromAccountGroup(final AccountGroup group)
      throws OrmException {
    db.accountGroups().delete(Collections.singleton(group));
  }

  private void deleteFromAccountGroupNames(final AccountGroup group)
      throws OrmException {
    db.accountGroupNames().delete(
        Collections.singleton(new AccountGroupName(group)));
  }

  private void deleteFromAccountGroupMembers() throws OrmException {
    List<AccountGroupMember.Key> membersKeys =
        new ArrayList<AccountGroupMember.Key>();
    ResultSet<AccountGroupMember> members =
        db.accountGroupMembers().byGroup(groupid);
    for (AccountGroupMember member : members) {
      membersKeys.add(member.getKey());
    }

    db.accountGroupMembers().deleteKeys(membersKeys);
  }

  private void deleteFromRefRights() throws OrmException {
    ResultSet<RefRight> rights = db.refRights().byGroup(groupid);
    db.refRights().delete(rights);
  }

}
