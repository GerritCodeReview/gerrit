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

public abstract class PasswordCredentialsVerifier implements CredentialsVerifier<PasswordCredentials> {

  protected final class PasswordAuthUser {
    protected final AuthUser user;
    protected final String password;

    public PasswordAuthUser(AuthUser user, String password) {
      this.user = checkNotNull(user);
      this.password = password;
    }
  }

  @Override
  public final AuthUser verify(PasswordCredentials creds) throws AuthException {
    if (creds.getPassword() == null) {
      throw new InvalidCredentialsException();
    }

    PasswordAuthUser puser = checkNotNull(lookup(creds));
    creds.checkPassword(puser.password);
    return puser.user;
  }

  protected abstract PasswordAuthUser lookup(PasswordCredentials creds) throws AuthException;
}
