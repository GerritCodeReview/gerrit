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

import static com.google.gerrit.server.account.GetSshKeys.readFromDb;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.io.ByteSource;
import com.google.gerrit.common.errors.EmailException;
import com.google.gerrit.common.errors.InvalidSshKeyException;
import com.google.gerrit.extensions.common.SshKeyInfo;
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
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.mail.AddKeySender;
import com.google.gerrit.server.ssh.SshKeyCache;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

@Singleton
public class AddSshKey implements RestModifyView<AccountResource, Input> {
  private static final Logger log = LoggerFactory.getLogger(AddSshKey.class);

  public static class Input {
    public RawInput raw;
  }

  private final Provider<CurrentUser> self;
  private final Provider<ReviewDb> dbProvider;
  private final GitRepositoryManager repoManager;
  private final Provider<AllUsersName> allUsersName;
  private final Provider<MetaDataUpdate.User> metaDataUpdateFactory;
  private final SshKeyCache sshKeyCache;
  private final AddKeySender.Factory addKeyFactory;
  private final boolean readFromGit;

  @Inject
  AddSshKey(Provider<CurrentUser> self,
      Provider<ReviewDb> dbProvider,
      GitRepositoryManager repoManager,
      Provider<AllUsersName> allUsersName,
      Provider<MetaDataUpdate.User> metaDataUpdateFactory,
      SshKeyCache sshKeyCache,
      AddKeySender.Factory addKeyFactory,
      @GerritServerConfig Config cfg) {
    this.self = self;
    this.dbProvider = dbProvider;
    this.repoManager = repoManager;
    this.allUsersName = allUsersName;
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.sshKeyCache = sshKeyCache;
    this.addKeyFactory = addKeyFactory;
    this.readFromGit =
        cfg.getBoolean("user", null, "readSshKeysFromGit", false);
  }

  @Override
  public Response<SshKeyInfo> apply(AccountResource rsrc, Input input)
      throws AuthException, BadRequestException, OrmException, IOException,
      ConfigInvalidException {
    if (self.get() != rsrc.getUser()
        && !self.get().getCapabilities().canAdministrateServer()) {
      throw new AuthException("not allowed to add SSH keys");
    }
    return apply(rsrc.getUser(), input);
  }

  public Response<SshKeyInfo> apply(IdentifiedUser user, Input input)
      throws BadRequestException, OrmException, IOException,
      ConfigInvalidException {
    if (input == null) {
      input = new Input();
    }
    if (input.raw == null) {
      throw new BadRequestException("SSH public key missing");
    }

    final RawInput rawKey = input.raw;
    String sshPublicKey = new ByteSource() {
      @Override
      public InputStream openStream() throws IOException {
        return rawKey.getInputStream();
      }
    }.asCharSource(UTF_8).read();

    AccountSshKey sshKey;
    if (readFromGit) {
      try (MetaDataUpdate md =
              metaDataUpdateFactory.get().create(allUsersName.get());
          Repository git = repoManager.openRepository(allUsersName.get())) {
        VersionedAuthorizedKeys authorizedKeys =
            new VersionedAuthorizedKeys(user.getAccountId());
        authorizedKeys.load(md);
        sshKey = authorizedKeys.addKey(sshPublicKey);
        authorizedKeys.commit(md);
      }
    } else {
      List<AccountSshKey> keys =
          readFromDb(dbProvider.get(), user.getAccountId());
      int max = keys.isEmpty() ? 0 : keys.get(keys.size() - 1).getKey().get();

      try {
        sshKey = sshKeyCache.create(
            new AccountSshKey.Id(user.getAccountId(), max + 1), sshPublicKey);
        keys.add(sshKey);
        try (MetaDataUpdate md =
                metaDataUpdateFactory.get().create(allUsersName.get());
            Repository git = repoManager.openRepository(allUsersName.get())) {
          VersionedAuthorizedKeys authorizedKeys =
              new VersionedAuthorizedKeys(user.getAccountId());
          authorizedKeys.load(md);
          authorizedKeys.setKeys(keys);
          authorizedKeys.commit(md);
        }
      } catch (InvalidSshKeyException e) {
        throw new BadRequestException(e.getMessage());
      }
    }

    dbProvider.get().accountSshKeys().insert(Collections.singleton(sshKey));

    try {
      addKeyFactory.create(user, sshKey).send();
    } catch (EmailException e) {
      log.error("Cannot send SSH key added message to "
          + user.getAccount().getPreferredEmail(), e);
    }
    sshKeyCache.evict(user.getUserName());
    return Response.<SshKeyInfo>created(GetSshKeys.newSshKeyInfo(sshKey));
  }
}
