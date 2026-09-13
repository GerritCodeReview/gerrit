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

package com.google.gerrit.auth.oauth;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.auth.oauth.OAuthServiceProvider;
import com.google.gerrit.extensions.auth.oauth.OAuthToken;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.server.auth.oauth.OAuthTokenRevokedListener;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;

/**
 * Revokes an account's OAuth token at the IdP (RFC 7009), evicts it from the {@code oauth_tokens}
 * cache, and fires {@link OAuthTokenRevokedListener}. Unlike a plain evict (local purge only),
 * revocation invalidates the token upstream. Always evicts and fires, even when the provider cannot
 * revoke upstream.
 */
@Singleton
public class OAuthTokenRevoker {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Outcome of revoking one account's token. */
  public enum Result {
    /** Revoked at the IdP and evicted locally. */
    REVOKED,
    /** Evicted locally only: the provider does not support revoke, or the IdP call failed. */
    EVICTED_ONLY,
    /** No cached token for the account; nothing to do. */
    NO_TOKEN,
  }

  private final OAuthTokenCache tokenCache;
  private final DynamicMap<OAuthServiceProvider> providers;
  private final PluginSetContext<OAuthTokenRevokedListener> revokedListeners;

  @Inject
  public OAuthTokenRevoker(
      OAuthTokenCache tokenCache,
      DynamicMap<OAuthServiceProvider> providers,
      PluginSetContext<OAuthTokenRevokedListener> revokedListeners) {
    this.tokenCache = tokenCache;
    this.providers = providers;
    this.revokedListeners = revokedListeners;
  }

  /**
   * Revokes the account's token at the IdP (if its provider supports revocation), evicts it
   * locally, and fires {@link OAuthTokenRevokedListener}.
   *
   * @return {@link Result#NO_TOKEN} if nothing was cached; otherwise {@link Result#REVOKED} or
   *     {@link Result#EVICTED_ONLY}.
   */
  public Result revoke(Account.Id accountId) {
    return revoke(accountId, /* notifySingle= */ true);
  }

  private Result revoke(Account.Id accountId, boolean notifySingle) {
    OAuthToken token;
    try {
      token = tokenCache.getEvenIfExpired(accountId);
    } catch (RuntimeException e) {
      // Entry present but undecryptable (wrong or rotated auth.tokenEncryptionKey, or a corrupt or
      // tampered value): the token cannot be loaded to revoke upstream, but a compromise response
      // must still purge it locally and fire the listener.
      logger.atWarning().withCause(e).log(
          "could not decrypt cached OAuth token for account %s; evicting locally anyway",
          accountId);
      return evictAndNotify(accountId, notifySingle, Result.EVICTED_ONLY);
    }
    if (token == null) {
      return Result.NO_TOKEN;
    }
    Result result = Result.EVICTED_ONLY;
    OAuthServiceProvider provider = resolveProvider(token);
    if (provider != null) {
      // supportsRevoke() and revoke() are both provider-supplied; a throw from either must not stop
      // the local eviction below.
      try {
        if (provider.supportsRevoke()) {
          provider.revoke(token);
          result = Result.REVOKED;
          logger.atInfo().log(
              "Revoked OAuth token at the IdP for account %s (provider %s)",
              accountId, token.getProviderId());
        } else {
          logger.atFine().log(
              "provider for account %s (%s) does not support revoke; evicting locally only",
              accountId, token.getProviderId());
        }
      } catch (IOException | RuntimeException e) {
        logger.atWarning().withCause(e).log(
            "IdP revocation failed for account %s (provider %s); evicting locally anyway",
            accountId, token.getProviderId());
      }
    } else {
      logger.atFine().log(
          "no provider resolved for account %s (%s); evicting locally only",
          accountId, token.getProviderId());
    }
    return evictAndNotify(accountId, notifySingle, result);
  }

  private Result evictAndNotify(Account.Id accountId, boolean notifySingle, Result result) {
    tokenCache.remove(accountId);
    if (notifySingle) {
      revokedListeners.runEach(l -> l.onTokenRevoked(accountId));
    }
    return result;
  }

  /**
   * Revokes and evicts every cached token (compromise-wide response), then purges the persistent
   * store to catch entries that were only on disk and thus not individually revocable.
   *
   * @return the number of accounts whose cached token was processed individually
   */
  public int revokeAll() {
    // Notify before and after the loop: before drops current entries, after catches any created
    // while the (possibly long) IdP revoke loop ran.
    revokedListeners.runEach(OAuthTokenRevokedListener::onAllTokensRevoked);
    int processed = 0;
    for (Account.Id id : tokenCache.accountsWithCachedToken()) {
      var unused = revoke(id, /* notifySingle= */ false);
      processed++;
    }
    // Disk-only entries are not enumerated above; purge them locally (they cannot be revoked
    // upstream because their token is not loaded).
    tokenCache.removeAll();
    revokedListeners.runEach(OAuthTokenRevokedListener::onAllTokensRevoked);
    logger.atInfo().log("Revoked and evicted %d cached OAuth token(s)", processed);
    return processed;
  }

  @Nullable
  private OAuthServiceProvider resolveProvider(OAuthToken token) {
    String providerId = token.getProviderId();
    if (providerId == null) {
      return null;
    }
    int colon = providerId.indexOf(':');
    if (colon <= 0 || colon == providerId.length() - 1) {
      return null;
    }
    try {
      return providers.get(providerId.substring(0, colon), providerId.substring(colon + 1));
    } catch (RuntimeException e) {
      return null;
    }
  }
}
