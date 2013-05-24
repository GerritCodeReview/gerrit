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

import com.google.common.base.Objects;
import com.google.gerrit.common.Nullable;
import com.google.inject.TypeLiteral;

/**
 * Credentials implementation for password based user verification
 */
public class PasswordCredentials extends Credentials {
  public static final TypeLiteral<CredentialsVerifier<PasswordCredentials>> VERIFIER_TYPE =
      new TypeLiteral<CredentialsVerifier<PasswordCredentials>>() {};

  private final String username;
  private final String password;

  public PasswordCredentials(String username, String password) {
    this.username = checkNotNull(username);
    this.password = password;
  }

  /**
   * Returns the username.
   *
   * @return username for authentication.
   */
  public final String getUsername() {
    return username;
  }

  /**
   * Returns the user's password.
   *
   * @return user's password or null
   */
  @Nullable
  public final String getPassword() {
    return password;
  }

  public void checkPassword(String pwd) throws AuthException {
    if (!Objects.equal(getPassword(), pwd)) {
      throw new InvalidCredentialsException();
    }
  }
}
