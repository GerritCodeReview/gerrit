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

package com.google.gerrit.server.restapi.account;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.flogger.FluentLogger;
import com.google.common.io.ByteSource;
import com.google.gerrit.exceptions.EmailException;
import com.google.gerrit.exceptions.InvalidSshKeyException;
import com.google.gerrit.extensions.api.accounts.SshKeyInput;
import com.google.gerrit.extensions.common.SshKeyInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RawInput;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestCollectionModifyView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.account.AccountSshKey;
import com.google.gerrit.server.account.VersionedAuthorizedKeys;
import com.google.gerrit.server.mail.send.AddKeySender;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.ssh.SshKeyCache;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class AddSshKey
    implements RestCollectionModifyView<AccountResource, AccountResource.SshKey, SshKeyInput> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Provider<CurrentUser> self;
  private final PermissionBackend permissionBackend;
  private final VersionedAuthorizedKeys.Accessor authorizedKeys;
  private final SshKeyCache sshKeyCache;
  private final AddKeySender.Factory addKeyFactory;

  @Inject
  AddSshKey(
      Provider<CurrentUser> self,
      PermissionBackend permissionBackend,
      VersionedAuthorizedKeys.Accessor authorizedKeys,
      SshKeyCache sshKeyCache,
      AddKeySender.Factory addKeyFactory) {
    this.self = self;
    this.permissionBackend = permissionBackend;
    this.authorizedKeys = authorizedKeys;
    this.sshKeyCache = sshKeyCache;
    this.addKeyFactory = addKeyFactory;
  }

  @Override
  public Response<SshKeyInfo> apply(AccountResource rsrc, SshKeyInput input)
      throws AuthException, BadRequestException, OrmException, IOException, ConfigInvalidException,
          PermissionBackendException {
    if (!self.get().hasSameAccountId(rsrc.getUser())) {
      permissionBackend.currentUser().check(GlobalPermission.ADMINISTRATE_SERVER);
    }
    return apply(rsrc.getUser(), input);
  }

  public Response<SshKeyInfo> apply(IdentifiedUser user, SshKeyInput input)
      throws BadRequestException, IOException, ConfigInvalidException {
    if (input == null) {
      input = new SshKeyInput();
    }
    if (input.raw == null) {
      throw new BadRequestException("SSH public key missing");
    }

    final RawInput rawKey = input.raw;
    String sshPublicKey =
        new ByteSource() {
          @Override
          public InputStream openStream() throws IOException {
            return rawKey.getInputStream();
          }
        }.asCharSource(UTF_8).read();

    try {
      AccountSshKey sshKey = authorizedKeys.addKey(user.getAccountId(), sshPublicKey);

      try {
        addKeyFactory.create(user, sshKey).send();
      } catch (EmailException e) {
        logger.atSevere().withCause(e).log(
            "Cannot send SSH key added message to %s", user.getAccount().getPreferredEmail());
      }

      user.getUserName().ifPresent(sshKeyCache::evict);
      return Response.created(GetSshKeys.newSshKeyInfo(sshKey));
    } catch (InvalidSshKeyException e) {
      throw new BadRequestException(e.getMessage());
    }
  }
}
