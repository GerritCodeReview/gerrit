// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.permissions;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@Singleton
public class DefaultPermissionBackend extends PermissionBackend {
  private final Provider<CurrentUser> currentUser;
  private final ProjectCache projectCache;
  private final ProjectControl.Factory projectControlFactory;
  private final IdentifiedUser.GenericFactory identifiedUserFactory;

  @Inject
  DefaultPermissionBackend(
      Provider<CurrentUser> currentUser,
      ProjectCache projectCache,
      ProjectControl.Factory projectControlFactory,
      IdentifiedUser.GenericFactory identifiedUserFactory) {
    this.currentUser = currentUser;
    this.projectCache = projectCache;
    this.projectControlFactory = projectControlFactory;
    this.identifiedUserFactory = identifiedUserFactory;
  }

  @Override
  public WithUser currentUser() {
    return new DefaultWithUserImpl(projectCache, projectControlFactory, currentUser.get());
  }

  @Override
  public WithUser user(CurrentUser user) {
    return new DefaultWithUserImpl(projectCache, projectControlFactory, checkNotNull(user, "user"));
  }

  @Override
  public WithUser absentUser(Account.Id user) {
    IdentifiedUser identifiedUser = identifiedUserFactory.create(checkNotNull(user, "user"));
    return new DefaultWithUserImpl(projectCache, projectControlFactory, identifiedUser);
  }

  @Override
  public boolean usesDefaultCapabilities() {
    return true;
  }
}
