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

package com.google.gerrit.server.auth;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.gerrit.server.account.AccountState;
import com.google.inject.Inject;

public class InternalPasswordCredentialsVerifier extends
    PasswordCredentialsVerifier {
  private final InternalUserBackend users;

  @Inject
  InternalPasswordCredentialsVerifier(InternalUserBackend users) {
    this.users = users;
  }

  @Override
  public PasswordAuthUser lookup(PasswordCredentials creds)
      throws UnknownUserException, UserNotAllowedException {
    AccountState who = checkNotNull(users.getByUsername(creds.getUsername()));
    String username = who.getUserName();
    return new PasswordAuthUser(new AuthUser(new AuthUser.UUID(username),
        username), who.getPassword(username));
  }
}
