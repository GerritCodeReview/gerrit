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

import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_USERNAME;

import com.google.common.base.Strings;
import com.google.gerrit.common.errors.EmailException;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.PutHttpPassword.Input;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.account.externalids.ExternalIdsUpdate;
import com.google.gerrit.server.mail.send.HttpPasswordSender;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import org.apache.commons.codec.binary.Base64;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PutHttpPassword implements RestModifyView<AccountResource, Input> {
  private static final Logger log = LoggerFactory.getLogger(AddSshKey.class);

  public static class Input {
    public String httpPassword;
    public boolean generate;
  }

  private static final int LEN = 31;
  private static final SecureRandom rng;

  static {
    try {
      rng = SecureRandom.getInstance("SHA1PRNG");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("Cannot create RNG for password generator", e);
    }
  }

  private final Provider<CurrentUser> self;
  private final PermissionBackend permissionBackend;
  private final ExternalIds externalIds;
  private final ExternalIdsUpdate.User externalIdsUpdate;
  private final HttpPasswordSender.Factory httpPasswordSenderFactory;

  @Inject
  PutHttpPassword(
      Provider<CurrentUser> self,
      PermissionBackend permissionBackend,
      ExternalIds externalIds,
      ExternalIdsUpdate.User externalIdsUpdate,
      HttpPasswordSender.Factory httpPasswordSenderFactory) {
    this.self = self;
    this.permissionBackend = permissionBackend;
    this.externalIds = externalIds;
    this.externalIdsUpdate = externalIdsUpdate;
    this.httpPasswordSenderFactory = httpPasswordSenderFactory;
  }

  @Override
  public Response<String> apply(AccountResource rsrc, Input input)
      throws AuthException, ResourceNotFoundException, ResourceConflictException, OrmException,
          IOException, ConfigInvalidException, PermissionBackendException {
    if (!self.get().hasSameAccountId(rsrc.getUser())) {
      permissionBackend.user(self).check(GlobalPermission.ADMINISTRATE_SERVER);
    }

    if (input == null) {
      input = new Input();
    }
    input.httpPassword = Strings.emptyToNull(input.httpPassword);

    String newPassword;
    if (input.generate) {
      newPassword = generate();
    } else if (input.httpPassword == null) {
      newPassword = null;
    } else {
      // Only administrators can explicitly set the password.
      permissionBackend.user(self).check(GlobalPermission.ADMINISTRATE_SERVER);
      newPassword = input.httpPassword;
    }
    return apply(rsrc.getUser(), newPassword);
  }

  public Response<String> apply(IdentifiedUser user, String newPassword)
      throws ResourceNotFoundException, ResourceConflictException, OrmException, IOException,
          ConfigInvalidException {
    if (user.getUserName() == null) {
      throw new ResourceConflictException("username must be set");
    }

    ExternalId extId = externalIds.get(ExternalId.Key.create(SCHEME_USERNAME, user.getUserName()));
    if (extId == null) {
      throw new ResourceNotFoundException();
    }
    ExternalId newExtId =
        ExternalId.createWithPassword(extId.key(), extId.accountId(), extId.email(), newPassword);
    externalIdsUpdate.create().upsert(newExtId);

    try {
      httpPasswordSenderFactory.create(user).send();
    } catch (EmailException e) {
      logger.atSevere().withCause(e).log(
          "Cannot send HttpPassword added or changed message to "
              + user.getAccount().getPreferredEmail(),
          e);
    }

    return Strings.isNullOrEmpty(newPassword) ? Response.<String>none() : Response.ok(newPassword);
  }

  public static String generate() {
    byte[] rand = new byte[LEN];
    rng.nextBytes(rand);

    byte[] enc = Base64.encodeBase64(rand, false);
    StringBuilder r = new StringBuilder(enc.length);
    for (int i = 0; i < enc.length; i++) {
      if (enc[i] == '=') {
        break;
      }
      r.append((char) enc[i]);
    }
    return r.toString();
  }
}
