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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Strings;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.meta.VersionedMetaData;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;

/**
 * 'tokens' file in the refs/users/CD/ABCD branches of the All-Users repository.
 *
 * <p>The `tokens' files stores the authentication tokens of the user. The file uses the git config
 * format, where each token is a subsection.
 */
public class VersionedAuthTokens extends VersionedMetaData {

  public interface Factory {
    VersionedAuthTokens create(Account.Id accountId);
  }

  public static final String FILE_NAME = "tokens";

  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsersName;
  private final Optional<Duration> maxAuthTokenLifetime;

  private final Account.Id accountId;
  private final String ref;
  private List<AuthToken> tokens;

  @Inject
  public VersionedAuthTokens(
      GitRepositoryManager repoManager,
      AllUsersName allUsersName,
      AuthConfig authConfig,
      @Assisted Account.Id accountId) {
    this.repoManager = repoManager;
    this.allUsersName = allUsersName;
    this.maxAuthTokenLifetime = authConfig.getMaxAuthTokenLifetime();

    this.accountId = accountId;
    this.ref = RefNames.refsUsers(accountId);
  }

  @Override
  protected String getRefName() {
    return ref;
  }

  public VersionedAuthTokens load() throws IOException, ConfigInvalidException {
    try (Repository git = repoManager.openRepository(allUsersName)) {
      load(allUsersName, git);
    }
    return this;
  }

  @Override
  protected void onLoad() throws IOException, ConfigInvalidException {
    tokens = parse(readUTF8(FILE_NAME));
  }

  @Override
  protected boolean onSave(CommitBuilder commit) throws IOException {
    if (Strings.isNullOrEmpty(commit.getMessage())) {
      commit.setMessage("Updated authentication tokens\n");
    }

    Config tokenConfig = new Config();
    for (AuthToken token : tokens) {
      tokenConfig.setString("token", token.id(), "hash", token.hashedToken());
      if (token.expirationDate().isPresent()) {
        tokenConfig.setLong(
            "token", token.id(), "expiration", token.expirationDate().get().toEpochMilli());
      }
    }

    saveUTF8(FILE_NAME, tokenConfig.toText());
    return true;
  }

  public static List<AuthToken> parse(String s) throws ConfigInvalidException {
    List<AuthToken> tokens = new ArrayList<>();
    Config tokenConfig = new Config();
    tokenConfig.fromText(s);
    for (String id : tokenConfig.getSubsections("token")) {
      Long expiration = tokenConfig.getLong("token", id, "expiration");
      Optional<Instant> expirationInstant =
          expiration != null ? Optional.of(Instant.ofEpochMilli(expiration)) : Optional.empty();
      tokens.add(
          AuthToken.create(id, tokenConfig.getString("token", id, "hash"), expirationInstant));
    }
    return tokens;
  }

  /** Returns all authentication tokens. */
  List<AuthToken> getTokens() {
    checkLoaded();
    return tokens;
  }

  /**
   * Returns the token with the given id.
   *
   * @param id id / name of the token
   * @return the token, <code>null</code> if there is no token with this id
   */
  @Nullable
  AuthToken getToken(String id) {
    checkLoaded();
    return tokens.stream().filter(t -> t.id().equals(id)).findFirst().orElse(null);
  }

  /**
   * Adds a new token.
   *
   * @param id the id of the token
   * @param hashedToken the hashed token to be added
   * @param expiration the expiration instant of the token
   * @return the new Token
   * @throws InvalidAuthTokenException if the token is invalid, e.g. if the ID already exists or the
   *     lifetime does not comply with the server's configuration.
   */
  AuthToken addToken(String id, String hashedToken, Optional<Instant> expiration)
      throws InvalidAuthTokenException {
    checkLoaded();

    AuthToken token = AuthToken.create(id, hashedToken, expiration);
    return addToken(token);
  }

  /**
   * Adds a new token.
   *
   * @param token the token to be added
   * @return the new Token
   * @throws InvalidAuthTokenException if the token is invalid, e.g. if the ID already exists or the
   *     lifetime does not comply with the server's configuration.
   */
  @CanIgnoreReturnValue
  AuthToken addToken(AuthToken token) throws InvalidAuthTokenException {
    checkLoaded();

    if (tokens.stream().anyMatch(t -> t.id().equals(token.id()))) {
      throw new AuthTokenConflictException(token.id(), accountId);
    }

    if (maxAuthTokenLifetime.isPresent()) {
      if (token.expirationDate().isEmpty()) {
        throw new InvalidAuthTokenException("Tokens with unlimited lifetime are not permitted.");
      } else if (token
          .expirationDate()
          .get()
          .isAfter(Instant.now().plus(maxAuthTokenLifetime.get()))) {
        throw new InvalidAuthTokenException(
            String.format(
                "Lifetime of token exceeds maximum allowed lifetime of %s days %s hours %s"
                    + " minutes.",
                maxAuthTokenLifetime.get().toDays(),
                maxAuthTokenLifetime.get().toHoursPart(),
                maxAuthTokenLifetime.get().toMinutesPart()));
      }
    }

    tokens.add(token);
    return token;
  }

  /**
   * Deletes the token with the given id.
   *
   * @param id the id
   * @return <code>true</code> if a token with this id was found and deleted, <code>false
   *     </code> if no token with the given id exists
   */
  boolean deleteToken(String id) {
    checkLoaded();
    return tokens.removeIf(t -> t.id().equals(id));
  }

  private void checkLoaded() {
    checkState(tokens != null, "Tokens not loaded yet");
  }
}
