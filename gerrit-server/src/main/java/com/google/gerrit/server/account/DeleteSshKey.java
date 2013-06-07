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

import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.DeleteSshKey.Input;
import com.google.gerrit.server.ssh.SshKeyCache;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.Collections;

public class DeleteSshKey implements
    RestModifyView<AccountResource.SshKey, Input> {
  public static class Input {
  }

  private final Provider<ReviewDb> dbProvider;
  private final SshKeyCache sshKeyCache;

  @Inject
  DeleteSshKey(Provider<ReviewDb> dbProvider, SshKeyCache sshKeyCache) {
    this.dbProvider = dbProvider;
    this.sshKeyCache = sshKeyCache;
  }

  @Override
  public Object apply(AccountResource.SshKey rsrc, Input input)
      throws OrmException {
    dbProvider.get().accountSshKeys()
        .deleteKeys(Collections.singleton(rsrc.getSshKey().getKey()));
    sshKeyCache.evict(rsrc.getUser().getUserName());
    return Response.none();
  }
}
