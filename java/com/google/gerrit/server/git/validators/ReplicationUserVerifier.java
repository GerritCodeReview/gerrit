// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.server.git.validators;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Objects;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ReplicationUserVerifier {
  private static final Logger log = LoggerFactory.getLogger(ReplicationUserVerifier.class);

  private final Config config;
  private final AccountResolver accountResolver;

  @Inject
  public ReplicationUserVerifier(
      @GerritServerConfig Config config, AccountResolver accountResolver) {
    this.config = config;
    this.accountResolver = accountResolver;
  }

  // TODO(aliceks): Look up the replication user in a way which works without indices.
  public boolean isReplicationUser(IdentifiedUser user) {
    String replicationUserIdentifier = config.getString("receive", null, "replicationUser");
    Account account;
    try {
      account = accountResolver.find(replicationUserIdentifier);
    } catch (OrmException | IOException | ConfigInvalidException e) {
      log.error("Could not retrieve account of replication user", e);
      return false;
    }
    return Objects.equals(account, user.getAccount());
  }
}
