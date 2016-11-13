// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.account;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.reviewdb.client.AccountSshKey;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class SshKeys implements ChildCollection<AccountResource, AccountResource.SshKey> {
  private final DynamicMap<RestView<AccountResource.SshKey>> views;
  private final GetSshKeys list;
  private final Provider<CurrentUser> self;
  private final VersionedAuthorizedKeys.Accessor authorizedKeys;

  @Inject
  SshKeys(
      DynamicMap<RestView<AccountResource.SshKey>> views,
      GetSshKeys list,
      Provider<CurrentUser> self,
      VersionedAuthorizedKeys.Accessor authorizedKeys) {
    this.views = views;
    this.list = list;
    this.self = self;
    this.authorizedKeys = authorizedKeys;
  }

  @Override
  public RestView<AccountResource> list() {
    return list;
  }

  @Override
  public AccountResource.SshKey parse(AccountResource rsrc, IdString id)
      throws ResourceNotFoundException, OrmException, IOException, ConfigInvalidException {
    if (self.get() != rsrc.getUser() && !self.get().getCapabilities().canModifyAccount()) {
      throw new ResourceNotFoundException();
    }
    return parse(rsrc.getUser(), id);
  }

  public AccountResource.SshKey parse(IdentifiedUser user, IdString id)
      throws ResourceNotFoundException, IOException, ConfigInvalidException {
    try {
      int seq = Integer.parseInt(id.get(), 10);
      AccountSshKey sshKey = authorizedKeys.getKey(user.getAccountId(), seq);
      if (sshKey == null) {
        throw new ResourceNotFoundException(id);
      }
      return new AccountResource.SshKey(user, sshKey);
    } catch (NumberFormatException e) {
      throw new ResourceNotFoundException(id);
    }
  }

  @Override
  public DynamicMap<RestView<AccountResource.SshKey>> views() {
    return views;
  }
}
