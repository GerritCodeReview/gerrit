// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.sshd.commands;

import com.google.gerrit.auth.oauth.OAuthTokenCache;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;
import org.kohsuke.args4j.Option;

/**
 * Evicts cached OAuth token(s), forcing re-authentication. Use after a suspected compromise (a
 * stolen encryption key, or an impersonated account).
 *
 * <p><b>Local purge only:</b> this removes Gerrit's cached copy; it does not revoke the token at
 * the identity provider (an already-exfiltrated token stays valid there until it expires) and does
 * not end the account's web session. A full response also rotates {@code auth.tokenEncryptionKey},
 * revokes at the IdP, and flushes the {@code web_sessions} cache.
 */
@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
@CommandMetaData(
    name = "evict",
    description = "Evict cached OAuth token(s), forcing re-authentication (local purge only)")
final class OAuthTokenEvictCommand extends SshCommand {
  @Inject private OAuthTokenCache tokenCache;

  @Option(name = "--account-id", metaVar = "ID", usage = "account id whose OAuth token to evict")
  private Integer accountId;

  @Option(name = "--all", usage = "evict every cached OAuth token (e.g. after a site compromise)")
  private boolean all;

  @Override
  protected void run() throws Failure {
    if (all == (accountId != null)) {
      throw die("specify exactly one of --account-id or --all");
    }
    if (all) {
      tokenCache.removeAll();
      stdout.print("evicted all cached OAuth tokens\n");
    } else {
      tokenCache.remove(Account.id(accountId));
      stdout.print("evicted OAuth token for account " + accountId + "\n");
    }
  }
}
