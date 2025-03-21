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

package com.google.gerrit.pgm.init;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Strings;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.pgm.init.api.AllUsersNameOnInitProvider;
import com.google.gerrit.pgm.init.api.InitFlags;
import com.google.gerrit.pgm.init.api.VersionedMetaDataOnInit;
import com.google.gerrit.server.account.AuthToken;
import com.google.gerrit.server.account.InvalidAuthTokenException;
import com.google.gerrit.server.account.VersionedAuthTokens;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;

public class VersionedAuthTokensOnInit extends VersionedMetaDataOnInit {
  public interface Factory {
    VersionedAuthTokensOnInit create(Account.Id accountId);
  }

  private Map<String, AuthToken> tokens;

  @Inject
  public VersionedAuthTokensOnInit(
      AllUsersNameOnInitProvider allUsers,
      SitePaths site,
      InitFlags flags,
      @Assisted Account.Id accountId) {
    super(flags, site, allUsers.get(), RefNames.refsUsers(accountId));
  }

  @Override
  public VersionedAuthTokensOnInit load() throws IOException, ConfigInvalidException {
    super.load();
    return this;
  }

  @Override
  protected void onLoad() throws IOException, ConfigInvalidException {
    tokens = VersionedAuthTokens.parse(readUTF8(VersionedAuthTokens.FILE_NAME));
  }

  @CanIgnoreReturnValue
  public AuthToken addToken(String id, String t, Optional<Instant> expirationDate)
      throws InvalidAuthTokenException {
    checkState(tokens != null, "Tokens not loaded yet");
    AuthToken token = AuthToken.createWithPlainToken(id, t, expirationDate);
    tokens.put(id, token);
    return token;
  }

  public void updateToken(AuthToken token) {
    checkState(tokens != null, "Tokens not loaded yet");
    tokens.remove(token.id());
    tokens.put(token.id(), token);
  }

  @Nullable
  public AuthToken getToken(String id) {
    checkState(tokens != null, "Tokens not loaded yet");
    return tokens.get(id);
  }

  public List<AuthToken> getTokens() {
    checkState(tokens != null, "Tokens not loaded yet");
    return List.copyOf(tokens.values());
  }

  @Override
  protected boolean onSave(CommitBuilder commit) throws IOException {
    if (Strings.isNullOrEmpty(commit.getMessage())) {
      commit.setMessage("Updated tokens\n");
    }

    Config tokenConfig = new Config();
    for (AuthToken token : tokens.values()) {
      tokenConfig.setString("token", token.id(), "hash", token.hashedToken());
      if (token.expirationDate().isPresent()) {
        tokenConfig.setString(
            "token", token.id(), "expiration", token.expirationDate().get().toString());
      }
    }

    saveUTF8(VersionedAuthTokens.FILE_NAME, tokenConfig.toText());
    return true;
  }
}
