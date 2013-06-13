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

import com.google.common.base.Charsets;
import com.google.common.io.ByteSource;
import com.google.gerrit.common.errors.InvalidSshKeyException;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.RawInput;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.AccountSshKey;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AddSshKey.Input;
import com.google.gerrit.server.account.GetSshKeys.SshKeyInfo;
import com.google.gerrit.server.ssh.SshKeyCache;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;

public class AddSshKey implements RestModifyView<AccountResource, Input> {
  public static class Input {
    public RawInput raw;
  }

  private final Provider<CurrentUser> self;
  private final Provider<ReviewDb> dbProvider;
  private final SshKeyCache sshKeyCache;

  @Inject
  AddSshKey(Provider<CurrentUser> self, Provider<ReviewDb> dbProvider,
      SshKeyCache sshKeyCache) {
    this.self = self;
    this.dbProvider = dbProvider;
    this.sshKeyCache = sshKeyCache;
  }

  @Override
  public Response<SshKeyInfo> apply(AccountResource rsrc, Input input)
      throws AuthException, MethodNotAllowedException, BadRequestException,
      ResourceConflictException, OrmException, IOException {
    if (self.get() != rsrc.getUser()
        && !self.get().getCapabilities().canAdministrateServer()) {
      throw new AuthException("not allowed to add SSH keys");
    }
    if (input == null) {
      input = new Input();
    }
    if (input.raw == null) {
      throw new BadRequestException("SSH public key missing");
    }

    int max = 0;
    for (AccountSshKey k : dbProvider.get().accountSshKeys()
        .byAccount(rsrc.getUser().getAccountId())) {
      max = Math.max(max, k.getKey().get());
    }

    final RawInput rawKey = input.raw;
    String sshPublicKey = new ByteSource() {
      @Override
      public InputStream openStream() throws IOException {
        return rawKey.getInputStream();
      }
    }.asCharSource(Charsets.UTF_8).read();

    try {
      AccountSshKey sshKey =
          sshKeyCache.create(new AccountSshKey.Id(
              rsrc.getUser().getAccountId(), max + 1), sshPublicKey);
      dbProvider.get().accountSshKeys().insert(Collections.singleton(sshKey));
      sshKeyCache.evict(rsrc.getUser().getUserName());
      return Response.<SshKeyInfo>created(new SshKeyInfo(sshKey));
    } catch (InvalidSshKeyException e) {
      throw new BadRequestException(e.getMessage());
    }
  }
}
