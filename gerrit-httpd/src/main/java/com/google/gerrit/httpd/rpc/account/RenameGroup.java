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
import com.google.gerrit.common.errors.NameAlreadyUsedException;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.server.account.PerformRenameGroup;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

class RenameGroup extends Handler<GroupDetail> {
  interface Factory {
    RenameGroup create(AccountGroup.Id id, String newName);
  }

  private final PerformRenameGroup.Factory performRenameGroupFactory;

  private final AccountGroup.Id groupId;
  private final String newName;

  @Inject
  RenameGroup(final PerformRenameGroup.Factory performRenameGroupFactory,
      @Assisted final AccountGroup.Id groupId, @Assisted final String newName) {
    this.performRenameGroupFactory = performRenameGroupFactory;
    this.groupId = groupId;
    this.newName = newName;
  }

  @Override
  public GroupDetail call() throws OrmException, NameAlreadyUsedException,
      NoSuchGroupException {
    return performRenameGroupFactory.create().renameGroup(groupId, newName);
  }
}
