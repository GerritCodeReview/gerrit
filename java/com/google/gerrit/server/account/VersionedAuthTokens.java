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
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.meta.VersionedMetaData;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;

/**
 * 'tokens.config' file in the refs/users/CD/ABCD branches of the All-Users repository.
 *
 * <p>The `tokens.config' file stores the authentication tokens of the user. The file uses the git
 * config format, where each token is a subsection.
 */
public class VersionedAuthTokens extends VersionedMetaData {

  public interface Factory {
    VersionedAuthTokens create(Account.Id accountId);
  }

  public static final String FILE_NAME = "tokens.config";

  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsersName;

  private final Account.Id accountId;
  private final String ref;
  private Map<String, AuthToken> tokens;

  @Inject
  public VersionedAuthTokens(
      GitRepositoryManager repoManager, AllUsersName allUsersName, @Assisted Account.Id accountId) {
    this.repoManager = repoManager;
    this.allUsersName = allUsersName;

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
    for (AuthToken token : tokens.values()) {
      tokenConfig.setString("token", token.id(), "hash", token.hashedToken());
    }

    saveUTF8(FILE_NAME, tokenConfig.toText());
    return true;
  }

  public static Map<String, AuthToken> parse(String s) throws ConfigInvalidException {
    Config tokenConfig = new Config();
    tokenConfig.fromText(s);
    Map<String, AuthToken> tokens = new HashMap<>(tokenConfig.getSubsections("token").size());
    for (String id : tokenConfig.getSubsections("token")) {
      try {
        tokens.put(id, AuthToken.create(id, tokenConfig.getString("token", id, "hash")));
      } catch (InvalidAuthTokenException e) {
        // Tokens were validated on creation.
      }
    }
    return tokens;
  }

  /** Returns all authorization tokens. */
  ImmutableList<AuthToken> getTokens() {
    checkLoaded();
    return ImmutableList.copyOf(tokens.values());
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
    return tokens.get(id);
  }

  /**
   * Adds a new token.
   *
   * @param id the id of the token
   * @param hashedToken the hashed token to be added
   * @return the new Token
   * @throws InvalidAuthTokenException if the token or its ID is invalid
   */
  AuthToken addToken(String id, String hashedToken) throws InvalidAuthTokenException {
    checkLoaded();

    AuthToken token = AuthToken.create(id, hashedToken);
    return addToken(token);
  }

  /**
   * Adds a new token.
   *
   * @param token the token to be added
   * @return the new Token
   * @throws AuthTokenConflictException if a token with the given id already exists
   */
  @CanIgnoreReturnValue
  AuthToken addToken(AuthToken token) throws AuthTokenConflictException {
    checkLoaded();

    if (tokens.containsKey(token.id())) {
      throw new AuthTokenConflictException(token.id(), accountId);
    }

    tokens.put(token.id(), token);
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
    return tokens.remove(id) != null;
  }

  private void checkLoaded() {
    checkState(tokens != null, "Tokens not loaded yet");
  }
}
