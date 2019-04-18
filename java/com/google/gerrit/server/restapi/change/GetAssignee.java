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

package com.google.gerrit.server.restapi.change;

import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;

@Singleton
public class GetAssignee implements RestReadView<ChangeResource> {
  private final AccountLoader.Factory accountLoaderFactory;

  @Inject
  GetAssignee(AccountLoader.Factory accountLoaderFactory) {
    this.accountLoaderFactory = accountLoaderFactory;
  }

  @Override
  public Response<AccountInfo> apply(ChangeResource rsrc) throws PermissionBackendException {
    Optional<Account.Id> assignee = Optional.ofNullable(rsrc.getChange().getAssignee());
    if (assignee.isPresent()) {
      return Response.ok(accountLoaderFactory.create(true).fillOne(assignee.get()));
    }
    return Response.none();
  }
}
