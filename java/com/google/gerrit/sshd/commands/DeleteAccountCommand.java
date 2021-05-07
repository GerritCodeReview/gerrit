// Copyright (C) 2021 The Android Open Source Project
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

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.restapi.account.DeleteAccount;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.kohsuke.args4j.Argument;

/** Delete an account. * */
@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
@CommandMetaData(name = "delete-account", description = "Delete an account")
final class DeleteAccountCommand extends SshCommand {
  @Argument(index = 0, required = true, metaVar = "USERNAME", usage = "name of the user account")
  private String username;

  @Inject private DeleteAccount deleteAccount;
  @Inject private IdentifiedUser.GenericFactory userFactory;

  @Inject private AccountCache accountCache;

  @Override
  protected void run()
      throws IOException, ConfigInvalidException, UnloggedFailure, PermissionBackendException {
    enableGracefulStop();
    Optional<AccountState> accountState = accountCache.getByUsername(username);
    if (accountState.isEmpty()) {
      throw die("User not found.");
    }
    try {
      deleteAccount.apply(
          new AccountResource(userFactory.create(accountState.get().account().id())), null);
    } catch (RestApiException e) {
      throw die(e.getMessage());
    }
  }
}
