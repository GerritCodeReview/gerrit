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
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.git.meta.VersionedMetaData;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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

  /** Read/write authentication tokens by user ID. */
  @Singleton
  public static class Accessor {
    private final GitRepositoryManager repoManager;
    private final AllUsersName allUsersName;
    private final VersionedAuthTokens.Factory authTokenFactory;
    private final Provider<MetaDataUpdate.User> metaDataUpdateFactory;
    private final IdentifiedUser.GenericFactory userFactory;

    @Inject
    Accessor(
        GitRepositoryManager repoManager,
        AllUsersName allUsersName,
        VersionedAuthTokens.Factory authTokenFactory,
        Provider<MetaDataUpdate.User> metaDataUpdateFactory,
        IdentifiedUser.GenericFactory userFactory) {
      this.repoManager = repoManager;
      this.allUsersName = allUsersName;
      this.authTokenFactory = authTokenFactory;
      this.metaDataUpdateFactory = metaDataUpdateFactory;
      this.userFactory = userFactory;
    }

    public List<AuthToken> getTokens(Account.Id accountId)
        throws IOException, ConfigInvalidException {
      return read(accountId).getTokens();
    }

    public AuthToken getToken(Account.Id accountId, String id)
        throws IOException, ConfigInvalidException {
      return read(accountId).getToken(id);
    }

    @CanIgnoreReturnValue
    public synchronized AuthToken addPlainToken(Account.Id accountId, String id, String token)
        throws IOException, ConfigInvalidException, AuthTokenConflictException {
      String hashedToken = HashedPassword.fromPassword(token).encode();
      return addToken(accountId, id, hashedToken);
    }

    @CanIgnoreReturnValue
    private synchronized AuthToken addToken(Account.Id accountId, String id, String hashedToken)
        throws IOException, ConfigInvalidException, AuthTokenConflictException {
      VersionedAuthTokens authokens = read(accountId);
      AuthToken token = authokens.addToken(id, hashedToken);
      commit(authokens);
      return token;
    }

    public synchronized void addTokens(Account.Id accountId, Collection<AuthToken> tokens)
        throws IOException, ConfigInvalidException, AuthTokenConflictException {
      VersionedAuthTokens authorizedTokens = read(accountId);
      for (AuthToken token : tokens) {
        authorizedTokens.addToken(token);
      }
      commit(authorizedTokens);
    }

    public synchronized void deleteToken(Account.Id accountId, String id)
        throws IOException, ConfigInvalidException {
      VersionedAuthTokens authTokens = read(accountId);
      if (authTokens.deleteToken(id)) {
        commit(authTokens);
      }
    }

    private VersionedAuthTokens read(Account.Id accountId)
        throws IOException, ConfigInvalidException {
      try (Repository git = repoManager.openRepository(allUsersName)) {
        VersionedAuthTokens authTokens = authTokenFactory.create(accountId);
        authTokens.load(allUsersName, git);
        return authTokens;
      }
    }

    private void commit(VersionedAuthTokens authTokens) throws IOException {
      try (MetaDataUpdate md =
          metaDataUpdateFactory
              .get()
              .create(allUsersName, userFactory.create(authTokens.accountId))) {
        authTokens.commit(md);
      }
    }
  }

  public interface Factory {
    VersionedAuthTokens create(Account.Id accountId);
  }

  public static final String FILE_NAME = "tokens";

  private final Account.Id accountId;
  private final String ref;
  private List<AuthToken> tokens;

  @Inject
  public VersionedAuthTokens(@Assisted Account.Id accountId) {
    this.accountId = accountId;
    this.ref = RefNames.refsUsers(accountId);
  }

  @Override
  protected String getRefName() {
    return ref;
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
    }

    saveUTF8(FILE_NAME, tokenConfig.toText());
    return true;
  }

  public static List<AuthToken> parse(String s) throws ConfigInvalidException {
    List<AuthToken> tokens = new ArrayList<>();
    Config tokenConfig = new Config();
    tokenConfig.fromText(s);
    for (String id : tokenConfig.getSubsections("token")) {
      tokens.add(AuthToken.create(id, tokenConfig.getString("token", id, "hash")));
    }
    return tokens;
  }

  /** Returns all authorization tokens. */
  private List<AuthToken> getTokens() {
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
  private AuthToken getToken(String id) {
    checkLoaded();
    return tokens.stream().filter(t -> t.id().equals(id)).findFirst().orElse(null);
  }

  /**
   * Adds a new token.
   *
   * @param id the id of the token
   * @param hashedToken the hashed token to be added
   * @return the new Token
   * @throws AuthTokenConflictException if a token with the given id already exists
   */
  private AuthToken addToken(String id, String hashedToken) throws AuthTokenConflictException {
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
  private AuthToken addToken(AuthToken token) throws AuthTokenConflictException {
    checkLoaded();

    if (tokens.stream().anyMatch(t -> t.id().equals(token.id()))) {
      throw new AuthTokenConflictException(token.id(), accountId);
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
  private boolean deleteToken(String id) {
    checkLoaded();
    return tokens.removeIf(t -> t.id().equals(id));
  }

  private void checkLoaded() {
    checkState(tokens != null, "Tokens not loaded yet");
  }
}
