// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.server.account;

import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_USERNAME;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.Account;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;

public class HttpPasswordFallbackAuthTokenAccessor implements AuthTokenAccessor {

  public static final String LEGACY_ID = "legacy";

  public interface Factory {
    HttpPasswordFallbackAuthTokenAccessor create(AuthTokenAccessor accessor);
  }

  private final AccountCache accountCache;
  private final AuthTokenAccessor accessor;

  @AssistedInject
  HttpPasswordFallbackAuthTokenAccessor(
      AccountCache accountCache, @Assisted AuthTokenAccessor accessor) {
    this.accessor = accessor;
    this.accountCache = accountCache;
  }

  @Override
  public List<AuthToken> getTokens(Account.Id accountId) {
    List<AuthToken> tokens = accessor.getTokens(accountId);
    if (tokens.isEmpty()) {
      tokens = fallBackToLegacyHttpPassword(accountId);
    }
    return tokens;
  }

  @Override
  public Optional<AuthToken> getToken(Account.Id accountId, String id) {
    return getTokens(accountId).stream().filter(token -> token.id().equals(id)).findFirst();
  }

  @Override
  public AuthToken addPlainToken(Account.Id accountId, String id, String token)
      throws IOException, ConfigInvalidException, InvalidAuthTokenException {
    return accessor.addPlainToken(accountId, id, token);
  }

  @Override
  public void addTokens(Account.Id accountId, Collection<AuthToken> tokens)
      throws IOException, ConfigInvalidException, AuthTokenConflictException {
    accessor.addTokens(accountId, tokens);
  }

  @Override
  public void deleteToken(Account.Id accountId, String id)
      throws IOException, ConfigInvalidException {
    accessor.deleteToken(accountId, id);
  }

  @Override
  public void deleteAllTokens(Account.Id accountId) throws IOException, ConfigInvalidException {
    accessor.deleteAllTokens(accountId);
  }

  ImmutableList<AuthToken> fallBackToLegacyHttpPassword(Account.Id accountId) {
    AccountState accountState = accountCache.getEvenIfMissing(accountId);
    Optional<ExternalId> optUser =
        accountState.externalIds().stream()
            .filter(e -> e.key().scheme().equals(SCHEME_USERNAME))
            .findFirst();
    if (optUser.isEmpty()) {
      return ImmutableList.of();
    }
    ExternalId user = optUser.get();
    String password = user.password();
    if (password != null) {
      try {
        return ImmutableList.of(AuthToken.create(LEGACY_ID, password));
      } catch (InvalidAuthTokenException e1) {
        // Can be ignored because the token ID is hardcoded.
      }
    }
    return ImmutableList.of();
  }
}
