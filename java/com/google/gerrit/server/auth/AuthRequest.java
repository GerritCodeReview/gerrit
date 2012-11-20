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

import com.google.common.base.Strings;
import com.google.gerrit.common.Nullable;
import java.util.Objects;
import java.util.Optional;

/** Defines an abstract request for user authentication to Gerrit. */
public abstract class AuthRequest {
  private final Optional<String> username;
  private final Optional<String> password;

  protected AuthRequest(@Nullable String username, @Nullable String password) {
    this.username = Optional.ofNullable(Strings.emptyToNull(username));
    this.password = Optional.ofNullable(Strings.emptyToNull(password));
  }

  /**
   * Returns the username to be authenticated.
   *
   * @return username for authentication or null for anonymous access.
   */
  public final Optional<String> getUsername() {
    return username;
  }

  /**
   * Returns the user's credentials
   *
   * @return user's credentials or null
   */
  public final Optional<String> getPassword() {
    return password;
  }

  public void checkPassword(Object pwd) throws AuthException {
    if (!Objects.equals(getPassword(), pwd)) {
      throw new InvalidCredentialsException();
    }
  }
}
