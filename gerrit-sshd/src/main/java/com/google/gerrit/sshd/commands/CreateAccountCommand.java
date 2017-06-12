// Copyright (C) 2010 The Android Open Source Project
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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.Lists;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.api.accounts.AccountInput;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.CreateAccount;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

/** Create a new user account. * */
@RequiresCapability(GlobalCapability.CREATE_ACCOUNT)
@CommandMetaData(name = "create-account", description = "Create a new batch/role account")
final class CreateAccountCommand extends SshCommand {
  @Option(
    name = "--group",
    aliases = {"-g"},
    metaVar = "GROUP",
    usage = "groups to add account to"
  )
  private List<AccountGroup.Id> groups = new ArrayList<>();

  @Option(name = "--full-name", metaVar = "NAME", usage = "display name of the account")
  private String fullName;

  @Option(name = "--email", metaVar = "EMAIL", usage = "email address of the account")
  private String email;

  @Option(name = "--ssh-key", metaVar = "-|KEY", usage = "public key for SSH authentication")
  private String sshKey;

  @Option(
    name = "--http-password",
    metaVar = "PASSWORD",
    usage = "password for HTTP authentication"
  )
  private String httpPassword;

  @Argument(index = 0, required = true, metaVar = "USERNAME", usage = "name of the user account")
  private String username;

  @Inject private CreateAccount.Factory createAccountFactory;

  @Override
  protected void run() throws OrmException, IOException, ConfigInvalidException, UnloggedFailure {
    AccountInput input = new AccountInput();
    input.username = username;
    input.email = email;
    input.name = fullName;
    input.sshKey = readSshKey();
    input.httpPassword = httpPassword;
    input.groups = Lists.transform(groups, AccountGroup.Id::toString);
    try {
      createAccountFactory.create(username).apply(TopLevelResource.INSTANCE, input);
    } catch (RestApiException e) {
      throw die(e.getMessage());
    }
  }

  private String readSshKey() throws IOException {
    if (sshKey == null) {
      return null;
    }
    if ("-".equals(sshKey)) {
      sshKey = "";
      BufferedReader br = new BufferedReader(new InputStreamReader(in, UTF_8));
      String line;
      while ((line = br.readLine()) != null) {
        sshKey += line + "\n";
      }
    }
    return sshKey;
  }
}
