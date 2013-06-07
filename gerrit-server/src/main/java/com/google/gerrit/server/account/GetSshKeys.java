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

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.AccountSshKey;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.List;

public class GetSshKeys implements RestReadView<AccountResource> {

  private final Provider<CurrentUser> self;
  private final Provider<ReviewDb> dbProvider;

  @Inject
  GetSshKeys(Provider<CurrentUser> self, Provider<ReviewDb> dbProvider) {
    this.self = self;
    this.dbProvider = dbProvider;
  }

  @Override
  public List<SshKeyInfo> apply(AccountResource rsrc) throws AuthException,
      OrmException {
    if (self.get() != rsrc.getUser()
        && !self.get().getCapabilities().canAdministrateServer()) {
      throw new AuthException("not allowed to get SSH keys");
    }

    List<SshKeyInfo> sshKeys = Lists.newArrayList();
    for (AccountSshKey sshKey : dbProvider.get().accountSshKeys()
        .byAccount(rsrc.getUser().getAccountId()).toList()) {
      sshKeys.add(new SshKeyInfo(sshKey));
    }
    return sshKeys;
  }

  public static class SshKeyInfo {
    public SshKeyInfo(AccountSshKey sshKey) {
      seq = sshKey.getKey().get();
      sshPublicKey = sshKey.getSshPublicKey();
      encodedKey = sshKey.getEncodedKey();
      algorithm = sshKey.getAlgorithm();
      comment = Strings.emptyToNull(sshKey.getComment());
      valid = sshKey.isValid();
    }

    public int seq;
    public String sshPublicKey;
    public String encodedKey;
    public String algorithm;
    public String comment;
    public boolean valid;
  }
}
