// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.server.auth.oauth;

import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.annotations.ExtensionPoint;

/**
 * Notified when an account's OAuth token is revoked and evicted (single account), or when all
 * tokens are revoked in bulk, so a plugin holding token-derived state (e.g. a Git-over-HTTP
 * validation cache) can invalidate it.
 *
 * <p>A single-account revocation fires {@link #onTokenRevoked} once, after the token is revoked at
 * the IdP (RFC 7009) and evicted from the {@code oauth_tokens} cache. A bulk {@code revoke --all}
 * fires {@link #onAllTokensRevoked} instead (possibly more than once, before and after the revoke
 * loop). The two are never mixed for one operation.
 */
@ExtensionPoint
public interface OAuthTokenRevokedListener {
  /**
   * Called after {@code accountId}'s OAuth token was revoked and evicted.
   *
   * @param accountId the affected account
   */
  void onTokenRevoked(Account.Id accountId);

  /**
   * Called for a bulk revocation ({@code revoke --all}) instead of per account. May fire more than
   * once per operation (before and after the revoke loop) and even when nothing was cached, so
   * implementations must be idempotent. Default: no-op.
   */
  default void onAllTokensRevoked() {}
}
