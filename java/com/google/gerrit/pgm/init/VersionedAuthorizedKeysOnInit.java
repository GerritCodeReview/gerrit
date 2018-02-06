// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.pgm.init;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Strings;
import com.google.gerrit.pgm.init.api.AllUsersNameOnInitProvider;
import com.google.gerrit.pgm.init.api.InitFlags;
import com.google.gerrit.pgm.init.api.VersionedMetaDataOnInit;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.account.AccountSshKey;
import com.google.gerrit.server.account.AuthorizedKeys;
import com.google.gerrit.server.account.VersionedAuthorizedKeys;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;

public class VersionedAuthorizedKeysOnInit extends VersionedMetaDataOnInit {
  public interface Factory {
    VersionedAuthorizedKeysOnInit create(Account.Id accountId);
  }

  private final Account.Id accountId;
  private List<Optional<AccountSshKey>> keys;

  @Inject
  public VersionedAuthorizedKeysOnInit(
      AllUsersNameOnInitProvider allUsers,
      SitePaths site,
      InitFlags flags,
      @Assisted Account.Id accountId) {
    super(flags, site, allUsers.get(), RefNames.refsUsers(accountId));
    this.accountId = accountId;
  }

  @Override
  public VersionedAuthorizedKeysOnInit load() throws IOException, ConfigInvalidException {
    super.load();
    return this;
  }

  @Override
  protected void onLoad() throws IOException, ConfigInvalidException {
    keys = AuthorizedKeys.parse(accountId, readUTF8(AuthorizedKeys.FILE_NAME));
  }

  public AccountSshKey addKey(String pub) {
    checkState(keys != null, "SSH keys not loaded yet");
    int seq = keys.isEmpty() ? 1 : keys.size() + 1;
    AccountSshKey key =
        new VersionedAuthorizedKeys.SimpleSshKeyCreator().create(accountId, seq, pub);
    keys.add(Optional.of(key));
    return key;
  }

  @Override
  protected boolean onSave(CommitBuilder commit) throws IOException {
    if (Strings.isNullOrEmpty(commit.getMessage())) {
      commit.setMessage("Updated SSH keys\n");
    }

    saveUTF8(AuthorizedKeys.FILE_NAME, AuthorizedKeys.serialize(keys));
    return true;
  }
}
