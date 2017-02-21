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

import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@Singleton
public class GetHttpPassword implements RestReadView<AccountResource> {
  private final PermissionBackend permissionBackend;
  private final Provider<CurrentUser> self;

  @Inject
  GetHttpPassword(PermissionBackend permissionBackend, Provider<CurrentUser> self) {
    this.permissionBackend = permissionBackend;
    this.self = self;
  }

  @Override
  public String apply(AccountResource rsrc)
      throws AuthException, ResourceNotFoundException, PermissionBackendException {
    if (self.get() != rsrc.getUser()) {
      permissionBackend.user(self).check(GlobalPermission.ADMINISTRATE_SERVER);
    }

    AccountState s = rsrc.getUser().state();
    if (s.getUserName() == null) {
      throw new ResourceNotFoundException();
    }
    String p = s.getPassword(s.getUserName());
    if (p == null) {
      throw new ResourceNotFoundException();
    }
    return p;
  }
}
