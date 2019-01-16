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

import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Account;
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

@Singleton
public class SetInactiveFlag {
  private final PluginSetContext<AccountActivationValidationListener>
      accountActivationValidationListeners;
  private final Provider<AccountsUpdate> accountsUpdateProvider;

  @Inject
  SetInactiveFlag(
      PluginSetContext<AccountActivationValidationListener> accountActivationValidationListeners,
      @ServerInitiated Provider<AccountsUpdate> accountsUpdateProvider) {
    this.accountActivationValidationListeners = accountActivationValidationListeners;
    this.accountsUpdateProvider = accountsUpdateProvider;
  }

  public Response<?> deactivate(Account.Id accountId)
      throws RestApiException, IOException, ConfigInvalidException, StorageException {
    AtomicBoolean alreadyInactive = new AtomicBoolean(false);
    AtomicReference<Optional<RestApiException>> exception = new AtomicReference<>(Optional.empty());
    accountsUpdateProvider
        .get()
        .update(
            "Deactivate Account via API",
            accountId,
            (a, u) -> {
              if (!a.getAccount().isActive()) {
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
            })
        .orElseThrow(() -> new ResourceNotFoundException("account not found"));
    if (exception.get().isPresent()) {
      throw exception.get().get();
    }
    if (alreadyInactive.get()) {
      throw new ResourceConflictException("account not active");
    }
    return Response.none();
  }

  public Response<String> activate(Account.Id accountId)
      throws RestApiException, IOException, ConfigInvalidException, StorageException {
    AtomicBoolean alreadyActive = new AtomicBoolean(false);
    AtomicReference<Optional<RestApiException>> exception = new AtomicReference<>(Optional.empty());
    accountsUpdateProvider
        .get()
        .update(
            "Activate Account via API",
            accountId,
            (a, u) -> {
              if (a.getAccount().isActive()) {
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
            })
        .orElseThrow(() -> new ResourceNotFoundException("account not found"));
    if (exception.get().isPresent()) {
      throw exception.get().get();
    }
    return alreadyActive.get() ? Response.ok("") : Response.created("");
  }
}
