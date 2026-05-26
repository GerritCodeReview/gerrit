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

import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.annotations.ExtensionPoint;
import java.io.IOException;

/* Contract that OAuth provider must implement */
@ExtensionPoint
public interface OAuthServiceProvider {

  /**
   * Returns the URL where you should redirect your users to authenticate your application.
   *
   * @return the OAuth service URL to redirect your users for authentication
   */
  default String getAuthorizationUrl() {
    return getAuthorizationInfo().getAuthorizationUrl();
  }

  /**
   * Returns the URL where you should redirect your users to authenticate your application.
   *
   * @return the OAuth service URL to redirect your users for authentication and verifier string
   *     generated during initial authorization phase
   */
  default OAuthAuthorizationInfo getAuthorizationInfo() {
    return new OAuthAuthorizationInfo(getAuthorizationUrl(), null);
  }

  /**
   * Retrieve the access token
   *
   * @param verifier verifier code
   * @return access token
   */
  default OAuthToken getAccessToken(OAuthVerifier verifier) {
    return getAccessToken(verifier, null);
  }

  /**
   * Retrieve the access token
   *
   * @param verifier verifier code
   * @param codeVerifier verifier string generated during initial authorization phase
   * @return access token
   */
  default OAuthToken getAccessToken(OAuthVerifier verifier, @Nullable String codeVerifier) {
    return getAccessToken(verifier);
  }

  /**
   * After establishing of secure communication channel, this method supposed to access the
   * protected resource and retrieve the user name.
   *
   * @return OAuth user information
   */
  OAuthUserInfo getUserInfo(OAuthToken token) throws IOException;

  /**
   * Returns the OAuth version of the service.
   *
   * @return oauth version as string
   */
  String getVersion();

  /**
   * Returns the name of this service. This name is resented the user to choose between multiple
   * service providers
   *
   * @return name of the service
   */
  String getName();

  /**
   * Returns a path to an icon for this provider, rendered alongside the provider name on the OAuth
   * login form. May be either an absolute URL (starting with {@code http://} or {@code https://}),
   * a path starting with {@code /}, or a relative path; relative paths are resolved against the
   * plugin's static resource directory ({@code /plugins/<pluginName>/static/}).
   *
   * @return path to the provider icon, or {@code null} if no icon should be rendered
   */
  @Nullable
  default String getIconUrl() {
    return null;
  }
}
