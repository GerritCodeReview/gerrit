// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.acceptance.api.accounts;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.acceptance.testsuite.account.AccountOperations;
import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.validators.AccountActivationValidationListener;
import com.google.gerrit.server.validators.ValidationException;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

/**
 * Tests the wiring of a real plugin's account listeners
 *
 * <p>This test really puts focus on the wiring of the account listeners. Tests for the inner
 * workings of account activation/deactivation can be found in {@link AccountIT}.
 */
@TestPlugin(
    name = "account-listener-IT-plugin",
    sysModule = "com.google.gerrit.acceptance.api.accounts.AccountListenersIT$Module")
public class AccountListenersIT extends LightweightPluginDaemonTest {
  @Inject private AccountOperations accountOperations;

  static AtomicInteger activationValidations = new AtomicInteger(0);
  static AtomicInteger deactivationValidations = new AtomicInteger(0);

  public static class Module extends AbstractModule {
    @Override
    protected void configure() {
      DynamicSet.bind(binder(), AccountActivationValidationListener.class).to(Validator.class);
    }
  }

  @Test
  public void testBindings() throws RestApiException {
    Account.Id accountId = accountOperations.newAccount().active().create();

    assertThat(deactivationValidations.get()).isEqualTo(0);
    gApi.accounts().id(accountId.get()).setActive(false);
    assertThat(deactivationValidations.get()).isEqualTo(1);

    assertThat(activationValidations.get()).isEqualTo(0);
    gApi.accounts().id(accountId.get()).setActive(true);
    assertThat(activationValidations.get()).isEqualTo(1);
  }

  public static class Validator implements AccountActivationValidationListener {
    @Override
    public void validateActivation(AccountState account) throws ValidationException {
      activationValidations.incrementAndGet();
    }

    @Override
    public void validateDeactivation(AccountState account) throws ValidationException {
      deactivationValidations.incrementAndGet();
    }
  }
}
