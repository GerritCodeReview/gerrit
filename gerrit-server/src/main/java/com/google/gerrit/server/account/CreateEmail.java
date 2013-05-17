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

import com.google.gerrit.common.errors.EmailException;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.DefaultInput;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.AuthType;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.CreateEmail.Input;
import com.google.gerrit.server.account.GetEmails.EmailInfo;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.mail.RegisterNewEmailSender;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateEmail implements RestModifyView<AccountResource, Input> {
  private final Logger log = LoggerFactory.getLogger(getClass());

  static class Input {
    @DefaultInput
    String email;
    boolean preferred;
  }

  static interface Factory {
    CreateEmail create(String email);
  }

  private final Provider<CurrentUser> self;
  private final AuthConfig authConfig;
  private final AccountManager accountManager;
  private final RegisterNewEmailSender.Factory registerNewEmailFactory;
  private final Provider<PutPreferred> putPreferredProvider;
  private final String email;

  @Inject
  CreateEmail(Provider<CurrentUser> self, AuthConfig authConfig,
      AccountManager accountManager,
      RegisterNewEmailSender.Factory registerNewEmailFactory,
      Provider<PutPreferred> putPreferredProvider, @Assisted String email) {
    this.self = self;
    this.authConfig = authConfig;
    this.accountManager = accountManager;
    this.registerNewEmailFactory = registerNewEmailFactory;
    this.putPreferredProvider = putPreferredProvider;
    this.email = email;
  }

  @Override
  public Object apply(AccountResource rsrc, Input input) throws AuthException,
      BadRequestException, ResourceConflictException,
      ResourceNotFoundException, OrmException, EmailException {
    if (!(self.get() instanceof IdentifiedUser)) {
      throw new AuthException("Authentication required");
    }
    IdentifiedUser s = (IdentifiedUser) self.get();
    if (s.getAccountId().get() != rsrc.getUser().getAccountId().get()
        && !self.get().getCapabilities().canAdministrateServer()) {
      throw new AuthException("not allowed to add email address");
    }
    if (input == null) {
      input = new Input();
    }
    if (input.email != null && !email.equals(input.email)) {
      throw new BadRequestException("email address must match URL");
    }

    if (authConfig.getAuthType() == AuthType.DEVELOPMENT_BECOME_ANY_ACCOUNT) {
      try {
        accountManager.link(rsrc.getUser().getAccountId(),
            AuthRequest.forEmail(email));
      } catch (AccountException e) {
        throw new ResourceConflictException(e.getMessage());
      }
    } else {
      try {
        RegisterNewEmailSender sender = registerNewEmailFactory.create(email);
        sender.send();
      } catch (EmailException e) {
        log.error("Cannot send email verification message to " + email, e);
        throw e;
      } catch (RuntimeException e) {
        log.error("Cannot send email verification message to " + email, e);
        throw e;
      }
    }
    EmailInfo e = new EmailInfo();
    e.email = email;
    if (input.preferred) {
      putPreferredProvider.get().apply(
          new AccountResource.Email(rsrc.getUser(), email), null);
      e.setPreferred(true);
    }
    return Response.created(e);
  }
}
