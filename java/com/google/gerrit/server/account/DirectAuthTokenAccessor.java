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

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.entities.Account;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;

/** Read/write authentication tokens by user ID. */
@Singleton
public class DirectAuthTokenAccessor implements AuthTokenAccessor {
  public static final String LEGACY_ID = "legacy";

  private final AllUsersName allUsersName;
  private final VersionedAuthTokens.Factory authTokenFactory;
  private final Provider<MetaDataUpdate.User> metaDataUpdateFactory;
  private final IdentifiedUser.GenericFactory userFactory;

  @Inject
  DirectAuthTokenAccessor(
      AllUsersName allUsersName,
      VersionedAuthTokens.Factory authTokenFactory,
      Provider<MetaDataUpdate.User> metaDataUpdateFactory,
      IdentifiedUser.GenericFactory userFactory) {
    this.allUsersName = allUsersName;
    this.authTokenFactory = authTokenFactory;
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.userFactory = userFactory;
  }

  @Override
  public ImmutableList<AuthToken> getValidTokens(Account.Id accountId)
      throws IOException, ConfigInvalidException {
    return ImmutableList.copyOf(getTokens(accountId).stream().filter(t -> !t.isExpired()).toList());
  }

  @Override
  public ImmutableList<AuthToken> getTokens(Account.Id accountId)
      throws IOException, ConfigInvalidException {
    return readFromNoteDb(accountId).getTokens();
  }

  @Override
  public Optional<AuthToken> getToken(Account.Id accountId, String id)
      throws IOException, ConfigInvalidException {
    return Optional.ofNullable(readFromNoteDb(accountId).getToken(id));
  }

  @Override
  @CanIgnoreReturnValue
  public synchronized AuthToken addPlainToken(
      Account.Id accountId, String id, String token, Optional<Instant> expiration)
      throws IOException, ConfigInvalidException, InvalidAuthTokenException {
    String hashedToken = HashedPassword.fromPassword(token).encode();
    return addToken(accountId, id, hashedToken, expiration);
  }

  @Override
  public synchronized void addTokens(Account.Id accountId, Collection<AuthToken> tokens)
      throws IOException, ConfigInvalidException, InvalidAuthTokenException {
    VersionedAuthTokens authTokens = readFromNoteDb(accountId);
    for (AuthToken token : tokens) {
      authTokens.addToken(token);
    }
    commit(accountId, authTokens);
  }

  @Override
  @CanIgnoreReturnValue
  public synchronized AuthToken addToken(
      Account.Id accountId, String id, String hashedToken, Optional<Instant> expiration)
      throws IOException, ConfigInvalidException, InvalidAuthTokenException {
    VersionedAuthTokens authTokens = readFromNoteDb(accountId);
    AuthToken token = authTokens.addToken(id, hashedToken, expiration);
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
    VersionedAuthTokens authTokens = readFromNoteDb(accountId);
    if (authTokens.getTokens().isEmpty()) {
      return;
    }
    for (AuthToken token : getTokens(accountId)) {
      @SuppressWarnings("unused")
      var unused = authTokens.deleteToken(token.id());
    }
    commit(accountId, authTokens);
  }

  @Override
  public void updateToken(Account.Id accountId, AuthToken token)
      throws IOException, ConfigInvalidException, InvalidAuthTokenException {
    VersionedAuthTokens authTokens = readFromNoteDb(accountId);
    @SuppressWarnings("unused")
    var unused = authTokens.deleteToken(token.id());
    authTokens.addToken(token);
    commit(accountId, authTokens);
  }

  private VersionedAuthTokens readFromNoteDb(Account.Id accountId)
      throws IOException, ConfigInvalidException {
    return authTokenFactory.create(accountId).load();
  }

  private void commit(Account.Id accountId, VersionedAuthTokens authTokens) throws IOException {
    try (MetaDataUpdate md =
        metaDataUpdateFactory.get().create(allUsersName, userFactory.create(accountId))) {
      authTokens.commit(md, false);
    }
  }
}
