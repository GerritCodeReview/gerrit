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

import static com.google.gerrit.reviewdb.client.AccountExternalId.SCHEME_USERNAME;

import com.google.common.base.Strings;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.PutHttpPassword.Input;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Collections;
import org.apache.commons.codec.binary.Base64;

@Singleton
public class PutHttpPassword implements RestModifyView<AccountResource, Input> {
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
  private final Provider<ReviewDb> dbProvider;
  private final AccountCache accountCache;

  @Inject
  PutHttpPassword(
      Provider<CurrentUser> self, Provider<ReviewDb> dbProvider, AccountCache accountCache) {
    this.self = self;
    this.dbProvider = dbProvider;
    this.accountCache = accountCache;
  }

  @Override
  public Response<String> apply(AccountResource rsrc, Input input)
      throws AuthException, ResourceNotFoundException, ResourceConflictException, OrmException,
          IOException {
    if (input == null) {
      input = new Input();
    }
    input.httpPassword = Strings.emptyToNull(input.httpPassword);

    String newPassword;
    if (input.generate) {
      if (self.get() != rsrc.getUser() && !self.get().getCapabilities().canAdministrateServer()) {
        throw new AuthException("not allowed to generate HTTP password");
      }
      newPassword = generate();

    } else if (input.httpPassword == null) {
      if (self.get() != rsrc.getUser() && !self.get().getCapabilities().canAdministrateServer()) {
        throw new AuthException("not allowed to clear HTTP password");
      }
      newPassword = null;
    } else {
      if (!self.get().getCapabilities().canAdministrateServer()) {
        throw new AuthException(
            "not allowed to set HTTP password directly, "
                + "requires the Administrate Server permission");
      }
      newPassword = input.httpPassword;
    }
    return apply(rsrc.getUser(), newPassword);
  }

  public Response<String> apply(IdentifiedUser user, String newPassword)
      throws ResourceNotFoundException, ResourceConflictException, OrmException, IOException {
    if (user.getUserName() == null) {
      throw new ResourceConflictException("username must be set");
    }

    AccountExternalId id =
        dbProvider
            .get()
            .accountExternalIds()
            .get(new AccountExternalId.Key(SCHEME_USERNAME, user.getUserName()));
    if (id == null) {
      throw new ResourceNotFoundException();
    }
    id.setPassword(newPassword);
    dbProvider.get().accountExternalIds().update(Collections.singleton(id));
    accountCache.evict(user.getAccountId());

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
