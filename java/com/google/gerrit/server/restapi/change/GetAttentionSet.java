// Copyright (C) 2020 The Android Open Source Project
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

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.gerrit.server.util.AttentionSetUtil.additionsOnly;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.extensions.common.AttentionSetEntry;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.sql.Timestamp;
import java.util.Set;

/** Reads the list of users currently in the attention set. */
@Singleton
public class GetAttentionSet implements RestReadView<ChangeResource> {

  private final AccountLoader.Factory accountLoaderFactory;

  @Inject
  GetAttentionSet(AccountLoader.Factory accountLoaderFactory) {
    this.accountLoaderFactory = accountLoaderFactory;
  }

  @Override
  public Response<Set<AttentionSetEntry>> apply(ChangeResource changeResource)
      throws PermissionBackendException {
    AccountLoader accountLoader = accountLoaderFactory.create(true);
    ImmutableSet<AttentionSetEntry> response =
        // This filtering should match ChangeJson.
        additionsOnly(changeResource.getNotes().getAttentionSet()).stream()
            .map(
                a ->
                    new AttentionSetEntry(
                        accountLoader.get(a.account()), Timestamp.from(a.timestamp()), a.reason()))
            .collect(toImmutableSet());
    accountLoader.fill();
    return Response.ok(response);
  }
}
