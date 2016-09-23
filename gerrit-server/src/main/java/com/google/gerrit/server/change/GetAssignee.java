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

import com.google.common.base.Optional;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.account.AccountInfoCacheFactory;
import com.google.gerrit.server.account.AccountJson;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class GetAssignee implements RestReadView<ChangeResource> {
  private final AccountInfoCacheFactory.Factory accountInfo;

  @Inject
  GetAssignee(AccountInfoCacheFactory.Factory accountInfo) {
    this.accountInfo = accountInfo;
  }

  @Override
  public Response<AccountInfo> apply(ChangeResource rsrc) throws OrmException {

    Optional<Account.Id> assignee =
        Optional.fromNullable(rsrc.getChange().getAssignee());
    if (assignee.isPresent()) {
      Account account = accountInfo.create().get(assignee.get());
      return Response.ok(AccountJson.toAccountInfo(account));
    }
    return Response.none();
  }
}
