// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server.config;

import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.DefaultInput;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.config.ConfirmEmail.Input;
import com.google.gerrit.server.mail.EmailTokenVerifier;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;

@Singleton
public class ConfirmEmail implements RestModifyView<ConfigResource, Input> {
  public static class Input {
    @DefaultInput public String token;
  }

  private final Provider<CurrentUser> self;
  private final EmailTokenVerifier emailTokenVerifier;
  private final AccountManager accountManager;

  @Inject
  public ConfirmEmail(
      Provider<CurrentUser> self,
      EmailTokenVerifier emailTokenVerifier,
      AccountManager accountManager) {
    this.self = self;
    this.emailTokenVerifier = emailTokenVerifier;
    this.accountManager = accountManager;
  }

  @Override
  public Response<?> apply(ConfigResource rsrc, Input input)
      throws AuthException, UnprocessableEntityException, AccountException, OrmException,
          IOException {
    CurrentUser user = self.get();
    if (!user.isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }

    if (input == null) {
      input = new Input();
    }
    if (input.token == null) {
      throw new UnprocessableEntityException("missing token");
    }

    try {
      EmailTokenVerifier.ParsedToken token = emailTokenVerifier.decode(input.token);
      Account.Id accId = user.getAccountId();
      if (accId.equals(token.getAccountId())) {
        accountManager.link(accId, token.toAuthRequest());
        return Response.none();
      }
      throw new UnprocessableEntityException("invalid token");
    } catch (EmailTokenVerifier.InvalidTokenException e) {
      throw new UnprocessableEntityException("invalid token");
    } catch (AccountException e) {
      throw new UnprocessableEntityException(e.getMessage());
    }
  }
}
