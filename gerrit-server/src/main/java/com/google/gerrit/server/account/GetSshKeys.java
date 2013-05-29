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
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.AccountSshKey;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.List;

public class GetSshKeys implements RestReadView<AccountResource> {

  private final Provider<ReviewDb> dbProvider;

  @Inject
  GetSshKeys(Provider<ReviewDb> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public List<SshKeyInfo> apply(AccountResource rsrc) throws OrmException {
    List<SshKeyInfo> sshKeys = Lists.newArrayList();
    for (AccountSshKey sshKey : dbProvider.get().accountSshKeys()
        .byAccount(rsrc.getUser().getAccountId()).toList()) {
      SshKeyInfo info = new SshKeyInfo();
      info.id = sshKey.getKey().get();
      info.sshPublicKey = sshKey.getSshPublicKey();
      info.encodedKey = sshKey.getEncodedKey();
      info.algorithm = sshKey.getAlgorithm();
      info.comment = Strings.emptyToNull(sshKey.getComment());
      info.valid = sshKey.isValid();
      sshKeys.add(info);
    }
    return sshKeys;
  }

  public static class SshKeyInfo {
    public int id;
    public String sshPublicKey;
    public String encodedKey;
    public String algorithm;
    public String comment;
    public boolean valid;
  }
}
