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

import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.change.AttentionSetEntryResource;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.util.AttentionSetUtil;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class AttentionSet implements ChildCollection<ChangeResource, AttentionSetEntryResource> {
  private final DynamicMap<RestView<AttentionSetEntryResource>> views;
  private final AccountResolver accountResolver;
  private final GetAttentionSet getAttentionSet;

  @Inject
  AttentionSet(
      DynamicMap<RestView<AttentionSetEntryResource>> views,
      GetAttentionSet getAttentionSet,
      AccountResolver accountResolver) {
    this.views = views;
    this.accountResolver = accountResolver;
    this.getAttentionSet = getAttentionSet;
  }

  @Override
  public DynamicMap<RestView<AttentionSetEntryResource>> views() {
    return views;
  }

  @Override
  public RestView<ChangeResource> list() throws ResourceNotFoundException {
    return getAttentionSet;
  }

  @Override
  public AttentionSetEntryResource parse(ChangeResource changeResource, IdString idString)
      throws ResourceNotFoundException,
          AuthException,
          IOException,
          ConfigInvalidException,
          BadRequestException {
    Account.Id accountId =
        AttentionSetUtil.resolveAccount(accountResolver, changeResource.getNotes(), idString.get());
    return new AttentionSetEntryResource(changeResource, accountId);
  }
}
