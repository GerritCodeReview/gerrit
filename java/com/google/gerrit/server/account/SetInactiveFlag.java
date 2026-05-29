// Copyright (C) 2017 The Android Open Source Project
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

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.events.AccountActivationListener;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.validators.AccountActivationValidationListener;
import com.google.gerrit.server.validators.ValidationException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.jgit.errors.ConfigInvalidException;

/** Toggler for account active state. */
@Singleton
public class SetInactiveFlag {
  private final PluginSetContext<AccountActivationValidationListener>
      accountActivationValidationListeners;
  private final Provider<AccountsUpdate> accountsUpdateProvider;
  private final PluginSetContext<AccountActivationListener> accountActivationListeners;

  @Inject
  SetInactiveFlag(
      PluginSetContext<AccountActivationValidationListener> accountActivationValidationListeners,
      @ServerInitiated Provider<AccountsUpdate> accountsUpdateProvider,
      PluginSetContext<AccountActivationListener> accountActivationListeners) {
    this.accountActivationValidationListeners = accountActivationValidationListeners;
    this.accountsUpdateProvider = accountsUpdateProvider;
    this.accountActivationListeners = accountActivationListeners;
  }

  @CanIgnoreReturnValue
  public Response<?> deactivate(Account.Id accountId)
      throws RestApiException, IOException, ConfigInvalidException {
    AtomicBoolean alreadyInactive = new AtomicBoolean(false);
    AtomicReference<Optional<RestApiException>> exception = new AtomicReference<>(Optional.empty());
    Optional<AccountState> updatedAccount =
        accountsUpdateProvider
            .get()
            .update(
                "Deactivate Account via API",
                accountId,
                (a, u) -> {
                  if (!a.account().isActive()) {
                    alreadyInactive.set(true);
                  } else {
                    try {
                      accountActivationValidationListeners.runEach(
                          l -> l.validateDeactivation(a), ValidationException.class);
                    } catch (ValidationException e) {
                      exception.set(Optional.of(new ResourceConflictException(e.getMessage(), e)));
                      return;
                    }
                    u.setActive(false);
                  }
                });
    if (!updatedAccount.isPresent()) {
      throw new ResourceNotFoundException("account not found");
    }

    if (exception.get().isPresent()) {
      throw exception.get().get();
    }
    if (alreadyInactive.get()) {
      throw new ResourceConflictException("account not active");
    }

    // At this point the account got set inactive and no errors occurred

    int id = accountId.get();
    accountActivationListeners.runEach(l -> l.onAccountDeactivated(id));

    return Response.none();
  }

  @CanIgnoreReturnValue
  public Response<String> activate(Account.Id accountId)
      throws RestApiException, IOException, ConfigInvalidException {
    AtomicBoolean alreadyActive = new AtomicBoolean(false);
    AtomicReference<Optional<RestApiException>> exception = new AtomicReference<>(Optional.empty());
    Optional<AccountState> updatedAccount =
        accountsUpdateProvider
            .get()
            .update(
                "Activate Account via API",
                accountId,
                (a, u) -> {
                  if (a.account().isActive()) {
                    alreadyActive.set(true);
                  } else {
                    try {
                      accountActivationValidationListeners.runEach(
                          l -> l.validateActivation(a), ValidationException.class);
                    } catch (ValidationException e) {
                      exception.set(Optional.of(new ResourceConflictException(e.getMessage(), e)));
                      return;
                    }
                    u.setActive(true);
                  }
                });
    if (!updatedAccount.isPresent()) {
      throw new ResourceNotFoundException("account not found");
    }

    if (exception.get().isPresent()) {
      throw exception.get().get();
    }

    Response<String> res;
    if (alreadyActive.get()) {
      res = Response.ok();
    } else {
      res = Response.created();

      int id = accountId.get();
      accountActivationListeners.runEach(l -> l.onAccountActivated(id));
    }
    return res;
  }
}
