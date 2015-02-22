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

/* Contract that OAuth provider must implement */
@ExtensionPoint
public interface OAuthServiceProvider {

  /**
   * Returns the URL where you should redirect your users to authenticate
   * your application.
   *
   * @param requestToken the request token you need to authorize
   * @return the URL where you should redirect your users
   */
  String getAuthorizationUrl(OAuthToken requestToken);

  /**
   * Retrieve the access token
   *
   * @param requestToken request token (obtained previously)
   * @param verifier verifier code
   * @return access token
   */
  OAuthToken getAccessToken(OAuthToken requestToken, OAuthVerifier verifier);

  /**
   * After establishing of secure communication channel, this method supossed to
   * access the protected resoure and retrieve the username.
   *
   * @param token
   * @return username
   * @throws IOException
   */
  String getUsername(OAuthToken token) throws IOException;

  /**
   * Returns the OAuth version of the service.
   *
   * @return oauth version as string
   */
  String getVersion();
}
