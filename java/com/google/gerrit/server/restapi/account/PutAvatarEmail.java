// Copyright (C) 2026 The Android Open Source Project
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

import static java.util.stream.Collectors.toSet;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.common.Input;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.jgit.errors.ConfigInvalidException;

/**
 * REST endpoint to set an email address as avatar email address for an account.
 *
 * <p>This REST endpoint handles {@code PUT
 * /accounts/<account-identifier>/emails/<email-identifier>/avatar} requests.
 *
 * <p>The avatar email is used for avatar lookup (e.g. Gravatar). Users can only set an email
 * address as avatar email that is assigned to their account.
 */
@Singleton
public class PutAvatarEmail implements RestModifyView<AccountResource.Email, Input> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Provider<CurrentUser> self;
  private final PermissionBackend permissionBackend;
  private final Provider<AccountsUpdate> accountsUpdateProvider;

  @Inject
  PutAvatarEmail(
      Provider<CurrentUser> self,
      PermissionBackend permissionBackend,
      @ServerInitiated Provider<AccountsUpdate> accountsUpdateProvider) {
    this.self = self;
    this.permissionBackend = permissionBackend;
    this.accountsUpdateProvider = accountsUpdateProvider;
  }

  @Override
  public Response<String> apply(AccountResource.Email rsrc, Input input)
      throws RestApiException, IOException, PermissionBackendException, ConfigInvalidException {
    if (!self.get().hasSameAccountId(rsrc.getUser())) {
      permissionBackend.currentUser().check(GlobalPermission.MODIFY_ACCOUNT);
    }
    return apply(rsrc.getUser(), rsrc.getEmail());
  }

  public Response<String> apply(IdentifiedUser user, String avatarEmail)
      throws RestApiException, IOException, ConfigInvalidException {
    AtomicReference<Optional<RestApiException>> exception = new AtomicReference<>(Optional.empty());
    AtomicBoolean alreadySet = new AtomicBoolean(false);
    Optional<AccountState> updatedAccount =
        accountsUpdateProvider
            .get()
            .update(
                "Set Avatar Email via API",
                user.getAccountId(),
                (r, a, u) -> {
                  if (avatarEmail.equals(a.account().avatarEmail())) {
                    alreadySet.set(true);
                  } else {
                    // Check if the user has a matching email
                    String matchingEmail = null;
                    for (String email :
                        a.externalIds().stream()
                            .map(ExternalId::email)
                            .filter(Objects::nonNull)
                            .collect(toSet())) {
                      if (email.equals(avatarEmail)) {
                        // We have an email that matches exactly
                        matchingEmail = email;
                        break;
                      } else if (matchingEmail == null && email.equalsIgnoreCase(avatarEmail)) {
                        // We found an email that matches but has a different case
                        matchingEmail = email;
                      }
                    }

                    if (matchingEmail == null) {
                      // User doesn't have this email address
                      logger.atWarning().log(
                          "Cannot set avatar email %s for account %s because it is not assigned to"
                              + " this account",
                          avatarEmail, user.getAccountId());
                      exception.set(Optional.of(new ResourceNotFoundException(avatarEmail)));
                      return;
                    }
                    u.setAvatarEmail(matchingEmail);
                  }
                });
    if (!updatedAccount.isPresent()) {
      throw new ResourceNotFoundException("account not found");
    }
    if (exception.get().isPresent()) {
      throw exception.get().get();
    }
    return alreadySet.get() ? Response.ok() : Response.created();
  }
}
