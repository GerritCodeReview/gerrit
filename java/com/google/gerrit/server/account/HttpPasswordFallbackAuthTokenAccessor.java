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
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.server.account.externalids.DuplicateExternalIdKeyException;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdFactory;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.account.externalids.storage.notedb.ExternalIdNotes;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Repository;

public class HttpPasswordFallbackAuthTokenAccessor implements AuthTokenAccessor {
  static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static final String LEGACY_ID = "legacy";

  public interface Factory {
    HttpPasswordFallbackAuthTokenAccessor create(AuthTokenAccessor accessor);
  }

  private final AccountCache accountCache;
  private final AuthTokenAccessor accessor;
  private final ExternalIds externalIds;
  private final ExternalIdFactory externalIdFactory;
  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsers;
  private final ExternalIdNotes.FactoryNoReindex externalIdNotesFactory;
  private final Provider<MetaDataUpdate.Server> metaDataUpdateServerFactory;

  @AssistedInject
  HttpPasswordFallbackAuthTokenAccessor(
      AccountCache accountCache,
      @Assisted AuthTokenAccessor accessor,
      ExternalIds externalIds,
      ExternalIdFactory externalIdFactory,
      GitRepositoryManager repoManager,
      AllUsersName allUsers,
      ExternalIdNotes.FactoryNoReindex externalIdNotesFactory,
      Provider<MetaDataUpdate.Server> metaDataUpdateServerFactory) {
    this.accessor = accessor;
    this.accountCache = accountCache;
    this.externalIds = externalIds;
    this.externalIdFactory = externalIdFactory;
    this.repoManager = repoManager;
    this.allUsers = allUsers;
    this.externalIdNotesFactory = externalIdNotesFactory;
    this.metaDataUpdateServerFactory = metaDataUpdateServerFactory;
  }

  @Override
  public List<AuthToken> getTokens(Account.Id accountId)
      throws IOException, ConfigInvalidException {
    List<AuthToken> tokens = accessor.getTokens(accountId);
    if (tokens.isEmpty()) {
      tokens = fallBackToLegacyHttpPassword(accountId);
    }
    return tokens;
  }

  @Override
  public List<AuthToken> getValidTokens(Account.Id accountId)
      throws IOException, ConfigInvalidException {
    return ImmutableList.copyOf(getTokens(accountId).stream().filter(t -> !t.isExpired()).toList());
  }

  @Override
  public Optional<AuthToken> getToken(Account.Id accountId, String id)
      throws IOException, ConfigInvalidException {
    return getTokens(accountId).stream().filter(token -> token.id().equals(id)).findFirst();
  }

  @Override
  public AuthToken addToken(
      Account.Id accountId, String id, String hashedToken, Optional<Instant> expiration)
      throws IOException, ConfigInvalidException, InvalidAuthTokenException {
    AuthToken token = accessor.addToken(accountId, id, hashedToken, expiration);
    purgeHttpPassword(accountId);
    return token;
  }

  @Override
  public void addTokens(Account.Id accountId, Collection<AuthToken> tokens)
      throws IOException, ConfigInvalidException, InvalidAuthTokenException {
    accessor.addTokens(accountId, tokens);
    purgeHttpPassword(accountId);
  }

  @Override
  public AuthToken addPlainToken(
      Account.Id accountId, String id, String token, Optional<Instant> expiration)
      throws IOException, ConfigInvalidException, InvalidAuthTokenException {
    AuthToken authToken = accessor.addPlainToken(accountId, id, token, expiration);
    purgeHttpPassword(accountId);
    return authToken;
  }

  @Override
  public void deleteToken(Account.Id accountId, String id)
      throws IOException, ConfigInvalidException, InvalidAuthTokenException {
    accessor.deleteToken(accountId, id);
  }

  @Override
  public void deleteAllTokens(Account.Id accountId)
      throws IOException, ConfigInvalidException, InvalidAuthTokenException {
    accessor.deleteAllTokens(accountId);
  }

  @Override
  public void updateToken(Account.Id accountId, AuthToken token)
      throws IOException, ConfigInvalidException, InvalidAuthTokenException {
    accessor.updateToken(accountId, token);
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

  void purgeHttpPassword(Account.Id accountId) {
    try {
      ImmutableSet<ExternalId> accountExtIds = externalIds.byAccount(accountId);
      Optional<ExternalId> usernameExtId =
          accountExtIds.stream()
              .filter(e -> e.key().scheme().equals(SCHEME_USERNAME) && e.password() != null)
              .findFirst();
      if (usernameExtId.isPresent()) {
        ExternalId oldExtId = usernameExtId.get();
        ExternalId updatedExtId =
            externalIdFactory.createWithEmail(
                oldExtId.key(), oldExtId.accountId(), oldExtId.email());
        try (Repository repo = repoManager.openRepository(allUsers)) {
          ExternalIdNotes extIdNotes = externalIdNotesFactory.load(repo);
          extIdNotes.replace(oldExtId, updatedExtId);
          try (MetaDataUpdate metaDataUpdate = metaDataUpdateServerFactory.get().create(allUsers)) {
            metaDataUpdate.setMessage("Delete old HTTP password on token creation");
            extIdNotes.commit(metaDataUpdate);
          }
        }
      }
    } catch (IOException | ConfigInvalidException | DuplicateExternalIdKeyException e) {
      logger.atSevere().withCause(e).log("Unable to purge HTTP password.");
    }
  }
}
