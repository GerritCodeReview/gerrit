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

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.entities.Account;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.inject.Provider;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;

public abstract class AbstractAuthTokenAccessor implements AuthTokenAccessor {
  private final AllUsersName allUsersName;
  private final VersionedAuthTokens.Factory authTokenFactory;
  private final Provider<MetaDataUpdate.User> metaDataUpdateFactory;
  private final IdentifiedUser.GenericFactory userFactory;

  AbstractAuthTokenAccessor(
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
  @CanIgnoreReturnValue
  public synchronized AuthToken addPlainToken(Account.Id accountId, String id, String token)
      throws IOException, ConfigInvalidException, AuthTokenConflictException {
    String hashedToken = HashedPassword.fromPassword(token).encode();
    return addToken(accountId, id, hashedToken);
  }

  @CanIgnoreReturnValue
  protected synchronized AuthToken addToken(Account.Id accountId, String id, String hashedToken)
      throws IOException, ConfigInvalidException, AuthTokenConflictException {
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
      authTokens.commit(md);
    }
    onCommit(accountId);
  }

  abstract void onCommit(Account.Id accountId);
}
