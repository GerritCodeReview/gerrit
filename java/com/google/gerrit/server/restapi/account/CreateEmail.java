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
import static com.google.gerrit.server.mail.EmailFactories.NEW_EMAIL_REGISTERED;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.UsedAt;
import com.google.gerrit.exceptions.EmailException;
import com.google.gerrit.extensions.api.accounts.EmailInput;
import com.google.gerrit.extensions.client.AccountFieldName;
import com.google.gerrit.extensions.common.EmailInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestCollectionCreateView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.account.Realm;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.mail.EmailFactories;
import com.google.gerrit.server.mail.send.MessageIdGenerator;
import com.google.gerrit.server.mail.send.OutgoingEmail;
import com.google.gerrit.server.mail.send.OutgoingEmailValidator;
import com.google.gerrit.server.mail.send.RegisterNewEmailDecorator;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;

/**
 * REST endpoint for registering a new email address for an account.
 *
 * <p>This REST endpoint handles {@code PUT
 * /accounts/<account-identifier>/emails/<email-identifier>} requests if the specified email doesn't
 * exist for the account yet. If it already exists, the request is handled by {@link PutEmail}.
 *
 * <p>Whether an email address can be registered for the account depends on whether the used {@link
 * Realm} supports this.
 *
 * <p>When a new email address is registered an email with a confirmation link is sent to that
 * address. Only when the receiver confirms the email by clicking on the confirmation link, the
 * email address is added to the account (see {@link
 * com.google.gerrit.server.restapi.config.ConfirmEmail}). Confirming an email address for an
 * account creates an external ID that links the email address to the account. An email address can
 * only be added to an account if it is not assigned to any other account yet.
 *
 * <p>In some cases it is allowed to skip the email confirmation and add the email directly (calling
 * user has 'Modify Account' capability or server is running in dev mode).
 */
@Singleton
public class CreateEmail
    implements RestCollectionCreateView<AccountResource, AccountResource.Email, EmailInput> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Provider<CurrentUser> self;
  private final Realm realm;
  private final PermissionBackend permissionBackend;
  private final AccountManager accountManager;
  private final EmailFactories emailFactories;
  private final PutPreferred putPreferred;
  private final OutgoingEmailValidator validator;
  private final MessageIdGenerator messageIdGenerator;
  private final boolean isDevMode;
  private final AuthRequest.Factory authRequestFactory;

  @Inject
  CreateEmail(
      Provider<CurrentUser> self,
      Realm realm,
      PermissionBackend permissionBackend,
      AuthConfig authConfig,
      AccountManager accountManager,
      EmailFactories emailFactories,
      PutPreferred putPreferred,
      OutgoingEmailValidator validator,
      MessageIdGenerator messageIdGenerator,
      AuthRequest.Factory authRequestFactory) {
    this.self = self;
    this.realm = realm;
    this.permissionBackend = permissionBackend;
    this.accountManager = accountManager;
    this.emailFactories = emailFactories;
    this.putPreferred = putPreferred;
    this.validator = validator;
    this.isDevMode = authConfig.getAuthType() == DEVELOPMENT_BECOME_ANY_ACCOUNT;
    this.messageIdGenerator = messageIdGenerator;
    this.authRequestFactory = authRequestFactory;
  }

  @Override
  public Response<EmailInfo> apply(AccountResource rsrc, IdString id, EmailInput input)
      throws RestApiException,
          EmailException,
          MethodNotAllowedException,
          IOException,
          ConfigInvalidException,
          PermissionBackendException {
    if (input == null) {
      input = new EmailInput();
    }

    if (!self.get().hasSameAccountId(rsrc.getUser()) || input.noConfirmation) {
      permissionBackend.currentUser().check(GlobalPermission.MODIFY_ACCOUNT);
    }

    if (!realm.allowsEdit(AccountFieldName.REGISTER_NEW_EMAIL)) {
      throw new MethodNotAllowedException("realm does not allow adding emails");
    }

    return Response.created(apply(rsrc.getUser(), id, input));
  }

  /** To be used from plugins that want to create emails without permission checks. */
  @UsedAt(UsedAt.Project.PLUGIN_SERVICEUSER)
  public EmailInfo apply(IdentifiedUser user, IdString id, EmailInput input)
      throws RestApiException,
          EmailException,
          MethodNotAllowedException,
          IOException,
          ConfigInvalidException,
          PermissionBackendException {
    String email = id.get().trim();

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
        logger.atWarning().log("skipping email validation in developer mode");
      }
      try {
        accountManager.link(user.getAccountId(), authRequestFactory.createForEmail(email));
      } catch (AccountException e) {
        throw new ResourceConflictException(e.getMessage());
      }
      if (input.preferred) {
        putPreferred.apply(new AccountResource.Email(user, email), null);
        info.preferred = true;
      }
    } else {
      try {
        RegisterNewEmailDecorator emailDecorator = emailFactories.createRegisterNewEmail(email);
        if (!emailDecorator.isAllowed()) {
          throw new MethodNotAllowedException("Not allowed to add email address " + email);
        }
        OutgoingEmail outgoingEmail =
            emailFactories.createOutgoingEmail(NEW_EMAIL_REGISTERED, emailDecorator);
        outgoingEmail.setMessageId(messageIdGenerator.fromAccountUpdate(user.getAccountId()));
        outgoingEmail.send();
        info.pendingConfirmation = true;
      } catch (EmailException | RuntimeException e) {
        logger.atSevere().withCause(e).log("Cannot send email verification message to %s", email);
        throw e;
      }
    }
    return info;
  }
}
