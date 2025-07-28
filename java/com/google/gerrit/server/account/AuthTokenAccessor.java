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
import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;

public interface AuthTokenAccessor {
  public List<AuthToken> getTokens(Account.Id accountId);

  public Optional<AuthToken> getToken(Account.Id accountId, String id);

  public AuthToken addPlainToken(
      Account.Id accountId, String id, String token, Optional<Instant> expiration)
      throws IOException, ConfigInvalidException, InvalidAuthTokenException;

  public AuthToken addToken(
      Account.Id accountId, String id, String hashedToken, Optional<Instant> expiration)
      throws IOException, ConfigInvalidException, InvalidAuthTokenException;

  public void deleteToken(Account.Id accountId, String id)
      throws IOException, ConfigInvalidException, InvalidAuthTokenException;

  public List<AuthToken> getValidTokens(Account.Id accountId);

  public void deleteAllTokens(Account.Id accountId)
      throws IOException, ConfigInvalidException, InvalidAuthTokenException;

  public void addTokens(Account.Id accountId, Collection<AuthToken> tokens)
      throws IOException, ConfigInvalidException, InvalidAuthTokenException;

  public void updateToken(Account.Id accountId, AuthToken token)
      throws IOException, ConfigInvalidException, InvalidAuthTokenException;
}
