// Copyright (C) 2012 The Android Open Source Project
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
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.extensions.restapi.RestCollection;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.inject.Inject;
import com.google.inject.Provider;

public class AccountsCollection implements
    RestCollection<TopLevelResource, AccountResource> {
  private final Provider<CurrentUser> self;
  private final DynamicMap<RestView<AccountResource>> views;

  @Inject
  AccountsCollection(Provider<CurrentUser> self,
      DynamicMap<RestView<AccountResource>> views) {
    this.self = self;
    this.views = views;
  }

  @Override
  public AccountResource parse(TopLevelResource root, String id)
      throws ResourceNotFoundException {
    if ("self".equals(id)) {
      CurrentUser user = self.get();
      if (user instanceof IdentifiedUser) {
        return new AccountResource((IdentifiedUser) user);
      }
      throw new ResourceNotFoundException(id);
    }
    throw new ResourceNotFoundException(id);
  }

  @Override
  public RestView<TopLevelResource> list() throws ResourceNotFoundException {
    throw new ResourceNotFoundException();
  }

  @Override
  public DynamicMap<RestView<AccountResource>> views() {
    return views;
  }
}
