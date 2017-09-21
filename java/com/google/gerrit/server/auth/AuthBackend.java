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

import com.google.gerrit.extensions.annotations.ExtensionPoint;

/** Implementations of AuthBackend authenticate users for incoming request. */
@ExtensionPoint
public interface AuthBackend {

  /** @return an identifier that uniquely describes the backend. */
  String getDomain();

  /**
   * Authenticate inspects the AuthRequest and returns authenticated user. If the request is unable
   * to be authenticated, an exception will be thrown. The {@link MissingCredentialsException} must
   * be thrown when there are no credentials for the request. It is expected that at most one
   * AuthBackend will either return an AuthUser or throw a non-MissingCredentialsException.
   *
   * @param req the object describing the request.
   * @return the successfully authenticated user.
   * @throws MissingCredentialsException when there are no credentials.
   * @throws InvalidCredentialsException when the credentials are present and invalid.
   * @throws UnknownUserException when the credentials are valid but there is no matching user.
   * @throws UserNotAllowedException when the credentials are valid but the user is not allowed.
   * @throws AuthException when any other error occurs.
   */
  AuthUser authenticate(AuthRequest req)
      throws MissingCredentialsException, InvalidCredentialsException, UnknownUserException,
          UserNotAllowedException, AuthException;
}
