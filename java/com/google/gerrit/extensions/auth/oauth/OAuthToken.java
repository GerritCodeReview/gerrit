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

import java.io.Serializable;

/* OAuth token */
public class OAuthToken implements Serializable {

  private static final long serialVersionUID = 1L;

  private final String token;
  private final String secret;
  private final String raw;

  /**
   * Time of expiration of this token, or {@code Long#MAX_VALUE} if this token never expires, or
   * time of expiration is unknown.
   */
  private final long expiresAt;

  /**
   * The identifier of the OAuth provider that issued this token in the form
   * <tt>"plugin-name:provider-name"</tt>, or {@code null}.
   */
  private final String providerId;

  public OAuthToken(String token, String secret, String raw) {
    this(token, secret, raw, Long.MAX_VALUE, null);
  }

  public OAuthToken(String token, String secret, String raw, long expiresAt, String providerId) {
    this.token = token;
    this.secret = secret;
    this.raw = raw;
    this.expiresAt = expiresAt;
    this.providerId = providerId;
  }

  public String getToken() {
    return token;
  }

  public String getSecret() {
    return secret;
  }

  public String getRaw() {
    return raw;
  }

  public long getExpiresAt() {
    return expiresAt;
  }

  public boolean isExpired() {
    return System.currentTimeMillis() > expiresAt;
  }

  public String getProviderId() {
    return providerId;
  }
}
