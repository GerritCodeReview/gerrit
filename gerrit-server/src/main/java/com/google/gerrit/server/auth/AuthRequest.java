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

/**
 * Defines an abstract request for user authentication to Gerrit.
 *
 */
public abstract class AuthRequest {

  /**
   * Returns the username to be authenticated.
   *
   * @return username for authentication.
   */
  public abstract String getUsername();

  /**
   * Returns the user's credentials
   *
   * @return user's credentials or null
   */
  public abstract String getPassword();

  /**
   * Indicates an anonymous user to request authentication.
   *
   * @return true if the request was anonymous.
   */
  public abstract boolean isAnonymous();
}
