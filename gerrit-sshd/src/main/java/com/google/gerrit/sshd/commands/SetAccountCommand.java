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

import com.google.gerrit.common.errors.EmailException;
import com.google.gerrit.extensions.restapi.RawInput;
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
import com.google.gerrit.server.account.GetSshKeys.SshKeyInfo;
import com.google.gerrit.server.account.PutActive;
import com.google.gerrit.server.account.PutHttpPassword;
import com.google.gerrit.server.account.PutName;
import com.google.gerrit.sshd.BaseCommand;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.apache.sshd.server.Environment;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Set a user's account settings. **/
@CommandMetaData(name = "set-account", description = "Change an account's settings")
final class SetAccountCommand extends BaseCommand {

  @Argument(index = 0, required = true, metaVar = "USER", usage = "full name, email-address, ssh username or account id")
  private Account.Id id;

  @Option(name = "--full-name", metaVar = "NAME", usage = "display name of the account")
  private String fullName;

  @Option(name = "--active", usage = "set account's state to active")
  private boolean active;

  @Option(name = "--inactive", usage = "set account's state to inactive")
  private boolean inactive;

  @Option(name = "--add-email", multiValued = true, metaVar = "EMAIL", usage = "email addresses to add to the account")
  private List<String> addEmails = new ArrayList<String>();

  @Option(name = "--delete-email", multiValued = true, metaVar = "EMAIL", usage = "email addresses to delete from the account")
  private List<String> deleteEmails = new ArrayList<String>();

  @Option(name = "--add-ssh-key", multiValued = true, metaVar = "-|KEY", usage = "public keys to add to the account")
  private List<String> addSshKeys = new ArrayList<String>();

  @Option(name = "--delete-ssh-key", multiValued = true, metaVar = "-|KEY", usage = "public keys to delete from the account")
  private List<String> deleteSshKeys = new ArrayList<String>();

  @Option(name = "--http-password", metaVar = "PASSWORD", usage = "password for HTTP authentication for the account")
  private String httpPassword;

  @Inject
  private IdentifiedUser currentUser;

  @Inject
  private IdentifiedUser.GenericFactory genericUserFactory;

  @Inject
  private CreateEmail.Factory createEmailFactory;

  @Inject
  private Provider<GetEmails> getEmailsProvider;

  @Inject
  private Provider<DeleteEmail> deleteEmailProvider;

  @Inject
  private Provider<PutName> putNameProvider;

  @Inject
  private Provider<PutHttpPassword> putHttpPasswordProvider;

  @Inject
  private Provider<PutActive> putActiveProvider;

  @Inject
  private Provider<DeleteActive> deleteActiveProvider;

  @Inject
  private Provider<AddSshKey> addSshKeyProvider;

  @Inject
  private Provider<GetSshKeys> getSshKeysProvider;

  @Inject
  private Provider<DeleteSshKey> deleteSshKeyProvider;

  private IdentifiedUser user;
  private AccountResource rsrc;

  @Override
  public void start(final Environment env) {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Exception {
        if (!currentUser.getCapabilities().canAdministrateServer()) {
          String msg =
              String.format(
                  "fatal: %s does not have \"Administrator\" capability.",
                  currentUser.getUserName());
          throw new UnloggedFailure(1, msg);
        }
        parseCommandLine();
        validate();
        setAccount();
      }
    });
  }

  private void validate() throws UnloggedFailure {
    if (active && inactive) {
      throw new UnloggedFailure(1,
          "--active and --inactive options are mutually exclusive.");
    }
    if (addSshKeys.contains("-") && deleteSshKeys.contains("-")) {
      throw new UnloggedFailure(1, "Only one option may use the stdin");
    }
    if (deleteSshKeys.contains("ALL")) {
      deleteSshKeys = Collections.singletonList("ALL");
    }
    if (deleteEmails.contains("ALL")) {
      deleteEmails = Collections.singletonList("ALL");
    }
  }

  private void setAccount() throws OrmException, IOException, UnloggedFailure {
    user = genericUserFactory.create(id);
    rsrc = new AccountResource(user);
    try {
      for (String email : addEmails) {
        addEmail(email);
      }

      for (String email : deleteEmails) {
        deleteEmail(email);
      }

      if (fullName != null) {
        PutName.Input in = new PutName.Input();
        in.name = fullName;
        putNameProvider.get().apply(rsrc, in);
      }

      if (httpPassword != null) {
        PutHttpPassword.Input in = new PutHttpPassword.Input();
        in.httpPassword = httpPassword;
        putHttpPasswordProvider.get().apply(rsrc, in);
      }

      if (active) {
        putActiveProvider.get().apply(rsrc, null);
      } else if (inactive) {
        try {
          deleteActiveProvider.get().apply(rsrc, null);
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

  private void addSshKeys(List<String> sshKeys) throws RestApiException,
      UnloggedFailure, OrmException, IOException {
    for (final String sshKey : sshKeys) {
      AddSshKey.Input in = new AddSshKey.Input();
      in.raw = new RawInput() {
        @Override
        public InputStream getInputStream() throws IOException {
          return new ByteArrayInputStream(sshKey.getBytes("UTF-8"));
        }

        @Override
        public String getContentType() {
          return "plain/text";
        }

        @Override
        public long getContentLength() {
          return sshKey.length();
        }
      };
      addSshKeyProvider.get().apply(rsrc, in);
    }
  }

  private void deleteSshKeys(List<String> sshKeys) throws RestApiException,
      OrmException {
    List<SshKeyInfo> infos = getSshKeysProvider.get().apply(rsrc);
    if (sshKeys.contains("ALL")) {
      for (SshKeyInfo i : infos) {
        deleteSshKey(i);
      }
    } else {
      for (String sshKey : sshKeys) {
        for (SshKeyInfo i : infos) {
          if (sshKey.trim().equals(i.sshPublicKey)
              || sshKey.trim().equals(i.comment)) {
            deleteSshKey(i);
          }
        }
      }
    }
  }

  private void deleteSshKey(SshKeyInfo i) throws OrmException {
    AccountSshKey sshKey = new AccountSshKey(
        new AccountSshKey.Id(user.getAccountId(), i.seq), i.sshPublicKey);
    deleteSshKeyProvider.get().apply(
        new AccountResource.SshKey(user, sshKey), null);
  }

  private void addEmail(String email) throws UnloggedFailure, RestApiException,
      OrmException {
    CreateEmail.Input in = new CreateEmail.Input();
    in.email = email;
    in.noConfirmation = true;
    try {
      createEmailFactory.create(email).apply(rsrc, in);
    } catch (EmailException e) {
      throw die(e.getMessage());
    }
  }

  private void deleteEmail(String email) throws UnloggedFailure,
      RestApiException, OrmException {
    if (email.equals("ALL")) {
      List<EmailInfo> emails = getEmailsProvider.get().apply(rsrc);
      DeleteEmail deleteEmail = deleteEmailProvider.get();
      for (EmailInfo e : emails) {
        deleteEmail.apply(new AccountResource.Email(user, e.email),
            new DeleteEmail.Input());
      }
    } else {
      deleteEmailProvider.get().apply(new AccountResource.Email(user, email),
          new DeleteEmail.Input());
    }
  }

  private List<String> readSshKey(final List<String> sshKeys)
      throws UnsupportedEncodingException, IOException {
    if (!sshKeys.isEmpty()) {
      String sshKey = "";
      int idx = sshKeys.indexOf("-");
      if (idx >= 0) {
        sshKey = "";
        BufferedReader br =
            new BufferedReader(new InputStreamReader(in, "UTF-8"));
        String line;
        while ((line = br.readLine()) != null) {
          sshKey += line + "\n";
        }
        sshKeys.set(idx, sshKey);
      }
    }
    return sshKeys;
  }
}
