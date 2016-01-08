// Copyright (C) 2013 The Android Open Source Project
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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.Iterables;
import com.google.common.io.ByteSource;
import com.google.gerrit.common.errors.EmailException;
import com.google.gerrit.common.errors.InvalidSshKeyException;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RawInput;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.AccountSshKey;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AddSshKey.Input;
import com.google.gerrit.server.account.GetSshKeys.SshKeyInfo;
import com.google.gerrit.server.mail.AddKeySender;
import com.google.gerrit.server.ssh.SshKeyCache;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;

@Singleton
public class AddSshKey implements RestModifyView<AccountResource, Input> {
  private static final Logger log = LoggerFactory.getLogger(AddSshKey.class);

  public static class Input {
    public RawInput raw;
  }

  private final Provider<CurrentUser> self;
  private final Provider<ReviewDb> dbProvider;
  private final SshKeyCache sshKeyCache;
  private final AddKeySender.Factory addKeyFactory;

  @Inject
  AddSshKey(Provider<CurrentUser> self, Provider<ReviewDb> dbProvider,
      SshKeyCache sshKeyCache, AddKeySender.Factory addKeyFactory) {
    this.self = self;
    this.dbProvider = dbProvider;
    this.sshKeyCache = sshKeyCache;
    this.addKeyFactory = addKeyFactory;
  }

  @Override
  public Response<SshKeyInfo> apply(AccountResource rsrc, Input input)
      throws AuthException, BadRequestException, OrmException, IOException {
    if (self.get() != rsrc.getUser()
        && self.get().getUserName() != rsrc.getUser().getUserName()
        && !self.get().getCapabilities().canAdministrateServer()) {
      throw new AuthException("not allowed to add SSH keys");
    }
    return apply(rsrc.getUser(), input);
  }

  public Response<SshKeyInfo> apply(IdentifiedUser user, Input input)
      throws BadRequestException, OrmException, IOException {
    if (input == null) {
      input = new Input();
    }
    if (input.raw == null) {
      throw new BadRequestException("SSH public key missing");
    }

    ResultSet<AccountSshKey> byAccountLast =
        dbProvider.get().accountSshKeys().byAccountLast(user.getAccountId());
    AccountSshKey last = Iterables.getOnlyElement(byAccountLast, null);
    int max = last == null ? 0 : last.getKey().get();

    final RawInput rawKey = input.raw;
    String sshPublicKey = new ByteSource() {
      @Override
      public InputStream openStream() throws IOException {
        return rawKey.getInputStream();
      }
    }.asCharSource(UTF_8).read();

    try {
      AccountSshKey sshKey =
          sshKeyCache.create(new AccountSshKey.Id(
              user.getAccountId(), max + 1), sshPublicKey);
      dbProvider.get().accountSshKeys().insert(Collections.singleton(sshKey));
      try {
        addKeyFactory.create(user, sshKey).send();
      } catch (EmailException e) {
        log.error("Cannot send SSH key added message to "
            + user.getAccount().getPreferredEmail(), e);
      }
      sshKeyCache.evict(user.getUserName());
      return Response.<SshKeyInfo>created(new SshKeyInfo(sshKey));
    } catch (InvalidSshKeyException e) {
      throw new BadRequestException(e.getMessage());
    }
  }
}
