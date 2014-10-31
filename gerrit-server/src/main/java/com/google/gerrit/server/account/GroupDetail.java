// Copyright (C) 2014 The Android Open Source Project
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

import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwtorm.server.OrmException;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GroupDetail {
  public interface Factory {
    GroupDetail create(AccountGroup.UUID groupId, Project.NameKey project);

    GroupDetail create(AccountGroup.UUID groupId);
  }

  private final GroupBackend groupBackend;
  private final AccountGroup.UUID groupUUID;
  private final Project.NameKey project;
  private final AccountCache accountCache;

  @AssistedInject
  GroupDetail(final GroupControl.Factory groupControl,
      final GroupBackend groupBackend, final AccountCache accountCache,
      @Assisted final AccountGroup.UUID groupUUID) {
    this(groupControl, groupBackend, accountCache, groupUUID, null);
  }

  @AssistedInject
  GroupDetail(final GroupControl.Factory groupControl,
      final GroupBackend groupBackend, final AccountCache accountCache,
      @Assisted final AccountGroup.UUID groupUUID,
      @Nullable @Assisted final Project.NameKey project) {
    this.groupBackend = groupBackend;
    this.accountCache = accountCache;
    this.groupUUID = groupUUID;
    this.project = project;
  }

  public GroupDescription.Basic getDescrip() {
    return groupBackend.get(groupUUID);
  }

  public List<Account.Id> getDirctMembers() {
    return groupBackend.loadMembers(groupUUID, true);
  }

  public List<AccountGroup.UUID> getDirectIncludes() {
    return groupBackend.loadIncludes(groupUUID, project, true);
  }

  public Set<Account> listAccounts() throws OrmException, IOException {
    return getGroupMembers(groupUUID, new HashSet<AccountGroup.UUID>());
  }

  private Set<Account> getGroupMembers(final AccountGroup.UUID groupUUID,
      final Set<AccountGroup.UUID> seen) throws OrmException, IOException {
    seen.add(groupUUID);

    final Set<Account> members = new HashSet<>();
    List<Account.Id> m = groupBackend.loadMembers(groupUUID, false);
    if (m != null && !m.isEmpty()) {
      for (final Account.Id member : m) {
        members.add(accountCache.get(member).getAccount());
      }
    }
    List<AccountGroup.UUID> gs =
        groupBackend.loadIncludes(groupUUID, project, false);
    if (gs != null && !gs.isEmpty()) {
      for (final AccountGroup.UUID uuidIncluded : gs) {
        if (!seen.contains(uuidIncluded)) {
          members.addAll(getGroupMembers(uuidIncluded, seen));
        }
      }
    }
    return members;
  }
}
