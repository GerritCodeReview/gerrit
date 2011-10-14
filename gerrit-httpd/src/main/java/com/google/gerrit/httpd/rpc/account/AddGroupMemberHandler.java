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
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.server.account.AddGroupMember;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

public class AddGroupMemberHandler extends Handler<GroupDetail> {

  interface Factory {
    AddGroupMemberHandler create(AccountGroup.Id groupId, String nameOrEmail);
  }

  private final AddGroupMember.Factory addGroupMemberFactory;

  private final AccountGroup.Id groupId;
  private final String nameOrEmail;

  @Inject
  AddGroupMemberHandler(final AddGroupMember.Factory addGroupMemberFactory,
      final @Assisted AccountGroup.Id groupId,
      final @Assisted String nameOrEmail) {
    this.addGroupMemberFactory = addGroupMemberFactory;
    this.groupId = groupId;
    this.nameOrEmail = nameOrEmail;
  }

  @Override
  public GroupDetail call() throws Exception {
    return addGroupMemberFactory.create(groupId, nameOrEmail).call();
  }
}
