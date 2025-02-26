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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.server.IdentifiedUser;
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
public class DirectAuthTokenAccessor extends AbstractAuthTokenAccessor {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Inject
  DirectAuthTokenAccessor(
      AllUsersName allUsersName,
      VersionedAuthTokens.Factory authTokenFactory,
      Provider<MetaDataUpdate.User> metaDataUpdateFactory,
      IdentifiedUser.GenericFactory userFactory) {
    super(allUsersName, authTokenFactory, metaDataUpdateFactory, userFactory);
  }

  @Override
  public List<AuthToken> getTokens(Account.Id accountId) {
    try {
      return readFromNoteDb(accountId).getTokens();
    } catch (IOException | ConfigInvalidException e) {
      logger.atSevere().withCause(e).log("Error reading auth tokens for account %s", accountId);
      throw new StorageException(e);
    }
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
  void onCommit(Account.Id accountId) {}
}
