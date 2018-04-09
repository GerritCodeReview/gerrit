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

import static com.google.gerrit.extensions.client.AuthType.DEVELOPMENT_BECOME_ANY_ACCOUNT;

import com.google.gerrit.common.errors.EmailException;
import com.google.gerrit.extensions.api.accounts.EmailInput;
import com.google.gerrit.extensions.client.AccountFieldName;
import com.google.gerrit.extensions.common.EmailInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.account.Realm;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.mail.send.OutgoingEmailValidator;
import com.google.gerrit.server.mail.send.RegisterNewEmailSender;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateEmail implements RestModifyView<AccountResource, EmailInput> {
  private static final Logger log = LoggerFactory.getLogger(CreateEmail.class);

  public interface Factory {
    CreateEmail create(String email);
  }

  private final Provider<CurrentUser> self;
  private final Realm realm;
  private final PermissionBackend permissionBackend;
  private final AccountManager accountManager;
  private final RegisterNewEmailSender.Factory registerNewEmailFactory;
  private final PutPreferred putPreferred;
  private final OutgoingEmailValidator validator;
  private final String email;
  private final boolean isDevMode;

  @Inject
  CreateEmail(
      Provider<CurrentUser> self,
      Realm realm,
      PermissionBackend permissionBackend,
      AuthConfig authConfig,
      AccountManager accountManager,
      RegisterNewEmailSender.Factory registerNewEmailFactory,
      PutPreferred putPreferred,
      OutgoingEmailValidator validator,
      @Assisted String email) {
    this.self = self;
    this.realm = realm;
    this.permissionBackend = permissionBackend;
    this.accountManager = accountManager;
    this.registerNewEmailFactory = registerNewEmailFactory;
    this.putPreferred = putPreferred;
    this.validator = validator;
    this.email = email != null ? email.trim() : null;
    this.isDevMode = authConfig.getAuthType() == DEVELOPMENT_BECOME_ANY_ACCOUNT;
  }

  @Override
  public Response<EmailInfo> apply(AccountResource rsrc, EmailInput input)
      throws RestApiException, OrmException, EmailException, MethodNotAllowedException, IOException,
          ConfigInvalidException, PermissionBackendException {
    if (input == null) {
      input = new EmailInput();
    }

    if (self.get() != rsrc.getUser() || input.noConfirmation) {
      permissionBackend.currentUser().check(GlobalPermission.MODIFY_ACCOUNT);
    }

    if (!realm.allowsEdit(AccountFieldName.REGISTER_NEW_EMAIL)) {
      throw new MethodNotAllowedException("realm does not allow adding emails");
    }

    return apply(rsrc.getUser(), input);
  }

  /** To be used from plugins that want to create emails without permission checks. */
  public Response<EmailInfo> apply(IdentifiedUser user, EmailInput input)
      throws RestApiException, OrmException, EmailException, MethodNotAllowedException, IOException,
          ConfigInvalidException, PermissionBackendException {
    if (input == null) {
      input = new EmailInput();
    }

    if (input.email != null && !email.equals(input.email)) {
      throw new BadRequestException("email address must match URL");
    }

    if (!validator.isValid(email)) {
      throw new BadRequestException("invalid email address");
    }

    EmailInfo info = new EmailInfo();
    info.email = email;
    if (input.noConfirmation || isDevMode) {
      if (isDevMode) {
        log.warn("skipping email validation in developer mode");
      }
      try {
        accountManager.link(user.getAccountId(), AuthRequest.forEmail(email));
      } catch (AccountException e) {
        throw new ResourceConflictException(e.getMessage());
      }
      if (input.preferred) {
        putPreferred.apply(new AccountResource.Email(user, email), null);
        info.preferred = true;
      }
    } else {
      try {
        RegisterNewEmailSender sender = registerNewEmailFactory.create(email);
        if (!sender.isAllowed()) {
          throw new MethodNotAllowedException("Not allowed to add email address " + email);
        }
        sender.send();
        info.pendingConfirmation = true;
      } catch (EmailException | RuntimeException e) {
        log.error("Cannot send email verification message to " + email, e);
        throw e;
      }
    }
    return Response.created(info);
  }
}
