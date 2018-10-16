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

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.gerrit.common.Nullable;
import java.io.Serializable;
import java.util.Objects;

/**
 * OAuth token.
 *
 * <p>Only implements {@link Serializable} for backwards compatibility; new extensions should not
 * depend on the serialized format.
 */
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
   * The identifier of the OAuth provider that issued this token in the form {@code
   * "plugin-name:provider-name"}, or {@code null}. The empty string {@code ""} is treated the same
   * as {@code null}.
   */
  private final String providerId;

  public OAuthToken(String token, String secret, String raw) {
    this(token, secret, raw, Long.MAX_VALUE, null);
  }

  public OAuthToken(
      String token, String secret, String raw, long expiresAt, @Nullable String providerId) {
    this.token = requireNonNull(token, "token");
    this.secret = requireNonNull(secret, "secret");
    this.raw = requireNonNull(raw, "raw");
    this.expiresAt = expiresAt;
    this.providerId = Strings.emptyToNull(providerId);
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

  @Nullable
  public String getProviderId() {
    return providerId;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof OAuthToken)) {
      return false;
    }
    OAuthToken t = (OAuthToken) o;
    return token.equals(t.token)
        && secret.equals(t.secret)
        && raw.equals(t.raw)
        && expiresAt == t.expiresAt
        && Objects.equals(providerId, t.providerId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(token, secret, raw, expiresAt, providerId);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("token", token)
        .add("secret", secret)
        .add("raw", raw)
        .add("expiresAt", expiresAt)
        .add("providerId", providerId)
        .toString();
  }
}
