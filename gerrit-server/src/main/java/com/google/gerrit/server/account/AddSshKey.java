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

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.io.ByteSource;
import com.google.gerrit.common.errors.EmailException;
import com.google.gerrit.common.errors.InvalidSshKeyException;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RawInput;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Account.Id;
import com.google.gerrit.reviewdb.client.AccountSshKey;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AddSshKey.Input;
import com.google.gerrit.server.account.GetSshKeys.SshKeyInfo;
import com.google.gerrit.server.mail.AddKeySender;
import com.google.gerrit.server.ssh.SshKeyCache;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
  public List<SshKeyInfo> apply(AccountResource rsrc, Input input)
      throws AuthException, BadRequestException, OrmException, IOException {
    if (self.get() != rsrc.getUser()
        && !self.get().getCapabilities().canAdministrateServer()) {
      throw new AuthException("not allowed to add SSH keys");
    }
    return apply(rsrc.getUser(), input);
  }

  public List<SshKeyInfo> apply(IdentifiedUser user, Input input)
      throws BadRequestException, OrmException, IOException {
    if (input == null) {
      input = new Input();
    }
    if (input.raw == null) {
      throw new BadRequestException("SSH public key missing");
    }

    final RawInput rawInput = input.raw;
    ArrayList<String> keys = Lists.newArrayList(
        Splitter.on('\n').trimResults().omitEmptyStrings().split(
          new ByteSource() {
            @Override
            public InputStream openStream() throws IOException {
              return rawInput.getInputStream();
            }
          }.asCharSource(UTF_8).read()));

    if (keys.isEmpty()) {
      throw new BadRequestException("SSH public key missing");
    }

    // Iterate over all the given keys, and fail early if any of them is
    // invalid. Since we're using a HashMap, any duplicate keys will be
    // dropped.
    Id accountId = user.getAccountId();
    Map<String, AccountSshKey> keysToAdd = new HashMap<>(keys.size());
    for (String key : keys) {
      try {
        keysToAdd.put(key, sshKeyCache.create(new AccountSshKey.Id(
            accountId,
            dbProvider.get().nextAccountSshKeyId()),
            key));
      } catch (InvalidSshKeyException e) {
        throw new BadRequestException(e.getMessage());
      }
    }

    List<SshKeyInfo> added = Lists.newArrayList();
    for (AccountSshKey sshKey : keysToAdd.values()) {
      dbProvider.get().accountSshKeys().insert(Collections.singleton(sshKey));
      try {
        addKeyFactory.create(user, sshKey).send();
      } catch (EmailException e) {
        log.error("Cannot send SSH key added message to "
            + user.getAccount().getPreferredEmail(), e);
      }
      added.add(new SshKeyInfo(sshKey));
    }
    sshKeyCache.evict(user.getUserName());
    return added;
  }
}
