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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.Striped;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.auth.oauth.OAuthRevokedException;
import com.google.gerrit.extensions.auth.oauth.OAuthServiceProvider;
import com.google.gerrit.extensions.auth.oauth.OAuthToken;
import com.google.gerrit.extensions.auth.oauth.OAuthTokenEncrypter;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import org.eclipse.jgit.lib.Config;

/**
 * Renews an expired OAuth access token from its refresh token, on read.
 *
 * <p>{@link #refreshIfExpired} renews an expired token in place (RFC 6749 &sect;6); a revoked grant
 * surfaces as {@link OAuthRevokedException} after the cached token is dropped, other failures are
 * swallowed. A still-valid or absent token is detected from the cleartext {@code expiresAt} without
 * decrypting.
 *
 * <p>Constructed in the core web injector, where {@link DynamicMap}{@code <OAuthServiceProvider>}
 * is reachable, so no provider registry needs to move to sys. Renewal is serialized per account
 * with a non-blocking {@link Lock#tryLock()} and backs off for {@code
 * auth.oauthTokenRefreshInterval} after a failure.
 */
@Singleton
public class OAuthTokenRefresher {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final OAuthTokenCache tokenCache;
  private final DynamicMap<OAuthServiceProvider> providers;
  private final DynamicItem<OAuthTokenEncrypter> encrypter;
  // 16 stripes bound the lock count instead of one lock per account; an account id hashes to a
  // stripe, so two accounts may share one. Acquired with tryLock(): a busy stripe means skip this
  // refresh (single-flight), never block.
  private final Striped<Lock> refreshLocks = Striped.lock(16);
  private final AtomicBoolean warnedNoEncrypter = new AtomicBoolean();
  private final Cache<Account.Id, Boolean> recentlyFailed;

  @Inject
  public OAuthTokenRefresher(
      OAuthTokenCache tokenCache,
      DynamicMap<OAuthServiceProvider> providers,
      DynamicItem<OAuthTokenEncrypter> encrypter,
      @GerritServerConfig Config config) {
    this.tokenCache = tokenCache;
    this.providers = providers;
    this.encrypter = encrypter;
    long intervalMillis =
        ConfigUtil.getTimeUnit(
            config,
            "auth",
            null,
            "oauthTokenRefreshInterval",
            TimeUnit.MINUTES.toMillis(5),
            TimeUnit.MILLISECONDS);
    this.recentlyFailed =
        CacheBuilder.newBuilder().expireAfterWrite(intervalMillis, TimeUnit.MILLISECONDS).build();
  }

  /**
   * Renews the account's cached token in place if it has expired and its provider supports refresh;
   * otherwise does nothing.
   *
   * @throws OAuthRevokedException if the refresh token was rejected ({@code invalid_grant}); the
   *     cached token has been removed.
   */
  public void refreshIfExpired(Account.Id accountId) throws OAuthRevokedException {
    if (!tokenCache.hasExpiredToken(accountId)) {
      return;
    }
    if (recentlyFailed.getIfPresent(accountId) != null) {
      logger.atFine().log("backing off after a recent failed refresh for account %s", accountId);
      return;
    }
    Lock lock = refreshLocks.get(accountId);
    if (!lock.tryLock()) {
      return; // another thread is already refreshing this account
    }
    try {
      // Decrypt once, under the lock: earlier gates use only cleartext metadata.
      OAuthToken token = tokenCache.getEvenIfExpired(accountId);
      if (token == null || !token.isExpired()) {
        return; // refreshed or evicted while waiting
      }
      OAuthServiceProvider provider = resolveProvider(token);
      if (provider == null || !provider.supportsRefresh()) {
        logger.atFine().log(
            "expired token for account %s cannot be refreshed (provider '%s')",
            accountId, token.getProviderId());
        return;
      }
      warnIfStoringRefreshTokenUnencrypted();
      long oldExpiresAt = token.getExpiresAt();
      OAuthToken refreshed = provider.refresh(token);
      tokenCache.put(accountId, refreshed);
      logger.atInfo().log(
          "Refreshed OAuth access token for account %s (provider %s): expiresAt %d -> %d",
          accountId, token.getProviderId(), oldExpiresAt, refreshed.getExpiresAt());
    } catch (OAuthRevokedException e) {
      tokenCache.remove(accountId);
      logger.atInfo().log(
          "OAuth grant revoked for account %s (invalid_grant); dropped token", accountId);
      throw e;
    } catch (IOException e) {
      recentlyFailed.put(accountId, Boolean.TRUE);
      logger.atWarning().withCause(e).log(
          "OAuth access-token refresh failed for account %s", accountId);
    } catch (RuntimeException e) {
      recentlyFailed.put(accountId, Boolean.TRUE);
      logger.atWarning().withCause(e).log(
          "Unexpected error refreshing OAuth access token for account %s", accountId);
    } finally {
      lock.unlock();
    }
  }

  private void warnIfStoringRefreshTokenUnencrypted() {
    if (encrypter.get() == null && warnedNoEncrypter.compareAndSet(false, true)) {
      logger.atWarning().log(
          "OAuth token refresh is persisting long-lived refresh tokens in the oauth_tokens cache in"
              + " cleartext: no OAuthTokenEncrypter is bound. Restrict filesystem access to the"
              + " site's cache directory, or do not enable token refresh.");
    }
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
