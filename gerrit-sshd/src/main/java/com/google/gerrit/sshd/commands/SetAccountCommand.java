// Copyright (C) 2012 The Android Open Source Project
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

import com.google.common.base.Strings;
import com.google.gerrit.common.RawInputUtil;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.errors.EmailException;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.api.accounts.EmailInput;
import com.google.gerrit.extensions.common.SshKeyInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountSshKey;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.account.AddSshKey;
import com.google.gerrit.server.account.CreateEmail;
import com.google.gerrit.server.account.DeleteActive;
import com.google.gerrit.server.account.DeleteEmail;
import com.google.gerrit.server.account.DeleteSshKey;
import com.google.gerrit.server.account.GetEmails;
import com.google.gerrit.server.account.GetEmails.EmailInfo;
import com.google.gerrit.server.account.GetSshKeys;
import com.google.gerrit.server.account.PutActive;
import com.google.gerrit.server.account.PutHttpPassword;
import com.google.gerrit.server.account.PutName;
import com.google.gerrit.server.account.PutPreferred;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

/** Set a user's account settings. * */
@CommandMetaData(name = "set-account", description = "Change an account's settings")
@RequiresCapability(GlobalCapability.MODIFY_ACCOUNT)
final class SetAccountCommand extends SshCommand {

  @Argument(
    index = 0,
    required = true,
    metaVar = "USER",
    usage = "full name, email-address, ssh username or account id"
  )
  private Account.Id id;

  @Option(name = "--full-name", metaVar = "NAME", usage = "display name of the account")
  private String fullName;

  @Option(name = "--active", usage = "set account's state to active")
  private boolean active;

  @Option(name = "--inactive", usage = "set account's state to inactive")
  private boolean inactive;

  @Option(name = "--add-email", metaVar = "EMAIL", usage = "email addresses to add to the account")
  private List<String> addEmails = new ArrayList<>();

  @Option(
    name = "--delete-email",
    metaVar = "EMAIL",
    usage = "email addresses to delete from the account"
  )
  private List<String> deleteEmails = new ArrayList<>();

  @Option(
    name = "--preferred-email",
    metaVar = "EMAIL",
    usage = "a registered email address from the account"
  )
  private String preferredEmail;

  @Option(name = "--add-ssh-key", metaVar = "-|KEY", usage = "public keys to add to the account")
  private List<String> addSshKeys = new ArrayList<>();

  @Option(
    name = "--delete-ssh-key",
    metaVar = "-|KEY",
    usage = "public keys to delete from the account"
  )
  private List<String> deleteSshKeys = new ArrayList<>();

  @Option(
    name = "--http-password",
    metaVar = "PASSWORD",
    usage = "password for HTTP authentication for the account"
  )
  private String httpPassword;

  @Option(name = "--clear-http-password", usage = "clear HTTP password for the account")
  private boolean clearHttpPassword;

  @Inject private IdentifiedUser.GenericFactory genericUserFactory;

  @Inject private CreateEmail.Factory createEmailFactory;

  @Inject private GetEmails getEmails;

  @Inject private DeleteEmail deleteEmail;

  @Inject private PutPreferred putPreferred;

  @Inject private PutName putName;

  @Inject private PutHttpPassword putHttpPassword;

  @Inject private PutActive putActive;

  @Inject private DeleteActive deleteActive;

  @Inject private AddSshKey addSshKey;

  @Inject private GetSshKeys getSshKeys;

  @Inject private DeleteSshKey deleteSshKey;

  private IdentifiedUser user;
  private AccountResource rsrc;

  @Override
  public void run() throws Exception {
    validate();
    setAccount();
  }

  private void validate() throws UnloggedFailure {
    if (active && inactive) {
      throw die("--active and --inactive options are mutually exclusive.");
    }
    if (clearHttpPassword && !Strings.isNullOrEmpty(httpPassword)) {
      throw die("--http-password and --clear-http-password options are " + "mutually exclusive.");
    }
    if (addSshKeys.contains("-") && deleteSshKeys.contains("-")) {
      throw die("Only one option may use the stdin");
    }
    if (deleteSshKeys.contains("ALL")) {
      deleteSshKeys = Collections.singletonList("ALL");
    }
    if (deleteEmails.contains("ALL")) {
      deleteEmails = Collections.singletonList("ALL");
    }
    if (deleteEmails.contains(preferredEmail)) {
      throw die(
          "--preferred-email and --delete-email options are mutually "
              + "exclusive for the same email address.");
    }
  }

  private void setAccount()
      throws OrmException, IOException, UnloggedFailure, ConfigInvalidException {
    user = genericUserFactory.create(id);
    rsrc = new AccountResource(user);
    try {
      for (String email : addEmails) {
        addEmail(email);
      }

      for (String email : deleteEmails) {
        deleteEmail(email);
      }

      if (preferredEmail != null) {
        putPreferred(preferredEmail);
      }

      if (fullName != null) {
        PutName.Input in = new PutName.Input();
        in.name = fullName;
        putName.apply(rsrc, in);
      }

      if (httpPassword != null || clearHttpPassword) {
        PutHttpPassword.Input in = new PutHttpPassword.Input();
        in.httpPassword = httpPassword;
        putHttpPassword.apply(rsrc, in);
      }

      if (active) {
        putActive.apply(rsrc, null);
      } else if (inactive) {
        try {
          deleteActive.apply(rsrc, null);
        } catch (ResourceNotFoundException e) {
          // user is already inactive
        }
      }

      addSshKeys = readSshKey(addSshKeys);
      if (!addSshKeys.isEmpty()) {
        addSshKeys(addSshKeys);
      }

      deleteSshKeys = readSshKey(deleteSshKeys);
      if (!deleteSshKeys.isEmpty()) {
        deleteSshKeys(deleteSshKeys);
      }
    } catch (RestApiException e) {
      throw die(e.getMessage());
    }
  }

  private void addSshKeys(List<String> sshKeys)
      throws RestApiException, OrmException, IOException, ConfigInvalidException {
    for (final String sshKey : sshKeys) {
      AddSshKey.Input in = new AddSshKey.Input();
      in.raw = RawInputUtil.create(sshKey.getBytes(), "plain/text");
      addSshKey.apply(rsrc, in);
    }
  }

  private void deleteSshKeys(List<String> sshKeys)
      throws RestApiException, OrmException, RepositoryNotFoundException, IOException,
          ConfigInvalidException {
    List<SshKeyInfo> infos = getSshKeys.apply(rsrc);
    if (sshKeys.contains("ALL")) {
      for (SshKeyInfo i : infos) {
        deleteSshKey(i);
      }
    } else {
      for (String sshKey : sshKeys) {
        for (SshKeyInfo i : infos) {
          if (sshKey.trim().equals(i.sshPublicKey) || sshKey.trim().equals(i.comment)) {
            deleteSshKey(i);
          }
        }
      }
    }
  }

  private void deleteSshKey(SshKeyInfo i)
      throws AuthException, OrmException, RepositoryNotFoundException, IOException,
          ConfigInvalidException {
    AccountSshKey sshKey =
        new AccountSshKey(new AccountSshKey.Id(user.getAccountId(), i.seq), i.sshPublicKey);
    deleteSshKey.apply(new AccountResource.SshKey(user, sshKey), null);
  }

  private void addEmail(String email)
      throws UnloggedFailure, RestApiException, OrmException, IOException {
    EmailInput in = new EmailInput();
    in.email = email;
    in.noConfirmation = true;
    try {
      createEmailFactory.create(email).apply(rsrc, in);
    } catch (EmailException e) {
      throw die(e.getMessage());
    }
  }

  private void deleteEmail(String email) throws RestApiException, OrmException, IOException {
    if (email.equals("ALL")) {
      List<EmailInfo> emails = getEmails.apply(rsrc);
      for (EmailInfo e : emails) {
        deleteEmail.apply(new AccountResource.Email(user, e.email), new DeleteEmail.Input());
      }
    } else {
      deleteEmail.apply(new AccountResource.Email(user, email), new DeleteEmail.Input());
    }
  }

  private void putPreferred(String email) throws RestApiException, OrmException, IOException {
    for (EmailInfo e : getEmails.apply(rsrc)) {
      if (e.email.equals(email)) {
        putPreferred.apply(new AccountResource.Email(user, email), null);
        return;
      }
    }
    stderr.println("preferred email not found: " + email);
  }

  private List<String> readSshKey(final List<String> sshKeys)
      throws UnsupportedEncodingException, IOException {
    if (!sshKeys.isEmpty()) {
      int idx = sshKeys.indexOf("-");
      if (idx >= 0) {
        StringBuilder sshKey = new StringBuilder();
        BufferedReader br = new BufferedReader(new InputStreamReader(in, UTF_8));
        String line;
        while ((line = br.readLine()) != null) {
          sshKey.append(line).append("\n");
        }
        sshKeys.set(idx, sshKey.toString());
      }
    }
    return sshKeys;
  }
}
