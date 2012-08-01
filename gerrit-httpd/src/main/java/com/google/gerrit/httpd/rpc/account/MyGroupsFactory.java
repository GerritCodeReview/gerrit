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
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.VisibleGroups;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import java.util.List;

class MyGroupsFactory extends Handler<List<AccountGroup>> {
  interface Factory {
    MyGroupsFactory create();
  }

  private final VisibleGroups.Factory visibleGroupsFactory;
  private final IdentifiedUser user;

  @Inject
  MyGroupsFactory(final VisibleGroups.Factory visibleGroupsFactory, final IdentifiedUser user) {
    this.visibleGroupsFactory = visibleGroupsFactory;
    this.user = user;
  }

  @Override
  public List<AccountGroup> call() throws OrmException, NoSuchGroupException {
    final VisibleGroups visibleGroups = visibleGroupsFactory.create();
    return visibleGroups.get(user).getGroups();
  }
}
