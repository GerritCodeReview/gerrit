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

import static java.util.stream.Collectors.toList;

import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.account.AccountInfoCacheFactory;
import com.google.gerrit.server.account.AccountJson;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class GetPastAssignees implements RestReadView<ChangeResource> {
  private final AccountInfoCacheFactory.Factory accountInfos;

  @Inject
  GetPastAssignees(AccountInfoCacheFactory.Factory accountInfosFactory) {
    this.accountInfos = accountInfosFactory;
  }

  @Override
  public Response<List<AccountInfo>> apply(ChangeResource rsrc) throws OrmException {

    Set<Account.Id> pastAssignees = rsrc.getControl().getNotes().load().getPastAssignees();
    if (pastAssignees == null) {
      return Response.ok(Collections.emptyList());
    }
    AccountInfoCacheFactory accountInfoFactory = accountInfos.create();

    return Response.ok(
        pastAssignees
            .stream()
            .map(accountInfoFactory::get)
            .map(AccountJson::toAccountInfo)
            .collect(toList()));
  }
}
