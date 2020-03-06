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

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.AttentionSetUpdate;
import com.google.gerrit.entities.AttentionSetUpdate.Operation;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.AttentionSetEntry;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.sql.Timestamp;
import java.util.List;

@Singleton
public class GetAttentionSet implements RestReadView<ChangeResource> {
  private final AccountLoader.Factory accountLoaderFactory;

  @Inject
  GetAttentionSet(AccountLoader.Factory accountLoaderFactory) {
    this.accountLoaderFactory = accountLoaderFactory;
  }

  @Override
  public Response<List<AttentionSetEntry>> apply(ChangeResource changeResource)
      throws PermissionBackendException {
    ImmutableList<AttentionSetUpdate> attentionSet = changeResource.getNotes().getAttentionSet();
    ImmutableList.Builder<AttentionSetEntry> response = ImmutableList.builder();
    for (AttentionSetUpdate attentionSetUpdate : attentionSet) {
      // ö skip removals (for now)
      response.add(
          new AttentionSetEntry(
              new AccountInfo(attentionSetUpdate.account().get()),
              Timestamp.from(attentionSetUpdate.timestamp()),
              (attentionSetUpdate.operation() == Operation.ADD ? "" : "removed => ")  // ö
                  + attentionSetUpdate.reason()));
    }
    return Response.ok(response.build());
  }
}
