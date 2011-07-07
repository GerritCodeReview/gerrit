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
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.server.account.GroupDetailFactory;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

public class GroupDetailHandler extends Handler<GroupDetail> {
  public interface Factory {
    GroupDetailHandler create(AccountGroup.Id groupId);
  }

  private final GroupDetailFactory.Factory groupDetailFactory;

  private final AccountGroup.Id groupId;

  @Inject
  GroupDetailHandler(final GroupDetailFactory.Factory groupDetailFactory,
      @Assisted final AccountGroup.Id groupId) {
    this.groupDetailFactory = groupDetailFactory;
    this.groupId = groupId;
  }

  @Override
  public GroupDetail call() throws OrmException, NoSuchGroupException {
    return groupDetailFactory.create(groupId).call();
  }
}
