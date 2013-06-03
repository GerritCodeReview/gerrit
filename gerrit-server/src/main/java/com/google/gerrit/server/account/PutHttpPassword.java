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
import com.google.gerrit.server.account.PutHttpPassword.Input;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.apache.commons.codec.binary.Base64;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Collections;

public class PutHttpPassword implements RestModifyView<AccountResource, Input> {
  static class Input {
    String httpPassword;
    boolean generate;
  }

  private static final int LEN = 12;
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
  PutHttpPassword(Provider<CurrentUser> self, Provider<ReviewDb> dbProvider,
      AccountCache accountCache) {
    this.self = self;
    this.dbProvider = dbProvider;
    this.accountCache = accountCache;
  }

  @Override
  public Response<String> apply(AccountResource rsrc, Input input) throws AuthException,
      ResourceNotFoundException, ResourceConflictException, OrmException {
    if (self.get() != rsrc.getUser()
        && !self.get().getCapabilities().canAdministrateServer()) {
      throw new AuthException("not allowed to set HTTP password");
    }
    if (input == null) {
      input = new Input();
    }
    if (rsrc.getUser().getUserName() == null) {
      throw new ResourceConflictException("username must be set");
    }
    AccountExternalId.Key key =
        new AccountExternalId.Key(SCHEME_USERNAME, rsrc.getUser().getUserName());
    AccountExternalId id = dbProvider.get().accountExternalIds().get(key);
    if (id == null) {
      throw new ResourceNotFoundException();
    }

    String newPassword;
    if (input.generate) {
      newPassword = generate();
    } else {
      if (!Strings.isNullOrEmpty(input.httpPassword)
          && !self.get().getCapabilities().canAdministrateServer()) {
        throw new AuthException("not allowed to set HTTP password directly, "
            + "need to be Gerrit administrator");
      }
      newPassword = Strings.emptyToNull(input.httpPassword);
    }

    id.setPassword(newPassword);
    dbProvider.get().accountExternalIds().update(Collections.singleton(id));
    accountCache.evict(rsrc.getUser().getAccountId());

    return Strings.isNullOrEmpty(newPassword)
        ? Response.<String>none()
        : Response.ok(newPassword);
  }

  private String generate() {
    byte[] rand = new byte[LEN];
    rng.nextBytes(rand);

    byte[] enc = Base64.encodeBase64(rand, false);
    StringBuilder r = new StringBuilder(LEN);
    for (int i = 0; i < LEN; i++) {
      if (enc[i] == '=') {
        break;
      }
      r.append((char) enc[i]);
    }
    return r.toString();
  }
}
