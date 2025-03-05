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

import com.google.gerrit.entities.Account;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;

/** Read/write authentication tokens by user ID using a cache for faster access. */
public class CachingAuthTokenAccessor implements AuthTokenAccessor {

  public interface Factory {
    CachingAuthTokenAccessor create(AuthTokenAccessor accessor);
  }

  private final AuthTokenCache authTokenCache;
  private final AuthTokenAccessor accessor;

  @AssistedInject
  CachingAuthTokenAccessor(
      AuthTokenCache authTokenCache, @Assisted AuthTokenAccessor directAuthTokenAccessor) {
    this.authTokenCache = authTokenCache;
    this.accessor = directAuthTokenAccessor;
  }

  @Override
  public List<AuthToken> getTokens(Account.Id accountId) {
    return authTokenCache.get(accountId);
  }

  @Override
  public Optional<AuthToken> getToken(Account.Id accountId, String id) {
    return getTokens(accountId).stream().filter(token -> token.id().equals(id)).findFirst();
  }

  @Override
  public synchronized void addTokens(Account.Id accountId, Collection<AuthToken> tokens)
      throws IOException, ConfigInvalidException, AuthTokenConflictException {
    accessor.addTokens(accountId, tokens);
    authTokenCache.evict(accountId);
  }

  @Override
  public AuthToken addPlainToken(Account.Id accountId, String id, String token)
      throws IOException, ConfigInvalidException, InvalidAuthTokenException {
    AuthToken authToken = accessor.addPlainToken(accountId, id, token);
    authTokenCache.evict(accountId);
    return authToken;
  }

  @Override
  public void deleteToken(Account.Id accountId, String id)
      throws IOException, ConfigInvalidException {
    accessor.deleteToken(accountId, id);
    authTokenCache.evict(accountId);
  }

  @Override
  public void deleteAllTokens(Account.Id accountId) throws IOException, ConfigInvalidException {
    accessor.deleteAllTokens(accountId);
    authTokenCache.evict(accountId);
  }
}
