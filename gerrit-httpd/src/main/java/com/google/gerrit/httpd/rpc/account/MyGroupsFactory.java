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

import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.GroupCache;
import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

class MyGroupsFactory extends Handler<List<AccountGroup>> {
  interface Factory {
    MyGroupsFactory create();
  }

  private final GroupCache groupCache;
  private final IdentifiedUser user;

  @Inject
  MyGroupsFactory(final GroupCache groupCache, final IdentifiedUser user) {
    this.groupCache = groupCache;
    this.user = user;
  }

  @Override
  public List<AccountGroup> call() {
    final Set<AccountGroup.UUID> effective = user.getEffectiveGroups();
    final int cnt = effective.size();
    final List<AccountGroup> groupList = new ArrayList<AccountGroup>(cnt);
    for (final AccountGroup.UUID groupId : effective) {
      groupList.add(groupCache.get(groupId));
    }
    Collections.sort(groupList, new Comparator<AccountGroup>() {
      @Override
      public int compare(AccountGroup a, AccountGroup b) {
        return a.getName().compareTo(b.getName());
      }
    });
    return groupList;
  }
}
