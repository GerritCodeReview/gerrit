// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.change;

import com.google.common.collect.Sets;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.account.AccountInfoCacheFactory;
import com.google.gerrit.server.account.AccountJson;
import com.google.inject.Inject;

import java.util.Collections;
import java.util.Set;

public class GetAllAssignees implements RestReadView<ChangeResource> {
  private final AccountInfoCacheFactory.Factory accountInfos;

  @Inject
  GetAllAssignees(AccountInfoCacheFactory.Factory accountInfosFactory) {
    this.accountInfos = accountInfosFactory;
  }

  @Override
  public Response<Set<AccountInfo>> apply(ChangeResource rsrc)
      throws Exception {

    Set<Account.Id> historicalAssignees = rsrc.getControl().getNotes().load().getHistoricalAssignees();
    if (historicalAssignees == null) {
      return Response.ok(Collections.emptySet());
    }
    Set<AccountInfo> assignees = Sets.newHashSet();
    AccountInfoCacheFactory accountInfoFactory = accountInfos.create();
    for (Account.Id id : historicalAssignees) {
      assignees.add(AccountJson.toAccountInfo(accountInfoFactory.get(id)));
    }
    return Response.ok(assignees);
  }
}
