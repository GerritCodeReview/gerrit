// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.extensions.auth.oauth;

import com.google.gerrit.extensions.annotations.ExtensionPoint;
import java.io.IOException;

@ExtensionPoint
public interface OAuthLoginProvider {

  /**
   * Performs a login with an OAuth2 provider for Git over HTTP communication.
   *
   * <p>An implementation of this interface must transmit the given user name and secret, which can
   * be either an OAuth2 access token or a password, to the OAuth2 backend for verification.
   *
   * @param username the user's identifier.
   * @param secret the secret to verify, e.g. a previously received access token or a password.
   * @return information about the logged in user, at least external id, user name and email
   *     address.
   * @throws IOException if the login failed.
   */
  OAuthUserInfo login(String username, String secret) throws IOException;
}
