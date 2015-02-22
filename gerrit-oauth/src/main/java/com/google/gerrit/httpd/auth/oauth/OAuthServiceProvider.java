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

package com.google.gerrit.httpd.auth.oauth;

import com.google.gerrit.extensions.annotations.ExtensionPoint;

import org.scribe.model.Token;
import org.scribe.oauth.OAuthService;

import java.io.IOException;

/* Contract that OAuth provider must implement */
@ExtensionPoint
public interface OAuthServiceProvider {

  /**
   * A facade responsible for the retrieval of request and access tokens and for
   * the signing of HTTP requests.
   *
   * @return OAuthService
   */
  OAuthService getService();

  /**
   * After establishing of secure communication channel, this method supossed to
   * access the protected resoure and retrieve the username.
   *
   * @param access token
   * @return username
   * @throws IOException
   */
  String getUserInfo(Token token) throws IOException;

  /**
   * Configuration option if anonymous browsing is enabled for this service
   * provider
   *
   * @return
   */
  boolean isAutoLogin();

  /**
   * Configuration option that provides Gerrit authentication header
   * @return
   */
  String getAuthHttpHeader();
}
