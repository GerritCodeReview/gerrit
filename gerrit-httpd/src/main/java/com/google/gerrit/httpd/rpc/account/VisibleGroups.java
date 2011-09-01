// Copyright (C) 2011 The Android Open Source Project
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
import com.google.gerrit.common.data.GroupList;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.GroupControl;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class VisibleGroups extends Handler<GroupList> {

  interface Factory {
    VisibleGroups create();
  }

  private final Provider<IdentifiedUser> identifiedUser;
  private final GroupCache groupCache;
  private final GroupControl.Factory groupControlFactory;
  private final GroupDetailHandler.Factory groupDetailFactory;

  @Inject
  VisibleGroups(final Provider<IdentifiedUser> currentUser,
      final GroupCache groupCache,
      final GroupControl.Factory groupControlFactory,
      final GroupDetailHandler.Factory groupDetailFactory) {
    this.identifiedUser = currentUser;
    this.groupCache = groupCache;
    this.groupControlFactory = groupControlFactory;
    this.groupDetailFactory = groupDetailFactory;
  }

  @Override
  public GroupList call() throws Exception {
    final IdentifiedUser user = identifiedUser.get();
    final List<AccountGroup> list;
    if (user.getCapabilities().canAdministrateServer()) {
      list = new LinkedList<AccountGroup>();
      for (final AccountGroup group : groupCache.all()) {
        list.add(group);
      }
    } else {
      list = new ArrayList<AccountGroup>();
      for(final AccountGroup group : groupCache.all()) {
        final GroupControl c = groupControlFactory.controlFor(group);
        if (c.isVisible()) {
          list.add(c.getAccountGroup());
        }
      }
    }

    List<GroupDetail> l = new ArrayList<GroupDetail>();
    for(AccountGroup group : list) {
      l.add(groupDetailFactory.create(group.getId()).call());
    }
    GroupList res = new GroupList();
    res.setGroups(l);
    res.setCanCreateGroup(user.getCapabilities().canCreateGroup());
    return res;
  }
}
