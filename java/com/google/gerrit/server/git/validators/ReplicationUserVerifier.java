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

import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Objects;
import org.eclipse.jgit.lib.Config;

/**
 * A verifier for anything relating to the replication user.
 *
 * <p>The replication user is only used in master-slave Gerrit environments. Whenever replication
 * events are sent from the master to a slave, the identity of that user is used.
 */
@Singleton
public class ReplicationUserVerifier {
  private final Config config;

  @Inject
  public ReplicationUserVerifier(@GerritServerConfig Config config) {
    this.config = config;
  }

  /**
   * Indicates whether the passed user is the configured replication user.
   *
   * @param user the user to test
   * @return {@code true} if the user is the replication user, {@code false} if not or if the
   *     replication user isn't configured
   */
  public boolean isReplicationUser(IdentifiedUser user) {
    // Use a string to prevent the use of metric prefixes (such as 'k') in IDs.
    String replicationUserId = config.getString("receive", null, "replicationUser");
    String userId = String.valueOf(user.getAccountId().get());
    return Objects.equals(userId, replicationUserId);
  }
}
