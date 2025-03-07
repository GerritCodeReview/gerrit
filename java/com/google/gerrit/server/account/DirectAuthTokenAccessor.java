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
import com.google.common.flogger.FluentLogger;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.entities.Account;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;

/** Read/write authentication tokens by user ID. */
@Singleton
public class DirectAuthTokenAccessor implements AuthTokenAccessor {
  public static final String LEGACY_ID = "legacy";
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final AccountCache accountCache;
  private final AllUsersName allUsersName;
  private final VersionedAuthTokens.Factory authTokenFactory;
  private final Provider<MetaDataUpdate.User> metaDataUpdateFactory;
  private final IdentifiedUser.GenericFactory userFactory;

  @Inject
  DirectAuthTokenAccessor(
      AccountCache accountCache,
      AllUsersName allUsersName,
      VersionedAuthTokens.Factory authTokenFactory,
      Provider<MetaDataUpdate.User> metaDataUpdateFactory,
      IdentifiedUser.GenericFactory userFactory) {
    this.accountCache = accountCache;
    this.allUsersName = allUsersName;
    this.authTokenFactory = authTokenFactory;
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.userFactory = userFactory;
  }

  @Override
  public List<AuthToken> getTokens(Account.Id accountId) {
    List<AuthToken> tokens = List.of();
    try {
      tokens = readFromNoteDb(accountId).getTokens();
    } catch (IOException | ConfigInvalidException e) {
      logger.atSevere().withCause(e).log("Error reading auth tokens for account %s", accountId);
      throw new StorageException(e);
    }
    if (tokens.isEmpty()) {
      Optional<AuthToken> legacyHttpPassword = getLegacyHttpPassword(accountId);
      tokens =
          legacyHttpPassword.isPresent()
              ? ImmutableList.of(legacyHttpPassword.get())
              : ImmutableList.of();
    }
    return tokens;
  }

  @Override
  public Optional<AuthToken> getToken(Account.Id accountId, String id) {
    try {
      return Optional.ofNullable(readFromNoteDb(accountId).getToken(id));
    } catch (IOException | ConfigInvalidException e) {
      logger.atSevere().withCause(e).log("Error reading auth tokens for account %s", accountId);
      throw new StorageException(e);
    }
  }

  @Override
  @CanIgnoreReturnValue
  public synchronized AuthToken addPlainToken(Account.Id accountId, String id, String token)
      throws IOException, ConfigInvalidException, InvalidAuthTokenException {
    String hashedToken = HashedPassword.fromPassword(token).encode();
    return addToken(accountId, id, hashedToken);
  }

  @CanIgnoreReturnValue
  protected synchronized AuthToken addToken(Account.Id accountId, String id, String hashedToken)
      throws IOException, ConfigInvalidException, InvalidAuthTokenException {
    VersionedAuthTokens authTokens = readFromNoteDb(accountId);
    AuthToken token = authTokens.addToken(id, hashedToken);
    commit(accountId, authTokens);
    return token;
  }

  @Override
  public synchronized void deleteToken(Account.Id accountId, String id)
      throws IOException, ConfigInvalidException {
    VersionedAuthTokens authTokens = readFromNoteDb(accountId);
    if (authTokens.deleteToken(id)) {
      commit(accountId, authTokens);
    }
  }

  @Override
  public void deleteAllTokens(Account.Id accountId) throws IOException, ConfigInvalidException {
    for (AuthToken token : getTokens(accountId)) {
      deleteToken(accountId, token.id());
    }
  }

  protected VersionedAuthTokens readFromNoteDb(Account.Id accountId)
      throws IOException, ConfigInvalidException {
    return authTokenFactory.create(accountId).load();
  }

  protected void commit(Account.Id accountId, VersionedAuthTokens authTokens) throws IOException {
    try (MetaDataUpdate md =
        metaDataUpdateFactory.get().create(allUsersName, userFactory.create(accountId))) {
      authTokens.commit(md, false);
    }
  }

  @Deprecated
  Optional<AuthToken> getLegacyHttpPassword(Account.Id accountId) {
    AccountState accountState = accountCache.getEvenIfMissing(accountId);
    Optional<ExternalId> optUser =
        accountState.externalIds().stream()
            .filter(e -> e.key().scheme().equals(SCHEME_USERNAME))
            .findFirst();
    if (optUser.isEmpty()) {
      return Optional.empty();
    }
    ExternalId user = optUser.get();
    String password = user.password();
    if (password != null) {
      try {
        return Optional.of(AuthToken.create(LEGACY_ID, password));
      } catch (InvalidAuthTokenException e1) {
        // Can be ignored because the token ID is hardcoded.
      }
    }
    return Optional.empty();
  }
}
