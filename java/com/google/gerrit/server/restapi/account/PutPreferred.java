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

import static java.util.stream.Collectors.toList;

import com.google.gerrit.extensions.common.Input;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class PutPreferred implements RestModifyView<AccountResource.Email, Input> {

  private final Provider<CurrentUser> self;
  private final PermissionBackend permissionBackend;
  private final Provider<AccountsUpdate> accountsUpdateProvider;
  private final ExternalIds externalIds;

  @Inject
  PutPreferred(
      Provider<CurrentUser> self,
      PermissionBackend permissionBackend,
      @ServerInitiated Provider<AccountsUpdate> accountsUpdateProvider,
      ExternalIds externalIds) {
    this.self = self;
    this.permissionBackend = permissionBackend;
    this.accountsUpdateProvider = accountsUpdateProvider;
    this.externalIds = externalIds;
  }

  @Override
  public Response<String> apply(AccountResource.Email rsrc, Input input)
      throws RestApiException, OrmException, IOException, PermissionBackendException,
          ConfigInvalidException {
    if (self.get() != rsrc.getUser()) {
      permissionBackend.currentUser().check(GlobalPermission.MODIFY_ACCOUNT);
    }
    return apply(rsrc.getUser(), rsrc.getEmail());
  }

  public Response<String> apply(IdentifiedUser user, String preferredEmail)
      throws RestApiException, IOException, ConfigInvalidException, OrmException {
    AtomicReference<Optional<RestApiException>> exception = new AtomicReference<>(Optional.empty());
    AtomicBoolean alreadyPreferred = new AtomicBoolean(false);
    accountsUpdateProvider
        .get()
        .update(
            "Set Preferred Email via API",
            user.getAccountId(),
            (a, u) -> {
              if (preferredEmail.equals(a.getAccount().getPreferredEmail())) {
                alreadyPreferred.set(true);
              } else {
                // check if the user has a matching email
                String matchingEmail = null;
                for (String email : ExternalId.getEmails(a.getExternalIds()).collect(toList())) {
                  if (email.equals(preferredEmail)) {
                    // we have an email that matches exactly, prefer this one
                    matchingEmail = email;
                    break;
                  } else if (matchingEmail == null && email.equalsIgnoreCase(preferredEmail)) {
                    // we found an email that matches but has a different case
                    matchingEmail = email;
                  }
                }

                if (matchingEmail == null) {
                  // user doesn't have an external ID for this email
                  if (user.hasEmailAddress(preferredEmail)) {
                    // but Realm says the user is allowed to use this email
                    if (!externalIds.byEmail(preferredEmail).isEmpty()) {
                      // but the email is already assigned to another account
                      exception.set(
                          Optional.of(
                              new ResourceConflictException("email in use by another account")));
                    }

                    // claim the email now
                    u.addExternalId(ExternalId.createEmail(a.getAccount().getId(), preferredEmail));
                    matchingEmail = preferredEmail;
                  } else {
                    // Realm says that the email doesn't belong to the user. This can only happen as
                    // a race condition because EmailsCollection would have thrown
                    // ResourceNotFoundException already before invoking this REST endpoint.
                    exception.set(Optional.of(new ResourceNotFoundException(preferredEmail)));
                  }
                }
                u.setPreferredEmail(matchingEmail);
              }
            })
        .orElseThrow(() -> new ResourceNotFoundException("account not found"));
    if (exception.get().isPresent()) {
      throw exception.get().get();
    }
    return alreadyPreferred.get() ? Response.ok("") : Response.created("");
  }
}
