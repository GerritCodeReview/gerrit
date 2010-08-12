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

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.util.FutureUtil;
import com.google.inject.Inject;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

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
  public List<AccountGroup> call() throws Exception {
    List<ListenableFuture<AccountGroup>> want = Lists.newArrayList();
    for (AccountGroup.Id id : user.getEffectiveGroups()) {
      want.add(groupCache.get(id));
    }

    List<AccountGroup> all = FutureUtil.get(FutureUtil.concatSingletons(want));
    Collections.sort(all, new Comparator<AccountGroup>() {
      @Override
      public int compare(AccountGroup a, AccountGroup b) {
        return a.getName().compareTo(b.getName());
      }
    });
    return all;
  }
}
