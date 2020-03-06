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
import com.google.gerrit.entities.AttentionSetUpdate.Operation;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.AttentionSetEntry;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Singleton;
import java.sql.Timestamp;
import java.util.List;

/** Reads the list of users currently in the attention set. */
@Singleton
public class GetAttentionSet implements RestReadView<ChangeResource> {
  @Override
  public Response<List<AttentionSetEntry>> apply(ChangeResource changeResource)
      throws PermissionBackendException {
    return Response.ok(
        changeResource.getNotes().getAttentionSet().stream()
            // This filtering should match ChangeJson.
            .filter(a -> a.operation() == Operation.ADD)
            .map(
                a ->
                    new AttentionSetEntry(
                        new AccountInfo(a.account().get()),
                        Timestamp.from(a.timestamp()),
                        a.reason()))
            .collect(ImmutableList.toImmutableList()));
  }
}
