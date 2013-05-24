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

package com.google.gerrit.server.auth;

import com.google.gerrit.extensions.annotations.ExtensionPoint;

/**
 * Implementations of CredentialsVerifier authenticate users for incoming
 * request.
 */
@ExtensionPoint
public interface CredentialsVerifier<T extends Credentials> {

  /**
   * Authenticate inspects the Credentials and returns authenticated user.
   *
   * @param creds the object describing the request.
   * @return the successfully authenticated user.
   * @throws InvalidCredentialsException when the credentials are present and
   *         invalid.
   * @throws UnknownUserException when the credentials are valid but there is
   *         no matching user.
   * @throws UserNotAllowedException when the credentials are valid but the user
   *         is not allowed.
   * @throws AuthException when any other error occurs.
   */
  AuthUser verify(T creds) throws InvalidCredentialsException,
      UnknownUserException, UserNotAllowedException, AuthException;
}
