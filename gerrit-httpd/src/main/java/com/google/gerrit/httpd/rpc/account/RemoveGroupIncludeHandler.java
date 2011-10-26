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

import com.google.gerrit.common.data.GroupMemberResult;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.server.account.RemoveGroupInclude;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.Collection;

public class RemoveGroupIncludeHandler extends Handler<GroupMemberResult> {

  interface Factory {
    RemoveGroupIncludeHandler create(AccountGroup.Id groupId,
        Collection<AccountGroup.Id> groupsToRemove);
  }

  private final RemoveGroupInclude.Factory removeGroupIncludeFactory;

  private final AccountGroup.Id groupId;
  private final Collection<AccountGroup.Id> groupsToRemove;

  @Inject
  public RemoveGroupIncludeHandler(
      final RemoveGroupInclude.Factory removeGroupIncludeFactory,
      final @Assisted AccountGroup.Id groupId,
      final @Assisted Collection<AccountGroup.Id> groupsToRemove) {
    this.removeGroupIncludeFactory = removeGroupIncludeFactory;
    this.groupId = groupId;
    this.groupsToRemove = groupsToRemove;
  }

  @Override
  public GroupMemberResult call() throws Exception {
    return removeGroupIncludeFactory.create(groupId, groupsToRemove).call();
  }
}
