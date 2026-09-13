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

import com.google.gerrit.auth.oauth.OAuthTokenRevoker;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.entities.Account;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.kohsuke.args4j.Option;

/**
 * Revokes OAuth token(s) at the IdP (RFC 7009) and evicts them; the compromise-response counterpart
 * of {@code evict} (which only purges locally). Fires {@code OAuthTokenRevokedListener}.
 *
 * <p>Does not terminate the account's Gerrit web sessions: a live {@code GerritAccount} cookie
 * keeps UI access until its own TTL; flush {@code web_sessions} or deactivate the account for a
 * full response (core has no per-account logout, even on deactivation).
 */
@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
@CommandMetaData(
    name = "revoke",
    description = "Revoke OAuth token(s) at the IdP and evict them (compromise response)")
final class OAuthTokenRevokeCommand extends SshCommand {
  @Inject private OAuthTokenRevoker revoker;
  @Inject private AccountResolver accountResolver;

  @Option(
      name = "--account-id",
      metaVar = "ID",
      usage = "numeric account id whose OAuth token to revoke")
  private Integer accountId;

  @Option(
      name = "--username",
      metaVar = "NAME",
      usage = "account (username, email, or id) whose OAuth token to revoke")
  private String username;

  @Option(name = "--all", usage = "revoke every cached OAuth token (e.g. after a site compromise)")
  private boolean all;

  @Override
  protected void run() throws Failure {
    int selectors = (accountId != null ? 1 : 0) + (username != null ? 1 : 0) + (all ? 1 : 0);
    if (selectors != 1) {
      throw die("specify exactly one of --account-id, --username, or --all");
    }
    if (all) {
      int n = revoker.revokeAll();
      stdout.print("revoked and evicted " + n + " cached OAuth token(s)\n");
      return;
    }
    Account.Id id = accountId != null ? Account.id(accountId) : resolve(username);
    OAuthTokenRevoker.Result result = revoker.revoke(id);
    if (result == OAuthTokenRevoker.Result.REVOKED) {
      stdout.print("revoked at the IdP and evicted OAuth token for account " + id + "\n");
    } else if (result == OAuthTokenRevoker.Result.EVICTED_ONLY) {
      stdout.print(
          "evicted OAuth token for account "
              + id
              + " (provider does not support IdP revocation, or the IdP call failed; see logs)\n");
    } else {
      stdout.print("no OAuth token cached for account " + id + "\n");
    }
  }

  private Account.Id resolve(String input) throws Failure {
    try {
      return accountResolver.resolve(input).asUnique().account().id();
    } catch (UnprocessableEntityException e) {
      throw die(e.getMessage());
    } catch (StorageException | IOException | ConfigInvalidException e) {
      throw die(e);
    }
  }
}
