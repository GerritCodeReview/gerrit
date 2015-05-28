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
import com.google.gerrit.extensions.api.accounts.EmailInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Account.FieldName;
import com.google.gerrit.reviewdb.client.AuthType;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.GetEmails.EmailInfo;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.mail.RegisterNewEmailSender;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

import org.apache.commons.validator.routines.EmailValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateEmail implements RestModifyView<AccountResource, EmailInput> {
  private final Logger log = LoggerFactory.getLogger(getClass());

  public static interface Factory {
    CreateEmail create(String email);
  }

  private final Provider<CurrentUser> self;
  private final Realm realm;
  private final AuthConfig authConfig;
  private final AccountManager accountManager;
  private final RegisterNewEmailSender.Factory registerNewEmailFactory;
  private final PutPreferred putPreferred;
  private final String email;

  @Inject
  CreateEmail(Provider<CurrentUser> self,
      Realm realm,
      AuthConfig authConfig,
      AccountManager accountManager,
      RegisterNewEmailSender.Factory registerNewEmailFactory,
      PutPreferred putPreferred,
      @Assisted String email) {
    this.self = self;
    this.realm = realm;
    this.authConfig = authConfig;
    this.accountManager = accountManager;
    this.registerNewEmailFactory = registerNewEmailFactory;
    this.putPreferred = putPreferred;
    this.email = email;
  }

  @Override
  public Response<EmailInfo> apply(AccountResource rsrc, EmailInput input)
      throws AuthException, BadRequestException, ResourceConflictException,
      ResourceNotFoundException, OrmException, EmailException,
      MethodNotAllowedException {
    if (self.get() != rsrc.getUser()
        && !self.get().getCapabilities().canModifyAccount()) {
      throw new AuthException("not allowed to add email address");
    }

    if (input == null) {
      input = new EmailInput();
    }

    if (!EmailValidator.getInstance().isValid(email)) {
      throw new BadRequestException("invalid email address");
    }

    if (input.noConfirmation
        && !self.get().getCapabilities().canModifyAccount()) {
      throw new AuthException("not allowed to use no_confirmation");
    }

    return apply(rsrc.getUser(), input);
  }

  public Response<EmailInfo> apply(IdentifiedUser user, EmailInput input)
      throws AuthException, BadRequestException, ResourceConflictException,
      ResourceNotFoundException, OrmException, EmailException,
      MethodNotAllowedException {
    if (!realm.allowsEdit(FieldName.REGISTER_NEW_EMAIL)) {
      throw new MethodNotAllowedException("realm does not allow adding emails");
    }

    if (input.email != null && !email.equals(input.email)) {
      throw new BadRequestException("email address must match URL");
    }

    EmailInfo info = new EmailInfo();
    info.email = email;
    if (input.noConfirmation
        || authConfig.getAuthType() == AuthType.DEVELOPMENT_BECOME_ANY_ACCOUNT) {
      try {
        accountManager.link(user.getAccountId(),
            AuthRequest.forEmail(email));
      } catch (AccountException e) {
        throw new ResourceConflictException(e.getMessage());
      }
      if (input.preferred) {
        putPreferred.apply(
            new AccountResource.Email(user, email),
            null);
        info.preferred = true;
      }
    } else {
      try {
        registerNewEmailFactory.create(email).send();
        info.pendingConfirmation = true;
      } catch (EmailException | RuntimeException e) {
        log.error("Cannot send email verification message to " + email, e);
        throw e;
      }
    }
    return Response.created(info);
  }
}
